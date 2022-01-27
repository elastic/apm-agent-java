/*
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
 */
package co.elastic.apm.agent.bci;

import co.elastic.apm.agent.bci.bytebuddy.AnnotationValueOffsetMappingFactory;
import co.elastic.apm.agent.bci.bytebuddy.ErrorLoggingListener;
import co.elastic.apm.agent.bci.bytebuddy.FailSafeDeclaredMethodsCompiler;
import co.elastic.apm.agent.bci.bytebuddy.InstallationListenerImpl;
import co.elastic.apm.agent.bci.bytebuddy.Instrumented;
import co.elastic.apm.agent.bci.bytebuddy.LruTypePoolCache;
import co.elastic.apm.agent.bci.bytebuddy.MatcherTimer;
import co.elastic.apm.agent.bci.bytebuddy.MinimumClassFileVersionValidator;
import co.elastic.apm.agent.bci.bytebuddy.NonInstrumented;
import co.elastic.apm.agent.bci.bytebuddy.PatchBytecodeVersionTo51Transformer;
import co.elastic.apm.agent.bci.bytebuddy.RootPackageCustomLocator;
import co.elastic.apm.agent.bci.bytebuddy.SimpleMethodSignatureOffsetMappingFactory;
import co.elastic.apm.agent.bci.classloading.ExternalPluginClassLoader;
import co.elastic.apm.agent.common.ThreadUtils;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.matcher.MethodMatcher;
import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import co.elastic.apm.agent.tracemethods.TraceMethodInstrumentation;
import co.elastic.apm.agent.util.DependencyInjectingServiceLoader;
import co.elastic.apm.agent.util.ExecutorUtils;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.TypeConstantAdjustment;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.scaffold.TypeValidation;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.JavaModule;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.source.ConfigurationSource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.file.Paths;
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

import static co.elastic.apm.agent.bci.bytebuddy.ClassLoaderNameMatcher.classLoaderWithName;
import static co.elastic.apm.agent.bci.bytebuddy.ClassLoaderNameMatcher.isReflectionClassLoader;
import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.anyMatch;
import static net.bytebuddy.asm.Advice.ExceptionHandler.Default.PRINTING;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.none;
import static net.bytebuddy.matcher.ElementMatchers.not;

public class ElasticApmAgent {

    // Don't eagerly create logger. Logging needs to be initialized first based on configuration. See also issue #593
    @Nullable
    private static Logger logger;

    private static final ConcurrentMap<String, MatcherTimer> matcherTimers = new ConcurrentHashMap<>();
    @Nullable
    private static Instrumentation instrumentation;
    @Nullable
    private static ResettableClassFileTransformer resettableClassFileTransformer;
    private static final List<ResettableClassFileTransformer> dynamicClassFileTransformers = new ArrayList<>();
    private static final WeakMap<Class<?>, Set<Collection<Class<? extends ElasticApmInstrumentation>>>> dynamicallyInstrumentedClasses = WeakConcurrent.buildMap();
    @Nullable
    private static File agentJarFile;

    /**
     * A mapping from advice class name to the class loader that loaded the corresponding instrumentation.
     * We need this in order to locate the advice class file. This implies that the advice class needs to be collocated
     * with the corresponding instrumentation class.
     */
    private static final ConcurrentMap<String, ClassLoader> adviceClassName2instrumentationClassLoader = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Collection<String>> pluginPackages2pluginClassLoaderRootPackages = new ConcurrentHashMap<>();

    /**
     * Called reflectively by {@code co.elastic.apm.agent.premain.AgentMain} to initialize the agent
     *
     * @param instrumentation the instrumentation instance
     * @param agentJarFile    a reference to the agent jar on the file system
     */
    @SuppressWarnings("unused") // called through reflection
    public static void initialize(@Nullable final String agentArguments, final Instrumentation instrumentation, final File agentJarFile, final boolean premain) {
        ElasticApmAgent.agentJarFile = agentJarFile;

        // silently early abort when agent is disabled to minimize the number of loaded classes
        List<ConfigurationSource> configSources = ElasticApmTracerBuilder.getConfigSources(agentArguments, premain);
        for (ConfigurationSource configSource : configSources) {
            String enabled = configSource.getValue(CoreConfiguration.ENABLED_KEY);
            if (enabled != null && !Boolean.parseBoolean(enabled)) {
                return;
            }
        }

        ElasticApmTracer tracer = new ElasticApmTracerBuilder(configSources).build();
        initInstrumentation(tracer, instrumentation, premain);
        tracer.start(premain);
    }

