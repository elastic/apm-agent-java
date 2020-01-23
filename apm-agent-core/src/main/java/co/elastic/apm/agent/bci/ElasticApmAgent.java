/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * #L%
 */
package co.elastic.apm.agent.bci;

import co.elastic.apm.agent.bci.bytebuddy.AnnotationValueOffsetMappingFactory;
import co.elastic.apm.agent.bci.bytebuddy.ErrorLoggingListener;
import co.elastic.apm.agent.bci.bytebuddy.FailSafeDeclaredMethodsCompiler;
import co.elastic.apm.agent.bci.bytebuddy.MatcherTimer;
import co.elastic.apm.agent.bci.bytebuddy.MinimumClassFileVersionValidator;
import co.elastic.apm.agent.bci.bytebuddy.RootPackageCustomLocator;
import co.elastic.apm.agent.bci.bytebuddy.SimpleMethodSignatureOffsetMappingFactory;
import co.elastic.apm.agent.bci.bytebuddy.SoftlyReferencingTypePoolCache;
import co.elastic.apm.agent.bci.methodmatching.MethodMatcher;
import co.elastic.apm.agent.bci.methodmatching.TraceMethodInstrumentation;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.util.DependencyInjectingServiceLoader;
import co.elastic.apm.agent.util.ThreadUtils;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.scaffold.TypeValidation;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.JavaModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static co.elastic.apm.agent.bci.bytebuddy.ClassLoaderNameMatcher.classLoaderWithName;
import static co.elastic.apm.agent.bci.bytebuddy.ClassLoaderNameMatcher.isReflectionClassLoader;
import static net.bytebuddy.asm.Advice.ExceptionHandler.Default.PRINTING;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

public class ElasticApmAgent {

    // Don't init logger as a static field, logging needs to be initialized first see also issue #593
    // private static final Logger doNotUseThisLogger = LoggerFactory.getLogger(ElasticApmAgent.class);

    private static final ConcurrentMap<String, MatcherTimer> matcherTimers = new ConcurrentHashMap<>();
    @Nullable
    private static Instrumentation instrumentation;
    @Nullable
    private static ResettableClassFileTransformer resettableClassFileTransformer;
    @Nullable
    private static File agentJarFile;

    /**
     * Called reflectively by {@link AgentMain} to initialize the agent
     *
     * @param instrumentation the instrumentation instance
     * @param agentJarFile    a reference to the agent jar on the file system
     */
    public static void initialize(String agentArguments, Instrumentation instrumentation, File agentJarFile) {
        ElasticApmAgent.agentJarFile = agentJarFile;
        initInstrumentation(new ElasticApmTracerBuilder(agentArguments).build(), instrumentation);
    }

    public static void initInstrumentation(ElasticApmTracer tracer, Instrumentation instrumentation) {
        initInstrumentation(tracer, instrumentation, loadInstrumentations(tracer));
    }

    @Nonnull
    private static Iterable<ElasticApmInstrumentation> loadInstrumentations(ElasticApmTracer tracer) {
        final List<ElasticApmInstrumentation> instrumentations = DependencyInjectingServiceLoader.load(ElasticApmInstrumentation.class, tracer);
        for (MethodMatcher traceMethod : tracer.getConfig(CoreConfiguration.class).getTraceMethods()) {
            instrumentations.add(new TraceMethodInstrumentation(tracer, traceMethod));
        }

        return instrumentations;
    }

