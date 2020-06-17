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
import co.elastic.apm.agent.collections.WeakMapSupplier;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.util.DependencyInjectingServiceLoader;
import co.elastic.apm.agent.util.ExecutorUtils;
import co.elastic.apm.agent.util.ThreadUtils;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
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
import org.stagemonitor.configuration.ConfigurationOption;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static co.elastic.apm.agent.bci.ElasticApmInstrumentation.tracer;
import static co.elastic.apm.agent.bci.bytebuddy.ClassLoaderNameMatcher.classLoaderWithName;
import static co.elastic.apm.agent.bci.bytebuddy.ClassLoaderNameMatcher.isReflectionClassLoader;
import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.anyMatch;
import static net.bytebuddy.asm.Advice.ExceptionHandler.Default.PRINTING;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;
import static net.bytebuddy.matcher.ElementMatchers.not;

public class ElasticApmAgent {

    // Don't init logger as a static field, logging needs to be initialized first see also issue #593
    // private static final Logger doNotUseThisLogger = LoggerFactory.getLogger(ElasticApmAgent.class);

    private static final ConcurrentMap<String, MatcherTimer> matcherTimers = new ConcurrentHashMap<>();
    @Nullable
    private static Instrumentation instrumentation;
    @Nullable
    private static ResettableClassFileTransformer resettableClassFileTransformer;
    private static final List<ResettableClassFileTransformer> dynamicClassFileTransformers = new ArrayList<>();
    private static final WeakConcurrentMap<Class<?>, Set<Collection<Class<? extends ElasticApmInstrumentation>>>> dynamicallyInstrumentedClasses = WeakMapSupplier.createMap();
    @Nullable
    private static File agentJarFile;