    public static void initInstrumentation(ElasticApmTracer tracer, Instrumentation instrumentation) {
        initInstrumentation(tracer, instrumentation, false);
    }

    private static void initInstrumentation(ElasticApmTracer tracer, Instrumentation instrumentation, boolean premain) {
        if (!tracer.getConfig(CoreConfiguration.class).isEnabled()) {
            return;
        }
        GlobalTracer.init(tracer);
        // ensure classes can be instrumented before LifecycleListeners use them by starting the tracer after initializing instrumentation
        initInstrumentation(tracer, instrumentation, loadInstrumentations(tracer), premain);
    }

    @Nonnull
    private static Iterable<ElasticApmInstrumentation> loadInstrumentations(ElasticApmTracer tracer) {
        List<ClassLoader> pluginClassLoaders = new ArrayList<>();
        pluginClassLoaders.add(ElasticApmAgent.class.getClassLoader());
        pluginClassLoaders.addAll(createExternalPluginClassLoaders(tracer.getConfig(CoreConfiguration.class).getPluginsDir()));
        final List<ElasticApmInstrumentation> instrumentations = DependencyInjectingServiceLoader.load(ElasticApmInstrumentation.class, pluginClassLoaders, tracer);
        for (MethodMatcher traceMethod : tracer.getConfig(CoreConfiguration.class).getTraceMethods()) {
            instrumentations.add(new TraceMethodInstrumentation(tracer, traceMethod));
        }
        return instrumentations;
    }