    public static void initInstrumentation(final ElasticApmTracer tracer, Instrumentation instrumentation,
                                           Iterable<ElasticApmInstrumentation> instrumentations) {
        Runtime.getRuntime().addShutdownHook(new Thread(ThreadUtils.addElasticApmThreadPrefix("init-instrumentation-shutdown-hook")) {
            @Override
            public void run() {
                tracer.stop();
                matcherTimers.clear();
            }
        });
        matcherTimers.clear();
        final Logger logger = LoggerFactory.getLogger(ElasticApmAgent.class);
        if (ElasticApmAgent.instrumentation != null) {
            logger.warn("Instrumentation has already been initialized");
            return;
        }
        ElasticApmInstrumentation.staticInit(tracer);
        final CoreConfiguration coreConfiguration = tracer.getConfig(CoreConfiguration.class);
        ElasticApmAgent.instrumentation = instrumentation;
        final ByteBuddy byteBuddy = new ByteBuddy()
            .with(TypeValidation.of(logger.isDebugEnabled()))
            .with(FailSafeDeclaredMethodsCompiler.INSTANCE);
        AgentBuilder agentBuilder = getAgentBuilder(byteBuddy, coreConfiguration, logger);
        int numberOfAdvices = 0;
        for (final ElasticApmInstrumentation advice : instrumentations) {
            if (isIncluded(advice, coreConfiguration)) {
                numberOfAdvices++;
                agentBuilder = applyAdvice(tracer, agentBuilder, advice);
            }
        }
        logger.debug("Applied {} advices", numberOfAdvices);

        resettableClassFileTransformer = agentBuilder.installOn(ElasticApmAgent.instrumentation);
    }

    private static boolean isIncluded(ElasticApmInstrumentation advice, CoreConfiguration coreConfiguration) {
        final Collection<String> disabledInstrumentations = coreConfiguration.getDisabledInstrumentations();
        return !isGroupDisabled(disabledInstrumentations, advice.getInstrumentationGroupNames()) && isInstrumentationEnabled(advice, coreConfiguration);
    }