    /**
     * Called reflectively by {@link AgentMain} to initialize the agent
     *
     * @param instrumentation the instrumentation instance
     * @param agentJarFile    a reference to the agent jar on the file system
     */
    @SuppressWarnings("unused") // called through reflection
    public static void initialize(String agentArguments, Instrumentation instrumentation, File agentJarFile, boolean premain) {
        ElasticApmAgent.agentJarFile = agentJarFile;
        ElasticApmTracer tracer = new ElasticApmTracerBuilder(agentArguments).build();
        // ensure classes can be instrumented before LifecycleListeners use them by starting the tracer after initializing instrumentation
        initInstrumentation(tracer, instrumentation, loadInstrumentations(tracer), premain);
        tracer.start();
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

    public static synchronized void initInstrumentation(final ElasticApmTracer tracer, Instrumentation instrumentation,
                                                        Iterable<ElasticApmInstrumentation> instrumentations) {
        initInstrumentation(tracer, instrumentation, instrumentations, false);
    }

    private static synchronized void initInstrumentation(final ElasticApmTracer tracer, Instrumentation instrumentation,
                                                        Iterable<ElasticApmInstrumentation> instrumentations, boolean premain) {
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
        // POOL_ONLY because we don't want to cause eager linking on startup as the class path may not be complete yet
        AgentBuilder agentBuilder = initAgentBuilder(tracer, instrumentation, instrumentations, logger, AgentBuilder.DescriptionStrategy.Default.POOL_ONLY, premain);
        resettableClassFileTransformer = agentBuilder.installOn(ElasticApmAgent.instrumentation);
        CoreConfiguration coreConfig = tracer.getConfig(CoreConfiguration.class);
        for (ConfigurationOption<?> instrumentationOption : coreConfig.getInstrumentationOptions()) {
            instrumentationOption.addChangeListener(new ConfigurationOption.ChangeListener() {
                @Override
                public void onChange(ConfigurationOption configurationOption, Object oldValue, Object newValue) {
                    reInitInstrumentation();
                }
            });
        }
    }

    public static synchronized Future<?> reInitInstrumentation() {
        final ElasticApmTracer tracer = ElasticApmInstrumentation.tracer;
        if (tracer == null || instrumentation == null) {
            throw new IllegalStateException("Can't re-init agent before it has been initialized");
        }
        ThreadPoolExecutor executor = ExecutorUtils.createSingleThreadDeamonPool("apm-reinit", 1);
        try {
            return executor.submit(new Runnable() {
                @Override
                public void run() {
                    doReInitInstrumentation(loadInstrumentations(tracer));
                }
            });
        } finally {
            executor.shutdown();
        }
    }

    static synchronized void doReInitInstrumentation(Iterable<ElasticApmInstrumentation> instrumentations) {
        final Logger logger = LoggerFactory.getLogger(ElasticApmAgent.class);
        logger.info("Re initializing instrumentation");
        AgentBuilder agentBuilder = initAgentBuilder(tracer, instrumentation, instrumentations, logger, AgentBuilder.DescriptionStrategy.Default.POOL_ONLY, false);

        resettableClassFileTransformer = agentBuilder.patchOn(instrumentation, resettableClassFileTransformer);
    }

    private static AgentBuilder initAgentBuilder(ElasticApmTracer tracer, Instrumentation instrumentation,
                                                 Iterable<ElasticApmInstrumentation> instrumentations, Logger logger,
                                                 AgentBuilder.DescriptionStrategy descriptionStrategy, boolean premain) {
        final CoreConfiguration coreConfiguration = tracer.getConfig(CoreConfiguration.class);
        ElasticApmAgent.instrumentation = instrumentation;
        final ByteBuddy byteBuddy = new ByteBuddy()
            .with(TypeValidation.of(logger.isDebugEnabled()))
            .with(FailSafeDeclaredMethodsCompiler.INSTANCE);
        AgentBuilder agentBuilder = getAgentBuilder(byteBuddy, coreConfiguration, logger, descriptionStrategy, premain);
        int numberOfAdvices = 0;
        for (final ElasticApmInstrumentation advice : instrumentations) {
            if (isIncluded(advice, coreConfiguration)) {
                numberOfAdvices++;
                agentBuilder = applyAdvice(tracer, agentBuilder, advice, new ElementMatcher.Junction.Conjunction<>(advice.getTypeMatcher(), not(isInterface())));
            }
        }
        logger.debug("Applied {} advices", numberOfAdvices);
        return agentBuilder;
    }

    private static boolean isIncluded(ElasticApmInstrumentation advice, CoreConfiguration coreConfiguration) {
        ArrayList<String> disabledInstrumentations = new ArrayList<>(coreConfiguration.getDisabledInstrumentations());
        // Supporting the deprecated `incubating` tag for backward compatibility
        if (disabledInstrumentations.contains("incubating")) {
            disabledInstrumentations.add("experimental");
        }
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
                                            final ElasticApmInstrumentation instrumentation, final ElementMatcher<? super TypeDescription> typeMatcher) {
        final Logger logger = LoggerFactory.getLogger(ElasticApmAgent.class);
        logger.debug("Applying instrumentation {}", instrumentation.getClass().getName());
        final boolean classLoadingMatchingPreFilter = tracer.getConfig(CoreConfiguration.class).isClassLoadingMatchingPreFilter();
        final boolean typeMatchingWithNamePreFilter = tracer.getConfig(CoreConfiguration.class).isTypeMatchingWithNamePreFilter();
        final ElementMatcher.Junction<ClassLoader> classLoaderMatcher = instrumentation.getClassLoaderMatcher();
        final ElementMatcher<? super NamedElement> typeMatcherPreFilter = instrumentation.getTypeMatcherPreFilter();
        final ElementMatcher.Junction<ProtectionDomain> versionPostFilter = instrumentation.getImplementationVersionPostFilter();
        final ElementMatcher<? super MethodDescription> methodMatcher = new ElementMatcher.Junction.Conjunction<>(instrumentation.getMethodMatcher(), not(isAbstract()));
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
     * NOTE: THIS IS ONLY TO BE USED FOR UNIT TESTS
     * NOTE2: THIS METHOD MUST BE CALLED AFTER AGENT WAS INITIALIZED
     * </p>
     */
    public static synchronized void reset() {
        if (instrumentation == null) {
            return;
        }

        if (resettableClassFileTransformer == null) {
            throw new IllegalStateException("Reset was called before init");
        }
        dynamicallyInstrumentedClasses.clear();
        resettableClassFileTransformer.reset(instrumentation, RedefinitionStrategy.RETRANSFORMATION);
        resettableClassFileTransformer = null;
        for (ResettableClassFileTransformer transformer : dynamicClassFileTransformers) {
            transformer.reset(instrumentation, RedefinitionStrategy.RETRANSFORMATION);
        }
        dynamicClassFileTransformers.clear();
        instrumentation = null;
    }

    private static AgentBuilder getAgentBuilder(final ByteBuddy byteBuddy, final CoreConfiguration coreConfiguration, Logger logger, AgentBuilder.DescriptionStrategy descriptionStrategy, boolean premain) {
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
            .with(RedefinitionStrategy.RETRANSFORMATION)
            // when runtime attaching, only retransform up to 100 classes at once and sleep 100ms in-between as retransformation causes a stop-the-world pause
            .with(premain ? RedefinitionStrategy.BatchAllocator.ForTotal.INSTANCE : RedefinitionStrategy.BatchAllocator.ForFixedSize.ofSize(100))
            .with(premain ? RedefinitionStrategy.Listener.NoOp.INSTANCE : RedefinitionStrategy.Listener.Pausing.of(100, TimeUnit.MILLISECONDS))
            .with(descriptionStrategy)
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
            .or(nameStartsWith("com.newrelic."))
            .or(nameStartsWith("com.dynatrace."))
            // AppDynamics
            .or(nameStartsWith("com.singularity."))
            .or(nameStartsWith("com.instana."))
            .or(nameStartsWith("datadog."))
            .or(nameStartsWith("org.glowroot."))
            .or(nameStartsWith("com.compuware."))
            .or(nameStartsWith("io.sqreen."))
            .or(nameContains("javassist"))
            .or(nameContains(".asm."))
            .or(anyMatch(coreConfiguration.getDefaultClassesExcludedFromInstrumentation()))
            .or(anyMatch(coreConfiguration.getClassesExcludedFromInstrumentation()))
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

    /**
     * Instruments a specific class at runtime with one or multiple instrumentation classes.
     * <p>
     * Note that {@link ElasticApmInstrumentation#getTypeMatcher()} will be
     * {@linkplain net.bytebuddy.matcher.ElementMatcher.Junction#and(ElementMatcher) conjoined} with a
     * {@linkplain #getTypeMatcher(Class, ElementMatcher, ElementMatcher.Junction) computed} {@link TypeDescription}
     * that is specific to the provided class to instrument.
     * </p>
     *
     * @param classToInstrument the class which should be instrumented
     * @param instrumentationClasses the instrumentation which should be applied to the class to instrument.
     */
    public static void ensureInstrumented(Class<?> classToInstrument, Collection<Class<? extends ElasticApmInstrumentation>> instrumentationClasses) {
        Set<Collection<Class<? extends ElasticApmInstrumentation>>> appliedInstrumentations = getOrCreate(classToInstrument);

        if (!appliedInstrumentations.contains(instrumentationClasses)) {
            synchronized (ElasticApmAgent.class) {
                ElasticApmTracer tracer = ElasticApmInstrumentation.tracer;
                if (tracer == null || instrumentation == null) {
                    throw new IllegalStateException("Agent is not initialized");
                }

                if (!appliedInstrumentations.contains(instrumentationClasses)) {
                    appliedInstrumentations = new HashSet<>(appliedInstrumentations);
                    appliedInstrumentations.add(instrumentationClasses);
                    // immutability guards against race conditions (for example concurrent rehash due to add and lookup)
                    appliedInstrumentations = Collections.unmodifiableSet(appliedInstrumentations);
                    dynamicallyInstrumentedClasses.put(classToInstrument, appliedInstrumentations);

                    CoreConfiguration config = tracer.getConfig(CoreConfiguration.class);
                    final Logger logger = LoggerFactory.getLogger(ElasticApmAgent.class);
                    final ByteBuddy byteBuddy = new ByteBuddy()
                        .with(TypeValidation.of(logger.isDebugEnabled()))
                        .with(FailSafeDeclaredMethodsCompiler.INSTANCE);
                    AgentBuilder agentBuilder = getAgentBuilder(byteBuddy, config, logger, AgentBuilder.DescriptionStrategy.Default.HYBRID, false);
                    for (Class<? extends ElasticApmInstrumentation> instrumentationClass : instrumentationClasses) {
                        ElasticApmInstrumentation apmInstrumentation = instantiate(instrumentationClass);
                        ElementMatcher.Junction<? super TypeDescription> typeMatcher = getTypeMatcher(classToInstrument, apmInstrumentation.getMethodMatcher(), none());
                        if (typeMatcher != null && isIncluded(apmInstrumentation, config)) {
                            agentBuilder = applyAdvice(tracer, agentBuilder, apmInstrumentation, typeMatcher.and(apmInstrumentation.getTypeMatcher()));
                        }
                    }
                    dynamicClassFileTransformers.add(agentBuilder.installOn(instrumentation));
                }
            }
        }
    }

    private static Set<Collection<Class<? extends ElasticApmInstrumentation>>> getOrCreate(Class<?> classToInstrument) {
        Set<Collection<Class<? extends ElasticApmInstrumentation>>> instrumentedClasses = dynamicallyInstrumentedClasses.get(classToInstrument);
        if (instrumentedClasses == null) {
            instrumentedClasses = new HashSet<Collection<Class<? extends ElasticApmInstrumentation>>>();
            Set<Collection<Class<? extends ElasticApmInstrumentation>>> racy = dynamicallyInstrumentedClasses.put(classToInstrument, instrumentedClasses);
            if (racy != null) {
                instrumentedClasses = racy;
            }
        }
        return instrumentedClasses;
    }

    @Nullable
    private static ElementMatcher.Junction<? super TypeDescription> getTypeMatcher(@Nullable Class<?> classToInstrument, ElementMatcher<? super MethodDescription> methodMatcher, ElementMatcher.Junction<? super TypeDescription> typeMatcher) {
        if (classToInstrument == null) {
            return typeMatcher;
        }
        if (matches(classToInstrument, methodMatcher)) {
            // even if we have a match in this class, there could be another match in a super class
            //if the method matcher matches multiple methods
            typeMatcher = is(classToInstrument).or(typeMatcher);
        }
        return getTypeMatcher(classToInstrument.getSuperclass(), methodMatcher, typeMatcher);
    }

    private static boolean matches(Class<?> classToInstrument, ElementMatcher<? super MethodDescription> methodMatcher) {
        return !TypeDescription.ForLoadedType.of(classToInstrument).getDeclaredMethods().filter(methodMatcher).isEmpty();
    }

    private static ElasticApmInstrumentation instantiate(Class<? extends ElasticApmInstrumentation> instrumentation) {

        ElasticApmInstrumentation instance = tryInstantiate(instrumentation, false);
        if (instance == null) {
            instance = tryInstantiate(instrumentation, true);
        }

        if (instance == null) {
            throw new IllegalArgumentException("unable to find matching public constructor for instrumentation " + instrumentation);
        }

        return instance;
    }

    @Nullable
    private static ElasticApmInstrumentation tryInstantiate(Class<? extends ElasticApmInstrumentation> instrumentation, boolean withTracer) {

        Constructor<? extends ElasticApmInstrumentation> constructor = null;
        try {
            if (withTracer) {
                constructor = instrumentation.getConstructor(ElasticApmTracer.class);
            } else {
                constructor = instrumentation.getConstructor();
            }
        } catch (NoSuchMethodException e) {
            // silently ignored
        }

        ElasticApmInstrumentation instance = null;
        if (constructor != null) {
            try {
                if (withTracer) {
                    instance = constructor.newInstance(ElasticApmInstrumentation.tracer);
                } else {
                    instance = constructor.newInstance();
                }
            } catch (ReflectiveOperationException e) {
                // silently ignored
            }
        }

        return instance;
    }

}
