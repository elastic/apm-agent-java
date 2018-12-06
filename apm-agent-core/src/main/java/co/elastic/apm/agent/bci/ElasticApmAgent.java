/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.bci;

import co.elastic.apm.bci.bytebuddy.AnnotationValueOffsetMappingFactory;
import co.elastic.apm.bci.bytebuddy.ErrorLoggingListener;
import co.elastic.apm.bci.bytebuddy.MatcherTimer;
import co.elastic.apm.bci.bytebuddy.MinimumClassFileVersionValidator;
import co.elastic.apm.bci.bytebuddy.SimpleMethodSignatureOffsetMappingFactory;
import co.elastic.apm.bci.bytebuddy.SoftlyReferencingTypePoolCache;
import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.ElasticApmTracerBuilder;
import co.elastic.apm.matcher.WildcardMatcher;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.scaffold.MethodGraph;
import net.bytebuddy.dynamic.scaffold.TypeValidation;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.JavaModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static co.elastic.apm.bci.bytebuddy.ClassLoaderNameMatcher.classLoaderWithName;
import static co.elastic.apm.bci.bytebuddy.ClassLoaderNameMatcher.isReflectionClassLoader;
import static net.bytebuddy.asm.Advice.ExceptionHandler.Default.PRINTING;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;

public class ElasticApmAgent {

    private static final ConcurrentMap<String, MatcherTimer> matcherTimers = new ConcurrentHashMap<>();
    // Don't init logger as a static field, logging needs to be initialized first
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
    public static void initialize(Instrumentation instrumentation, File agentJarFile) {
        ElasticApmAgent.agentJarFile = agentJarFile;
        initInstrumentation(new ElasticApmTracerBuilder().build(), instrumentation);
    }

    public static void initInstrumentation(ElasticApmTracer tracer, Instrumentation instrumentation) {
        initInstrumentation(tracer, instrumentation, ServiceLoader.load(ElasticApmInstrumentation.class, ElasticApmInstrumentation.class.getClassLoader()));
    }

    public static void initInstrumentation(final ElasticApmTracer tracer, Instrumentation instrumentation,
                                           Iterable<ElasticApmInstrumentation> instrumentations) {
        Runtime.getRuntime().addShutdownHook(new Thread() {
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
            .with(MethodGraph.Compiler.ForDeclaredMethods.INSTANCE);
        AgentBuilder agentBuilder = getAgentBuilder(byteBuddy, coreConfiguration);
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
                                            final ElasticApmInstrumentation advice) {
        final Logger logger = LoggerFactory.getLogger(ElasticApmAgent.class);
        logger.debug("Applying advice {}", advice.getClass().getName());
        advice.init(tracer);
        final boolean typeMatchingWithNamePreFilter = tracer.getConfig(CoreConfiguration.class).isTypeMatchingWithNamePreFilter();
        return agentBuilder
            .type(new AgentBuilder.RawMatcher() {
                @Override
                public boolean matches(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, Class<?> classBeingRedefined, ProtectionDomain protectionDomain) {
                    long start = System.nanoTime();
                    try {
                        if (!advice.getClassLoaderMatcher().matches(classLoader)) {
                            return false;
                        }
                        if (typeMatchingWithNamePreFilter && !advice.getTypeMatcherPreFilter().matches(typeDescription)) {
                            return false;
                        }
                        boolean typeMatches;
                        try {
                            typeMatches = advice.getTypeMatcher().matches(typeDescription);
                        } catch (Exception ignored) {
                            // happens for example on WebSphere, not sure why ¯\_(ツ)_/¯
                            typeMatches = false;
                        }
                        if (typeMatches) {
                            logger.debug("Type match for advice {}: {} matches {}",
                                advice.getClass().getSimpleName(), advice.getTypeMatcher(), typeDescription);
                            if (logger.isTraceEnabled()) {
                                logClassLoaderHierarchy(classLoader, logger, advice);
                            }
                        }
                        return typeMatches;
                    } finally {
                        getOrCreateTimer(advice.getClass()).addTypeMatchingDuration(System.nanoTime() - start);
                    }
                }
            })
            .transform(new AgentBuilder.Transformer.ForAdvice(Advice
                .withCustomMapping()
                .bind(new SimpleMethodSignatureOffsetMappingFactory())
                .bind(new AnnotationValueOffsetMappingFactory()))
                .advice(new ElementMatcher<MethodDescription>() {
                    @Override
                    public boolean matches(MethodDescription target) {
                        long start = System.nanoTime();
                        try {
                            final boolean matches = advice.getMethodMatcher().matches(target);
                            if (matches) {
                                logger.debug("Method match for advice {}: {} matches {}",
                                    advice.getClass().getSimpleName(), advice.getMethodMatcher(), target);
                            }
                            return matches;
                        } finally {
                            getOrCreateTimer(advice.getClass()).addMethodMatchingDuration(System.nanoTime() - start);
                        }
                    }
                }, advice.getAdviceClass().getName())
                .include(ClassLoader.getSystemClassLoader())
                .withExceptionHandler(PRINTING))
            .transform(new AgentBuilder.Transformer() {
                @Override
                public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription,
                                                        ClassLoader classLoader, JavaModule module) {
                    return builder.visit(MinimumClassFileVersionValidator.INSTANCE);
                }
            });
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

    private static AgentBuilder getAgentBuilder(final ByteBuddy byteBuddy, final CoreConfiguration coreConfiguration) {
        final List<WildcardMatcher> excludedFromInstrumentation = coreConfiguration.getExcludedFromInstrumentation();
        return new AgentBuilder.Default(byteBuddy)
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(AgentBuilder.DescriptionStrategy.Default.POOL_ONLY)
            .with(new ErrorLoggingListener())
            // ReaderMode.FAST as we don't need to read method parameter names
            .with(coreConfiguration.isTypePoolCacheEnabled()
                ? new SoftlyReferencingTypePoolCache(TypePool.Default.ReaderMode.FAST, 1, isReflectionClassLoader())
                : AgentBuilder.PoolStrategy.Default.FAST)
            .ignore(any(), isReflectionClassLoader())
            .or(any(), classLoaderWithName("org.codehaus.groovy.runtime.callsite.CallSiteClassLoader"))
            .or(nameStartsWith("java."))
            .or(nameStartsWith("com.sun."))
            .or(nameStartsWith("sun"))
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
                    return WildcardMatcher.anyMatch(excludedFromInstrumentation, target.getName()) != null;
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