    private static boolean isGroupDisabled(Collection<String> disabledInstrumentations, Collection<String> instrumentationGroupNames) {
        for (String instrumentationGroupName : instrumentationGroupNames) {
            if (disabledInstrumentations.contains(instrumentationGroupName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInstrumentationEnabled(ElasticApmInstrumentation advice, CoreConfiguration coreConfiguration) {
        return advice.includeWhenInstrumentationIsDisabled() || coreConfiguration.isInstrument();
    }

    private static AgentBuilder applyAdvice(final ElasticApmTracer tracer, final AgentBuilder agentBuilder,
                                            final ElasticApmInstrumentation instrumentation) {
        final Logger logger = LoggerFactory.getLogger(ElasticApmAgent.class);
        logger.debug("Applying instrumentation {}", instrumentation.getClass().getName());
        final boolean classLoadingMatchingPreFilter = tracer.getConfig(CoreConfiguration.class).isClassLoadingMatchingPreFilter();
        final boolean typeMatchingWithNamePreFilter = tracer.getConfig(CoreConfiguration.class).isTypeMatchingWithNamePreFilter();
        final ElementMatcher.Junction<ClassLoader> classLoaderMatcher = instrumentation.getClassLoaderMatcher();
        final ElementMatcher<? super NamedElement> typeMatcherPreFilter = instrumentation.getTypeMatcherPreFilter();
        final ElementMatcher.Junction<ProtectionDomain> versionPostFilter = instrumentation.getImplementationVersionPostFilter();
        final ElementMatcher<? super TypeDescription> typeMatcher = new ElementMatcher.Junction.Conjunction<>(instrumentation.getTypeMatcher(), not(isInterface()));
        final ElementMatcher<? super MethodDescription> methodMatcher = instrumentation.getMethodMatcher();
        return agentBuilder
            .type(new AgentBuilder.RawMatcher() {
                @Override
                public boolean matches(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, Class<?> classBeingRedefined, ProtectionDomain protectionDomain) {
                    long start = System.nanoTime();
                    try {
                        if (classLoadingMatchingPreFilter && !classLoaderMatcher.matches(classLoader)) {
                            return false;
                        }
                        if (typeMatchingWithNamePreFilter && !typeMatcherPreFilter.matches(typeDescription)) {
                            return false;
                        }
                        boolean typeMatches;
                        try {
                            typeMatches = typeMatcher.matches(typeDescription) && versionPostFilter.matches(protectionDomain);
                        } catch (Exception ignored) {
                            // could be because of a missing type
                            typeMatches = false;
                        }
                        if (typeMatches) {
                            logger.debug("Type match for instrumentation {}: {} matches {}",
                                instrumentation.getClass().getSimpleName(), typeMatcher, typeDescription);
                            try {
                                instrumentation.onTypeMatch(typeDescription, classLoader, protectionDomain, classBeingRedefined);
                            } catch (Exception e) {
                                logger.error(e.getMessage(), e);
                            }
                            if (logger.isTraceEnabled()) {
                                logClassLoaderHierarchy(classLoader, logger, instrumentation);
                            }
                        }
                        return typeMatches;
                    } finally {
                        getOrCreateTimer(instrumentation.getClass()).addTypeMatchingDuration(System.nanoTime() - start);
                    }
                }
            })
            .transform(getTransformer(tracer, instrumentation, logger, methodMatcher))
            .transform(new AgentBuilder.Transformer() {
                @Override
                public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription,
                                                        ClassLoader classLoader, JavaModule module) {
                    return builder.visit(MinimumClassFileVersionValidator.INSTANCE);
                }
            });
    }

    private static AgentBuilder.Transformer.ForAdvice getTransformer(final ElasticApmTracer tracer, final ElasticApmInstrumentation instrumentation, final Logger logger, final ElementMatcher<? super MethodDescription> methodMatcher) {
        Advice.WithCustomMapping withCustomMapping = Advice
            .withCustomMapping()
            .bind(new SimpleMethodSignatureOffsetMappingFactory())
            .bind(new AnnotationValueOffsetMappingFactory());
        Advice.OffsetMapping.Factory<?> offsetMapping = instrumentation.getOffsetMapping();
        if (offsetMapping != null) {
            withCustomMapping = withCustomMapping.bind(offsetMapping);
        }
        return new AgentBuilder.Transformer.ForAdvice(withCustomMapping)
            .advice(new ElementMatcher<MethodDescription>() {
                @Override
                public boolean matches(MethodDescription target) {
                    long start = System.nanoTime();
                    try {
                        boolean matches;
                        try {
                            matches = methodMatcher.matches(target);
                        } catch (Exception ignored) {
                            // could be because of a missing type
                            matches = false;
                        }
                        if (matches) {
                            logger.debug("Method match for instrumentation {}: {} matches {}",
                                instrumentation.getClass().getSimpleName(), methodMatcher, target);
                        }
                        return matches;
                    } finally {
                        getOrCreateTimer(instrumentation.getClass()).addMethodMatchingDuration(System.nanoTime() - start);
                    }
                }
            }, instrumentation.getAdviceClass().getName())
            .include(ClassLoader.getSystemClassLoader())
            .withExceptionHandler(PRINTING);
    }

    private static MatcherTimer getOrCreateTimer(Class<? extends ElasticApmInstrumentation> adviceClass) {
        final String name = adviceClass.getName();
        MatcherTimer timer = matcherTimers.get(name);
        if (timer == null) {
            matcherTimers.putIfAbsent(name, new MatcherTimer(name));
            return matcherTimers.get(name);
        } else {
            return timer;
        }
    }

    static long getTotalMatcherTime() {
        long totalTime = 0;
        for (MatcherTimer value : matcherTimers.values()) {
            totalTime += value.getTotalTime();
        }
        return totalTime;
    }

    static Collection<MatcherTimer> getMatcherTimers() {
        return matcherTimers.values();
    }

    // may help to debug classloading problems
    private static void logClassLoaderHierarchy(@Nullable ClassLoader classLoader, Logger logger, ElasticApmInstrumentation advice) {
        logger.trace("Advice {} is loaded by {}", advice.getClass().getName(), advice.getClass().getClassLoader());
        if (classLoader != null) {
            boolean canLoadAgent = false;
            try {
                classLoader.loadClass(advice.getClass().getName());
                canLoadAgent = true;
            } catch (ClassNotFoundException ignore) {
            }
            logger.trace("{} can load advice ({}): {}", classLoader, advice.getClass().getName(), canLoadAgent);
            logClassLoaderHierarchy(classLoader.getParent(), logger, advice);
        } else {
            logger.trace("bootstrap classloader");
        }
    }

    /**
     * Reverts instrumentation of classes and re-transforms them to their state without the agent.
     * <p>
     * This is only to be used for unit tests
     * </p>
     */
    public static synchronized void reset() {
        if (resettableClassFileTransformer == null || instrumentation == null) {
            throw new IllegalStateException("Reset was called before init");
        }
        resettableClassFileTransformer.reset(instrumentation, AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);
        instrumentation = null;
        resettableClassFileTransformer = null;
    }

    private static AgentBuilder getAgentBuilder(final ByteBuddy byteBuddy, final CoreConfiguration coreConfiguration, Logger logger) {
        final List<WildcardMatcher> classesExcludedFromInstrumentation = coreConfiguration.getClassesExcludedFromInstrumentation();

        AgentBuilder.LocationStrategy locationStrategy = AgentBuilder.LocationStrategy.ForClassLoader.WEAK;
        if (agentJarFile != null) {
            try {
                locationStrategy =
                    ((AgentBuilder.LocationStrategy.ForClassLoader) locationStrategy).withFallbackTo(
                        ClassFileLocator.ForJarFile.of(agentJarFile),
                        new RootPackageCustomLocator("java.", ClassFileLocator.ForClassLoader.ofBootLoader())
                    );
            } catch (IOException e) {
                logger.warn("Failed to add ClassFileLocator for the agent jar. Some instrumentations may not work", e);
            }
        }

        return new AgentBuilder.Default(byteBuddy)
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(AgentBuilder.DescriptionStrategy.Default.POOL_ONLY)
            .with(locationStrategy)
            .with(new ErrorLoggingListener())
            // ReaderMode.FAST as we don't need to read method parameter names
            .with(coreConfiguration.isTypePoolCacheEnabled()
                ? new SoftlyReferencingTypePoolCache(TypePool.Default.ReaderMode.FAST, 1, isReflectionClassLoader())
                : AgentBuilder.PoolStrategy.Default.FAST)
            .ignore(any(), isReflectionClassLoader())
            .or(any(), classLoaderWithName("org.codehaus.groovy.runtime.callsite.CallSiteClassLoader"))
            // ideally, those bootstrap classpath inclusions should be set at plugin level, see issue #952
            .or(nameStartsWith("java.")
                .and(
                    not(
                        nameEndsWith("URLConnection")
                            .or(nameStartsWith("java.util.concurrent."))
                            .or(named("java.lang.ProcessBuilder"))
                            .or(named("java.lang.ProcessImpl"))
                            .or(named("java.lang.Process"))
                            .or(named("java.lang.UNIXProcess"))
                    )
                )
            )
            .or(nameStartsWith("com.sun.")
                .and(
                    not(
                        nameStartsWith("com.sun.faces.")
                            .or(nameEndsWith("URLConnection"))
                    )
                )
            )
            .or(nameStartsWith("sun")
                .and(
                    not(nameEndsWith("URLConnection"))
                )
            )
            .or(nameStartsWith("co.elastic.apm.agent.shaded"))
            .or(nameStartsWith("org.aspectj."))
            .or(nameStartsWith("org.groovy."))
            .or(nameStartsWith("com.p6spy."))
            .or(nameStartsWith("net.bytebuddy."))
            .or(nameStartsWith("org.stagemonitor."))
            .or(nameContains("javassist"))
            .or(nameContains(".asm."))
            .or(new ElementMatcher.Junction.AbstractBase<TypeDescription>() {
                @Override
                public boolean matches(TypeDescription target) {
                    return WildcardMatcher.anyMatch(classesExcludedFromInstrumentation, target.getName()) != null;
                }
            })
            .disableClassFormatChanges();
    }

    /**
     * Returns the directory the agent jar resides in.
     * <p>
     * In scenarios where the agent jar can't be resolved,
     * like in unit tests,
     * this method returns {@code null}.
     * </p>
     *
     * @return the directory the agent jar resides in
     */
    @Nullable
    public static String getAgentHome() {
        return agentJarFile == null ? null : agentJarFile.getParent();
    }
}