    private static Collection<? extends ClassLoader> createExternalPluginClassLoaders(@Nullable String pluginsDirString) {
        final Logger logger = LoggerFactory.getLogger(ElasticApmAgent.class);
        if (pluginsDirString == null) {
            logger.debug("No plugins dir");
            return Collections.emptyList();
        }
        File pluginsDir = new File(pluginsDirString);
        if (!pluginsDir.exists()) {
            logger.debug("Plugins dir does not exist: {}", pluginsDirString);
            return Collections.emptyList();
        }
        File[] pluginJars = pluginsDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".jar");
            }
        });
        if (pluginJars == null) {
            logger.info("Invalid plugins dir {}", pluginsDirString);
            return Collections.emptyList();
        }
        List<ClassLoader> result = new ArrayList<>(pluginJars.length);
        for (File pluginJar : pluginJars) {
            logger.info("Loading plugin {}", pluginJar.getName());
            try {
                result.add(new ExternalPluginClassLoader(pluginJar, ElasticApmAgent.class.getClassLoader()));
            } catch (Exception e) {
                logger.error("Error loading external plugin", e);
            }
        }
        return result;
    }



    public static synchronized void initInstrumentation(final ElasticApmTracer tracer, Instrumentation instrumentation,
                                                        Iterable<ElasticApmInstrumentation> instrumentations) {
        GlobalTracer.init(tracer);
        initInstrumentation(tracer, instrumentation, instrumentations, false);
    }

    private static synchronized void initInstrumentation(final ElasticApmTracer tracer, Instrumentation instrumentation,
                                                         Iterable<ElasticApmInstrumentation> instrumentations, boolean premain) {
        CoreConfiguration coreConfig = tracer.getConfig(CoreConfiguration.class);
        if (!coreConfig.isEnabled()) {
            return;
        }
        String bytecodeDumpPath = coreConfig.getBytecodeDumpPath();
        if (bytecodeDumpPath != null) {
            bytecodeDumpPath = bytecodeDumpPath.trim();
            if (!bytecodeDumpPath.isEmpty()) {
                try {
                    File bytecodeDumpDir = Paths.get(bytecodeDumpPath).toFile();
                    if (!bytecodeDumpDir.exists()) {
                        bytecodeDumpDir.mkdirs();
                    }
                    System.setProperty("net.bytebuddy.dump", bytecodeDumpDir.getPath());
                } catch (Exception e) {
                    System.err.println("[elastic-apm-agent] WARN Failed to create directory to dump instrumented bytecode: " + e.getMessage());
                }
            }
        }
        final List<PluginClassLoaderRootPackageCustomizer> rootPackageCustomizers = DependencyInjectingServiceLoader.load(
            PluginClassLoaderRootPackageCustomizer.class,
            getAgentClassLoader());
        for (PluginClassLoaderRootPackageCustomizer rootPackageCustomizer : rootPackageCustomizers) {
            Collection<String> previous = pluginPackages2pluginClassLoaderRootPackages.put(
                rootPackageCustomizer.getPluginPackage(),
                Collections.unmodifiableList(new ArrayList<>(rootPackageCustomizer.pluginClassLoaderRootPackages())));
            if (previous != null) {
                throw new IllegalStateException("Only one PluginClassLoaderRootPackageCustomizer is allowed per plugin package: "
                    + rootPackageCustomizer.getPluginPackage());
            }
        }
        for (ElasticApmInstrumentation apmInstrumentation : instrumentations) {
            mapInstrumentationCL2adviceClassName(
                apmInstrumentation.getAdviceClassName(),
                apmInstrumentation.getClass().getClassLoader());
        }
        Runtime.getRuntime().addShutdownHook(new Thread(ThreadUtils.addElasticApmThreadPrefix("init-instrumentation-shutdown-hook")) {
            @Override
            public void run() {
                tracer.stop();
                matcherTimers.clear();
            }
        });
        matcherTimers.clear();
        Logger logger = getLogger();
        if (ElasticApmAgent.instrumentation != null) {
            logger.warn("Instrumentation has already been initialized");
            return;
        }
        // POOL_ONLY because we don't want to cause eager linking on startup as the class path may not be complete yet
        AgentBuilder agentBuilder = initAgentBuilder(tracer, instrumentation, instrumentations, logger, AgentBuilder.DescriptionStrategy.Default.POOL_ONLY, premain);

        // Warmup Byte Buddy and agent's invokedynamic linkage paths on the attaching thread before installing it
        if (tracer.getConfig(CoreConfiguration.class).shouldWarmupByteBuddy()) {
            agentBuilder = agentBuilder.with(new InstallationListenerImpl())
                .warmUp(NonInstrumented.class)
                .warmUp(Instrumented.class);
        }

        resettableClassFileTransformer = agentBuilder.installOn(ElasticApmAgent.instrumentation);
        for (ConfigurationOption<?> instrumentationOption : coreConfig.getInstrumentationOptions()) {
            //noinspection Convert2Lambda
            instrumentationOption.addChangeListener(new ConfigurationOption.ChangeListener() {
                @Override
                public void onChange(ConfigurationOption configurationOption, Object oldValue, Object newValue) {
                    reInitInstrumentation();
                }
            });
        }
    }

    public static synchronized Future<?> reInitInstrumentation() {
        final ElasticApmTracer tracer = GlobalTracer.requireTracerImpl();
        if (instrumentation == null) {
            throw new IllegalStateException("Can't re-init agent before it has been initialized");
        }
        ThreadPoolExecutor executor = ExecutorUtils.createSingleThreadDaemonPool("apm-reinit", 1);
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
        Logger logger = getLogger();
        logger.info("Re initializing instrumentation");
        AgentBuilder agentBuilder = initAgentBuilder(GlobalTracer.requireTracerImpl(), instrumentation, instrumentations, logger, AgentBuilder.DescriptionStrategy.Default.POOL_ONLY, false);

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
        AgentBuilder agentBuilder = getAgentBuilder(
            byteBuddy, coreConfiguration, logger, descriptionStrategy, premain, coreConfiguration.isTypePoolCacheEnabled()
        );
        int numberOfAdvices = 0;
        for (final ElasticApmInstrumentation advice : instrumentations) {
            if (isIncluded(advice, coreConfiguration)) {
                try {
                    agentBuilder = applyAdvice(tracer, agentBuilder, advice, advice.getTypeMatcher());
                    numberOfAdvices++;
                } catch (Exception e) {
                    logger.error("Exception occurred while applying instrumentation {}", advice.getClass().getName(), e);
                    // this should fail tests but skip the instrumentations in prod
                    assert false;
                }
            } else {
                logger.debug("Not applying excluded instrumentation {}", advice.getClass().getName());
            }
        }
        logger.debug("Applied {} advices", numberOfAdvices);
        return agentBuilder;
    }

    private static boolean isIncluded(ElasticApmInstrumentation advice, CoreConfiguration coreConfiguration) {
        return isInstrumentationEnabled(advice, coreConfiguration) && coreConfiguration.isInstrumentationEnabled(advice.getInstrumentationGroupNames());
    }

    private static boolean isInstrumentationEnabled(ElasticApmInstrumentation advice, CoreConfiguration coreConfiguration) {
        return advice.includeWhenInstrumentationIsDisabled() || coreConfiguration.isInstrument();
    }

    private static AgentBuilder applyAdvice(final ElasticApmTracer tracer, final AgentBuilder agentBuilder,
                                            final ElasticApmInstrumentation instrumentation, final ElementMatcher<? super TypeDescription> typeMatcher) {
        final Logger logger = getLogger();
        logger.debug("Applying instrumentation {}", instrumentation.getClass().getName());
        final boolean classLoadingMatchingPreFilter = tracer.getConfig(CoreConfiguration.class).isClassLoadingMatchingPreFilter();
        final boolean typeMatchingWithNamePreFilter = tracer.getConfig(CoreConfiguration.class).isTypeMatchingWithNamePreFilter();
        final ElementMatcher.Junction<ClassLoader> classLoaderMatcher = instrumentation.getClassLoaderMatcher();
        final ElementMatcher<? super NamedElement> typeMatcherPreFilter = instrumentation.getTypeMatcherPreFilter();
        final ElementMatcher.Junction<ProtectionDomain> versionPostFilter = instrumentation.getProtectionDomainPostFilter();
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
            .transform(new PatchBytecodeVersionTo51Transformer())
            .transform(getTransformer(instrumentation, logger, methodMatcher))
            .transform(new AgentBuilder.Transformer() {
                @Override
                public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription,
                                                        ClassLoader classLoader, JavaModule module) {
                    return builder.visit(MinimumClassFileVersionValidator.V1_4)
                        // As long as we allow 1.4 bytecode, we need to add this constant pool adjustment as well
                        .visit(TypeConstantAdjustment.INSTANCE);
                }
            });
    }

    private static Logger getLogger() {
        if (logger == null) {
            // lazily init logger to allow the tracer builder to init the logging config first
            GlobalTracer.requireTracerImpl();
            logger = LoggerFactory.getLogger(ElasticApmAgent.class);
        }
        // re-using an existing logger avoids running into a JVM bug that leads to a segfault
        // this happens when StackWalker runs concurrently to class redefinitions
        // see https://bugs.openjdk.java.net/browse/JDK-8210457
        // this crash could only be reproduced in tests so far
        return logger;
    }

    private static AgentBuilder.Transformer.ForAdvice getTransformer(final ElasticApmInstrumentation instrumentation, final Logger logger, final ElementMatcher<? super MethodDescription> methodMatcher) {
        validateAdvice(instrumentation);
        Advice.WithCustomMapping withCustomMapping = Advice
            .withCustomMapping()
            .with(new Advice.AssignReturned.Factory().withSuppressed(ClassCastException.class))
            .bind(new SimpleMethodSignatureOffsetMappingFactory())
            .bind(new AnnotationValueOffsetMappingFactory());
        Advice.OffsetMapping.Factory<?> offsetMapping = instrumentation.getOffsetMapping();
        if (offsetMapping != null) {
            withCustomMapping = withCustomMapping.bind(offsetMapping);
        }
        withCustomMapping = withCustomMapping.bootstrap(IndyBootstrap.getIndyBootstrapMethod(logger));
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
            }, instrumentation.getAdviceClassName())
            .include(ClassLoader.getSystemClassLoader(), instrumentation.getClass().getClassLoader())
            .withExceptionHandler(PRINTING);
    }

    /**
     * Validates invariants explained in {@link ElasticApmInstrumentation} and {@link ElasticApmInstrumentation#getAdviceClassName()}
     */
    public static void validateAdvice(ElasticApmInstrumentation instrumentation) {
        String adviceClassName = instrumentation.getAdviceClassName();
        if (instrumentation.getClass().getName().equals(adviceClassName)) {
            throw new IllegalStateException("The advice must be declared in a separate class: " + adviceClassName);
        }
        ClassLoader adviceClassLoader = instrumentation.getClass().getClassLoader();
        if (adviceClassLoader == null) {
            // the bootstrap class loader can't do resource lookup
            // if classes are added via java.lang.instrument.Instrumentation.appendToBootstrapClassLoaderSearch
            adviceClassLoader = ClassLoader.getSystemClassLoader();
        }
        TypePool pool = new TypePool.Default.WithLazyResolution(
            TypePool.CacheProvider.NoOp.INSTANCE,
            ClassFileLocator.ForClassLoader.of(adviceClassLoader),
            TypePool.Default.ReaderMode.FAST);
        TypeDescription typeDescription = pool.describe(adviceClassName).resolve();
        int adviceModifiers = typeDescription.getModifiers();
        if (!Modifier.isPublic(adviceModifiers)) {
            throw new IllegalStateException(String.format("advice class %s should be public", adviceClassName));
        }
        for (MethodDescription.InDefinedShape enterAdvice : typeDescription.getDeclaredMethods().filter(isStatic().and(isAnnotatedWith(Advice.OnMethodEnter.class)))) {
            validateAdviceReturnAndParameterTypes(enterAdvice, adviceClassName);
            validateLegacyAssignToIsNotUsed(enterAdvice);

            for (AnnotationDescription enter : enterAdvice.getDeclaredAnnotations().filter(ElementMatchers.annotationType(Advice.OnMethodEnter.class))) {
                checkInline(enterAdvice, adviceClassName, enter.prepare(Advice.OnMethodEnter.class).load().inline());
            }
        }
        for (MethodDescription.InDefinedShape exitAdvice : typeDescription.getDeclaredMethods().filter(isStatic().and(isAnnotatedWith(Advice.OnMethodExit.class)))) {
            validateAdviceReturnAndParameterTypes(exitAdvice, adviceClassName);
            validateLegacyAssignToIsNotUsed(exitAdvice);
            if (exitAdvice.getReturnType().asRawType().getTypeName().startsWith("co.elastic.apm")) {
                throw new IllegalStateException("Advice return type must be visible from the bootstrap class loader and must not be an agent type.");
            }
            for (AnnotationDescription exit : exitAdvice.getDeclaredAnnotations().filter(ElementMatchers.annotationType(Advice.OnMethodExit.class))) {
                checkInline(exitAdvice, adviceClassName, exit.prepare(Advice.OnMethodExit.class).load().inline());
            }
        }
        if (!(adviceClassLoader instanceof ExternalPluginClassLoader) && !adviceClassName.startsWith("co.elastic.apm.agent.")) {
            throw new IllegalStateException(String.format(
                "Invalid Advice class - %s - Indy-dispatched advice class must be in a sub-package of 'co.elastic.apm.agent'.",
                adviceClassName)
            );

        }
    }

    private static void checkInline(MethodDescription.InDefinedShape advice, String adviceClassName, boolean isInline){
        if (isInline) {
            throw new IllegalStateException(String.format("Indy-dispatched advice %s#%s has to be declared with inline=false", adviceClassName, advice.getName()));
        } else if (!Modifier.isPublic(advice.getModifiers())) {
            throw new IllegalStateException(String.format("Indy-dispatched advice %s#%s has to be declared public", adviceClassName, advice.getName()));
        }
    }

    private static void validateLegacyAssignToIsNotUsed(MethodDescription.InDefinedShape advice) {
        boolean usesLegacyAssignToAnnotations = !advice.getDeclaredAnnotations()
            .asTypeList()
            .filter(nameStartsWith("co.elastic.apm.agent.sdk.advice.AssignTo"))
            .isEmpty();
        if (usesLegacyAssignToAnnotations) {
            throw new IllegalStateException("@AssignTo.* annotations have been removed in favor of Byte Buddy's @Advice.AssignReturned.* annotations");
        }
    }

    private static void validateAdviceReturnAndParameterTypes(MethodDescription.InDefinedShape advice, String adviceClass) {
        String adviceMethod = advice.getInternalName();
        try {
            checkNotAgentType(advice.getReturnType(), "return type", adviceClass, adviceMethod);

            for (ParameterDescription.InDefinedShape parameter : advice.getParameters()) {
                checkNotAgentType(parameter.getType(), "parameter", adviceClass, adviceMethod);
                AnnotationDescription.Loadable<Advice.Return> returnAnnotation = parameter.getDeclaredAnnotations().ofType(Advice.Return.class);
                if (returnAnnotation != null && !returnAnnotation.load().readOnly()) {
                    throw new IllegalStateException("Advice parameter must not use '@Advice.Return(readOnly=false)', use @Advice.AssignReturned.ToReturned instead");
                }
            }
        } catch (Exception e) {
            // Because types are lazily resolved, unexpected things are expected
            throw new IllegalStateException(String.format("unable to validate advice defined in %s#%s", adviceClass, adviceMethod), e);
        }
    }

    private static void checkNotAgentType(TypeDescription.Generic type, String description, String adviceClass, String adviceMethod) {
        // We have to use 'raw' type as framework classes are not accessible to the boostrap classloader, and
        // trying to resolve them will create exceptions.
        String name = type.asRawType().getTypeName();
        if (name.startsWith("co.elastic.apm")) {
            throw new IllegalStateException(String.format("Advice %s in %s#%s must not be an agent type: %s", description, adviceClass, adviceMethod, name));
        }
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
        GlobalTracer.get().stop();
        GlobalTracer.setNoop();
        Exception exception = null;
        if (resettableClassFileTransformer != null) {
            try {
                resettableClassFileTransformer.reset(instrumentation, RedefinitionStrategy.RETRANSFORMATION);
            } catch (Exception e) {
                exception = e;
            }
            resettableClassFileTransformer = null;
        }
        dynamicallyInstrumentedClasses.clear();
        for (ResettableClassFileTransformer transformer : dynamicClassFileTransformers) {
            try {
                transformer.reset(instrumentation, RedefinitionStrategy.RETRANSFORMATION);
            } catch (Exception e) {
                if (exception != null) {
                    exception.addSuppressed(e);
                } else {
                    exception = e;
                }
            }
        }
        dynamicClassFileTransformers.clear();
        instrumentation = null;
        IndyPluginClassLoaderFactory.clear();
        adviceClassName2instrumentationClassLoader.clear();
        pluginPackages2pluginClassLoaderRootPackages.clear();
    }

    private static AgentBuilder getAgentBuilder(final ByteBuddy byteBuddy, final CoreConfiguration coreConfiguration, final Logger logger,
                                                final AgentBuilder.DescriptionStrategy descriptionStrategy, final boolean premain,
                                                final boolean useTypePoolCache) {
        AgentBuilder.LocationStrategy locationStrategy = AgentBuilder.LocationStrategy.ForClassLoader.WEAK;
        if (agentJarFile != null) {
            try {
                locationStrategy = new AgentBuilder.LocationStrategy.Compound(
                    // it's important to first try loading from the agent jar and not the class loader of the instrumented class
                    // the latter may not have access to the agent resources:
                    // when adding the agent to the bootstrap CL (appendToBootstrapClassLoaderSearch)
                    // the bootstrap CL can load its classes but not its resources
                    // the application class loader may cache the fact that a resource like AbstractSpan.class can't be resolved
                    // and also refuse to load the class
                    new AgentBuilder.LocationStrategy.Simple(ClassFileLocator.ForJarFile.of(agentJarFile)),
                    AgentBuilder.LocationStrategy.ForClassLoader.WEAK,
                    new AgentBuilder.LocationStrategy.Simple(new RootPackageCustomLocator("java.", ClassFileLocator.ForClassLoader.ofBootLoader()))
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
            .with(new RedefinitionStrategy.Listener.Adapter() {
                @Override
                public Iterable<? extends List<Class<?>>> onError(int index, List<Class<?>> batch, Throwable throwable, List<Class<?>> types) {
                    logger.warn("Error while redefining classes {}", throwable.getMessage());
                    logger.debug(throwable.getMessage(), throwable);
                    return super.onError(index, batch, throwable, types);
                }
            })
            .with(descriptionStrategy)
            .with(locationStrategy)
            .with(new ErrorLoggingListener())
            // ReaderMode.FAST as we don't need to read method parameter names
            .with(useTypePoolCache
                ? new LruTypePoolCache(TypePool.Default.ReaderMode.FAST).scheduleEntryEviction()
                : AgentBuilder.PoolStrategy.Default.FAST)
            .ignore(any(), isReflectionClassLoader())
            .or(any(), classLoaderWithName("org.codehaus.groovy.runtime.callsite.CallSiteClassLoader"))
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
            .or(nameStartsWith("com.contrastsecurity."))
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

    @Nullable
    public static File getAgentJarFile() {
        return agentJarFile;
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
                ElasticApmTracer tracer = GlobalTracer.requireTracerImpl();
                if (instrumentation == null) {
                    throw new IllegalStateException("Agent is not initialized");
                }

                appliedInstrumentations = dynamicallyInstrumentedClasses.get(classToInstrument);
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
                    AgentBuilder agentBuilder = getAgentBuilder(
                        byteBuddy, config, logger, AgentBuilder.DescriptionStrategy.Default.POOL_ONLY, false, false
                    );
                    for (Class<? extends ElasticApmInstrumentation> instrumentationClass : instrumentationClasses) {
                        ElasticApmInstrumentation apmInstrumentation = instantiate(instrumentationClass);
                        mapInstrumentationCL2adviceClassName(
                            apmInstrumentation.getAdviceClassName(),
                            instrumentationClass.getClassLoader());
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

    public static void mapInstrumentationCL2adviceClassName(String adviceClassName, ClassLoader instrumentationClassLoader) {
        adviceClassName2instrumentationClassLoader.put(adviceClassName, instrumentationClassLoader);
    }

    private static Set<Collection<Class<? extends ElasticApmInstrumentation>>> getOrCreate(Class<?> classToInstrument) {
        Set<Collection<Class<? extends ElasticApmInstrumentation>>> instrumentedClasses = dynamicallyInstrumentedClasses.get(classToInstrument);
        if (instrumentedClasses == null) {
            instrumentedClasses = new HashSet<Collection<Class<? extends ElasticApmInstrumentation>>>();
            Set<Collection<Class<? extends ElasticApmInstrumentation>>> racy = dynamicallyInstrumentedClasses.putIfAbsent(classToInstrument, instrumentedClasses);
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
                    instance = constructor.newInstance(GlobalTracer.requireTracerImpl());
                } else {
                    instance = constructor.newInstance();
                }
            } catch (ReflectiveOperationException e) {
                // silently ignored
            }
        }

        return instance;
    }

    public static ClassLoader getAgentClassLoader() {
        ClassLoader agentClassLoader = ElasticApmAgent.class.getClassLoader();
        if (agentClassLoader == null) {
            throw new IllegalStateException("Agent is loaded from bootstrap class loader as opposed to the dedicated agent class loader");
        }
        return agentClassLoader;
    }

    /**
     * Returns the class loader that loaded the instrumentation class corresponding the given advice class.
     * We expect to be able to find the advice class file through this class loader.
     * @param adviceClass name of the advice class
     * @return class loader that can be used for the advice class file lookup
     */
    public static ClassLoader getInstrumentationClassLoader(String adviceClass) {
        ClassLoader classLoader = adviceClassName2instrumentationClassLoader.get(adviceClass);
        if (classLoader == null) {
            throw new IllegalStateException("There's no mapping for key " + adviceClass);
        }
        return classLoader;
    }

    public static Collection<String> getPluginClassLoaderRootPackages(String pluginPackage) {
        Collection<String> pluginPackages = pluginPackages2pluginClassLoaderRootPackages.get(pluginPackage);
        if (pluginPackages != null) {
            return pluginPackages;
        }
        return Collections.singleton(pluginPackage);
    }
}
