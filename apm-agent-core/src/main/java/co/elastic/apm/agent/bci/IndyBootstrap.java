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

import co.elastic.apm.agent.bci.classloading.ExternalPluginClassLoader;
import co.elastic.apm.agent.bci.classloading.IndyPluginClassLoader;
import co.elastic.apm.agent.bci.classloading.LookupExposer;
import co.elastic.apm.agent.common.JvmRuntimeInfo;
import co.elastic.apm.agent.sdk.state.GlobalState;
import co.elastic.apm.agent.util.PackageScanner;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassInjector;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.util.IOUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * We instruct Byte Buddy (via {@link Advice.WithCustomMapping#bootstrap(java.lang.reflect.Method)})
 * to dispatch {@linkplain Advice.OnMethodEnter#inline() non-inlined advices} via an invokedynamic (indy) instruction.
 * The target method is linked to a dynamically created plugin class loader that is specific to an instrumentation plugin
 * and the class loader of the instrumented method.
 * <p>
 * The first invocation of an {@code INVOKEDYNAMIC} causes the JVM to dynamically link a {@link CallSite}.
 * In this case, it will use the {@link #bootstrap} method to do that.
 * This will also create the plugin class loader.
 * </p>
 * <pre>
 *                      {@code co.elastic.apm.agent.premain.ShadedClassLoader}
 *   Bootstrap CL ←─────Cached Lookup Key────── Agent CL {@code co.elastic.apm.agent.premain.ShadedClassLoader}
 *       ↑ └java.lang.IndyBootstrapDispatcher ─ ↑ ───→ └ {@link IndyBootstrap#bootstrap}
 *     Ext/Platform CL               ↑          │                        ╷
 *       ↑                           ╷          │                        ↓
 *     System CL                     ╷          │        {@link IndyPluginClassLoaderFactory#getOrCreatePluginClassLoader}
 *       ↑                           ╷          │                        ╷
 *     Common               linking of CallSite {@link ExternalPluginClassLoader}
 *     ↑    ↑             (on first invocation) ↑ ├ AdviceClass          ╷
 * WebApp1  WebApp2                  ╷          │ ├ AdviceHelper      creates
 *          ↑ - InstrumentedClass    ╷          │ ├ GlobalState          ╷
 *          │                ╷       ╷          │ └ LookupExposer        ╷
 *          │                INVOKEDYNAMIC      │                        ╷
 *          └────────────────┼──────────────────{@link co.elastic.apm.agent.bci.classloading.DiscriminatingMultiParentClassLoader}
 *                           │                  ↑                        ↓
 *                           │                  {@link IndyPluginClassLoader}
 *                           └╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶→ ├ AdviceClass
 *                                              ├ AdviceHelper
 *                                              └ LookupExposer
 * Legend:
 *  ╶╶→ method calls
 *  ──→ class loader parent/child relationships
 * </pre>
 * <p>
 * Advantages:
 * </p>
 * <ul>
 *     <li>
 *         <b>OSGi class loaders</b>
 *         can't interfere with class loading.
 *         Instrumented classes only need to see java.lang.IndyBootstrapDispatcher.
 *         The actual advice class is invoked via {@code INVOKEDYNAMIC} instruction,
 *         basically a dynamically looked up {@link MethodHandle}.
 *     </li>
 *     <li>
 *         <b>Performance:</b>
 *         As the target {@link MethodHandle} is not changed after the initial lookup
 *         (we return a {@link ConstantCallSite} from {@link IndyBootstrap#bootstrap}),
 *         the JIT can easily inline the advice into the instrumented method.
 *     </li>
 *     <li>
 *         <b>Runtime attachment</b>
 *         This approach circumvents any OSGi issues even when attaching the agent at runtime.
 *         Setting the {@code org.osgi.framework.bootdelegation} property after the OSGi class loaders have already initialized has no effect.
 *         This is also a more holistic solution that also works for non-OSGi filtering class loaders.
 *     </li>
 *     <li>
 *         <b>Runtime detachment:</b>
 *         After un-instrumenting classes ({@link ElasticApmAgent#reset()}) and stopping all agent threads there should be no references
 *         to any Plugin CL or the Agent CL.
 *         This means the GC should be able to completely remove all loaded classes and class loaders of the agent,
 *         except for {@code java.lang.IndyBootstrapDispatcher}.
 *         This can be useful to completely remove/detach the agent at runtime or to update the agent version without restarting the application.
 *     </li>
 *     <li>
 *         <b>Class visibility:</b>
 *         The plugins can access both the specific types of the library they access and the agent classes as the Plugin CL
 *         has both the Agent CL and the CL of the instrumented class as its parent.
 *         Again, OSGi class loaders can't interfere here as both the Plugin CL and the Agent CL are under full control of the agent.
 *     </li>
 *     <li>
 *         <b>Debugging advices:</b>
 *         Advice classes can easily be debugged as they are not inlined in the instrumented methods.
 *     </li>
 *     <li>
 *         <b>Unit testing:</b>
 *         Classes loaded from the bootstrap class loader can be instrumented in unit tests.
 *     </li>
 *     <li>
 *         <b>No shading:</b>
 *         When loading the agent in an isolated class loader, we don't have to shade every dependency anymore.
 *         This makes packaging, source jar generation, and instrumenting shaded dependencies such as log4j2 much easier.
 *     </li>
 * </ul>
 * <p>
 * Challenges:
 * </p>
 * <ul>
 *     <li>
 *         As we're working with {@code INVOKEDYNAMIC} instructions that have only been introduced in Java 7,
 *         we have to patch classes we instrument that are compiled with Java 6 bytecode level (50) to Java 7 bytecode level (51).
 *         This involves re-computing stack frames and removing JSR instructions.
 *         See also {@link co.elastic.apm.agent.bci.ElasticApmAgent#applyAdvice}.
 *         This makes instrumentation a bit slower but it seems to work reliably,
 *         even when re-transforming classes (important for runtime attachment).
 *     </li>
 *     <li>
 *         The {@code INVOKEDYNAMIC} support of early Java 7 versions is not reliable.
 *         That's why we disable the agent on them.
 *         See also {@code co.elastic.apm.agent.premain.JavaVersionBootstrapCheck}
 *     </li>
 *     <li>
 *         There are some things to watch out for when writing plugins,
 *         as explained in {@link co.elastic.apm.agent.sdk.ElasticApmInstrumentation}
 *     </li>
 * </ul>
 * @see co.elastic.apm.agent.sdk.ElasticApmInstrumentation
 */
@SuppressWarnings("JavadocReference")
public class IndyBootstrap {

    /**
     * Starts with {@code java.lang} so that OSGi class loaders don't restrict access to it.
     * This also allows to load it in {@code java.base} module on Java9+ for Hotspot, Open J9 requires {@code ModuleSetter}
     */
    private static final String INDY_BOOTSTRAP_CLASS_NAME = "java.lang.IndyBootstrapDispatcher";

    /**
     * The class file of {@code IndyBootstrapDispatcher}, loaded from classpath resource, {@code esclazz} extension avoids
     * being loaded as a regular class.
     */
    private static final String INDY_BOOTSTRAP_RESOURCE = "bootstrap/java/lang/IndyBootstrapDispatcher.esclazz";

    /**
     * Needs to be loaded from the bootstrap CL because it uses {@code sun.misc.Unsafe}.
     * In addition, needs to be loaded explicitly by name only when running on Java 9, because compiled with Java 9
     */
    private static final String INDY_BOOTSTRAP_MODULE_SETTER_CLASS_NAME = "co.elastic.apm.agent.modulesetter.ModuleSetter";

    /**
     * The class file of {@code ModuleSetter}, loaded from classpath resource, {@code esclazz} extension avoids being
     * loaded as a regular class.
     */
    private static final String INDY_BOOTSTRAP_MODULE_SETTER_RESOURCE = "bootstrap/co/elastic/apm/agent/modulesetter/ModuleSetter.esclazz";

    /**
     * The name of the class we use as the lookup class during the invokedynamic bootstrap flow. The bytecode of this
     * class is injected into the plugin class loader, then loaded from that class loader and used as the lookup class
     * to link the instrumented call site to the advice method.
     */
    public static final String LOOKUP_EXPOSER_CLASS_NAME = "co.elastic.apm.agent.bci.classloading.LookupExposer";

    /**
     * The root package name prefix that all embedded plugins classes should start with
     */
    private static final String EMBEDDED_PLUGINS_PACKAGE_PREFIX = "co.elastic.apm.agent.";

    /**
     * Caches the names of classes that are defined within a package and it's subpackages
     */
    private static final ConcurrentMap<String, List<String>> classesByPackage = new ConcurrentHashMap<>();

    @Nullable
    static Method indyBootstrapMethod;

    public static Method getIndyBootstrapMethod(final Logger logger) {
        if (indyBootstrapMethod != null) {
            return indyBootstrapMethod;
        }
        try {
            Class<?> indyBootstrapClass = initIndyBootstrap(logger);
            indyBootstrapClass
                .getField("bootstrap")
                .set(null, IndyBootstrap.class.getMethod("bootstrap", MethodHandles.Lookup.class, String.class, MethodType.class, Object[].class));
            return indyBootstrapMethod = indyBootstrapClass.getMethod("bootstrap", MethodHandles.Lookup.class, String.class, MethodType.class, Object[].class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Injects the {@code java.lang.IndyBootstrapDispatcher} class into the bootstrap class loader if it wasn't already.
     */
    private static Class<?> initIndyBootstrap(final Logger logger) throws Exception {
        Class<?> indyBootstrapDispatcherClass = loadClassInBootstrap(INDY_BOOTSTRAP_CLASS_NAME, INDY_BOOTSTRAP_RESOURCE);

        if (JvmRuntimeInfo.ofCurrentVM().getMajorVersion() >= 9 && JvmRuntimeInfo.ofCurrentVM().isJ9VM()) {
            try {
                logger.info("Overriding IndyBootstrapDispatcher class's module to java.base module. This is required in J9 VMs.");
                setJavaBaseModule(indyBootstrapDispatcherClass);
            } catch (Throwable throwable) {
                logger.warn("Failed to setup proper module for the IndyBootstrapDispatcher class, instrumentation may fail", throwable);
            }
        }
        return indyBootstrapDispatcherClass;
    }

    /**
     * Loads a class from classpath resource in bootstrap classloader.
     * <p>
     * Ensuring that classes loaded through this method can ONLY be loaded in the bootstrap CL requires the following:
     * <ul>
     *     <li>The class bytecode resource name should not end with the {@code .class} suffix</li>
     *     <li>The class bytecode resource name should be in a location that reflects its package</li>
     *     <li>For tests in IDE, the class name used here should be distinct from its original class name to ensure
     *     that only the relocated resource is being used</li>
     * </ul>
     *
     * @param className    class name
     * @param resourceName class resource name
     * @return class loaded in bootstrap classloader
     * @throws IOException            if unable to open provided resource
     * @throws ClassNotFoundException if unable to load class in bootstrap CL
     */
    private static Class<?> loadClassInBootstrap(String className, String resourceName) throws IOException, ClassNotFoundException {
        Class<?> bootstrapClass;
        try {
            // Will return non-null value only if the class has already been loaded.
            // Ensuring that a class can ONLY be loaded through this method and not from regular classloading relies
            // on applying the listed instructions in method documentation
            bootstrapClass = Class.forName(className, false, null);
        } catch (ClassNotFoundException e) {
            byte[] classBytes = IOUtils.readToBytes(ElasticApmAgent.getAgentClassLoader().getResourceAsStream(resourceName));
            if (classBytes == null || classBytes.length == 0) {
                throw new IllegalStateException("Could not locate " + resourceName);
            }
            ClassInjector.UsingUnsafe.ofBootLoader().injectRaw(Collections.singletonMap(className, classBytes));
            bootstrapClass = Class.forName(className, false, null);
        }
        return bootstrapClass;
    }


    /**
     * A package-private method for unit-testing of the module overriding functionality
     *
     * @param targetClass class for which module should be overridden with the {@code java.base} module
     * @throws Throwable in case of any failure related to module overriding
     */
    static void setJavaBaseModule(Class<?> targetClass) throws Throwable {
        // In order to override the original unnamed module assigned to the IndyBootstrapDispatcher, we rely on the
        // Unsafe API, which requires the caller to be loaded by the Bootstrap CL

        Class<?> moduleSetterClass = loadClassInBootstrap(INDY_BOOTSTRAP_MODULE_SETTER_CLASS_NAME, INDY_BOOTSTRAP_MODULE_SETTER_RESOURCE);
        MethodHandles.lookup()
            .findStatic(moduleSetterClass, "setJavaBaseModule", MethodType.methodType(void.class, Class.class))
            .invoke(targetClass);
    }

    /**
     * Is called by {@code java.lang.IndyBootstrapDispatcher#bootstrap} via reflection.
     * <p>
     * This is to make it impossible for OSGi or other filtering class loaders to restrict access to classes in the bootstrap class loader.
     * Normally, additional classes that have been injected have to be explicitly allowed via the {@code org.osgi.framework.bootdelegation}
     * system property.
     * But because we inject our class directly in the {@code java.lang} package which has to be on the allow list of filtering class loaders
     * we can be sure that any other class can always call that class.
     * </p>
     * <p>
     * This method gets called the first time an instrumented method is called.
     * On instrumentation an {@code invokedynamic} instruction is inserted that delays the linking to the target method until runtime.
     * The linking to the target method is done in this method.
     * </p>
     * <p>
     * If not already created, the lookup of the target method creates a dedicated class loader for the classes in the plugin that has
     * instrumented the {@code instrumentedType}.
     * Via package scanning it finds all the classes that are in the package of the advice that was responsible for the instrumentation.
     * It then links to the advice loaded from the plugin class loader.
     * The advice can access both agent types and the types of the instrumented library.
     * </p>
     * <p>
     * Exceptions and {@code null} return values are handled by {@code java.lang.IndyBootstrapDispatcher#bootstrap}.
     * </p>
     * This is how a bootstrap method looks like in the class file:
     * <pre>
     * BootstrapMethods:
     *   1: #1060 REF_invokeStatic java/lang/IndyBootstrapDispatcher.bootstrap:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;
     *     Method arguments:
     *       #1049 co.elastic.apm.agent.bci.InstrumentationTest$CommonsLangInstrumentation
     *       #1050 0
     *       #12 org/apache/commons/lang3/StringUtils
     *       #1072 isNotEmpty
     *       #1075 REF_invokeStatic org/apache/commons/lang3/StringUtils.isNotEmpty:(Ljava/lang/CharSequence;)Z
     * </pre>
     *
     * And this is how a invokedynamic instruction looks like inside methods,
     * referencing above bootstrap method
     * <pre>
     *     invokedynamic #1076,  0           // InvokeDynamic #1:onEnter:()V
     * </pre>
     *
     * @param lookup           A {@code java.lang.invoke.MethodHandles.Lookup} representing the instrumented method.
     * @param adviceMethodName A {@link String} representing the advice method name.
     * @param adviceMethodType A {@link java.lang.invoke.MethodType} representing the arguments and return type of the advice method.
     * @param args             Additional arguments that are provided by Byte Buddy:
     *                         <ul>
     *                           <li>A {@link String} of the binary advice class name.</li>
     *                           <li>A {@link int} with value {@code 0} for an enter advice and {code 1} for an exist advice.</li>
     *                           <li>A {@link Class} representing the class implementing the instrumented method.</li>
     *                           <li>A {@link String} with the name of the instrumented method.</li>
     *                           <li>A {@link java.lang.invoke.MethodHandle} representing the instrumented method unless the target is the type's static initializer.</li>
     *                         </ul>
     * @return a {@link ConstantCallSite} that is the target of the invokedynamic
     */
    @Nullable
    public static ConstantCallSite bootstrap(MethodHandles.Lookup lookup,
                                             String adviceMethodName,
                                             MethodType adviceMethodType,
                                             Object... args) {
        try {
            String adviceClassName = (String) args[0];
            int enter = (Integer) args[1];
            Class<?> instrumentedType = (Class<?>) args[2];
            String instrumentedMethodName = (String) args[3];
            MethodHandle instrumentedMethod = args.length >= 5 ? (MethodHandle) args[4] : null;

            ClassLoader instrumentationClassLoader = ElasticApmAgent.getInstrumentationClassLoader(adviceClassName);
            ClassLoader targetClassLoader = lookup.lookupClass().getClassLoader();
            ClassFileLocator classFileLocator;
            List<String> pluginClasses = new ArrayList<>();
            if (instrumentationClassLoader instanceof ExternalPluginClassLoader) {
                List<String> externalPluginClasses = ((ExternalPluginClassLoader) instrumentationClassLoader).getClassNames();
                for (String externalPluginClass : externalPluginClasses) {
                    if (// API classes have no dependencies and don't need to be loaded by an IndyPluginCL
                        !(externalPluginClass.startsWith("co.elastic.apm.api")) &&
                        !(externalPluginClass.startsWith("co.elastic.apm.opentracing"))
                    ) {
                        pluginClasses.add(externalPluginClass);
                    }
                }
                File agentJarFile = ElasticApmAgent.getAgentJarFile();
                if (agentJarFile == null) {
                    throw new IllegalStateException("External plugin cannot be applied - can't find agent jar");
                }
                classFileLocator = new ClassFileLocator.Compound(
                    ClassFileLocator.ForClassLoader.of(instrumentationClassLoader),
                    ClassFileLocator.ForJarFile.of(agentJarFile)
                );
            } else {
                pluginClasses.addAll(getClassNamesFromBundledPlugin(adviceClassName, instrumentationClassLoader));
                classFileLocator = ClassFileLocator.ForClassLoader.of(instrumentationClassLoader);
            }
            pluginClasses.add(LOOKUP_EXPOSER_CLASS_NAME);
            ClassLoader pluginClassLoader = IndyPluginClassLoaderFactory.getOrCreatePluginClassLoader(
                targetClassLoader,
                pluginClasses,
                // we provide the instrumentation class loader as the agent class loader, but it could actually be an
                // ExternalPluginClassLoader, of which parent is the agent class loader, so this works as well.
                instrumentationClassLoader,
                classFileLocator,
                isAnnotatedWith(named(GlobalState.class.getName()))
                    // if config classes would be loaded from the plugin CL,
                    // tracer.getConfig(Config.class) would return null when called from an advice as the classes are not the same
                    .or(nameContains("Config").and(hasSuperType(is(ConfigurationOptionProvider.class)))));
            Class<?> adviceInPluginCL = pluginClassLoader.loadClass(adviceClassName);
            Class<LookupExposer> lookupExposer = (Class<LookupExposer>) pluginClassLoader.loadClass(LOOKUP_EXPOSER_CLASS_NAME);
            // can't use MethodHandle.lookup(), see also https://github.com/elastic/apm-agent-java/issues/1450
            MethodHandles.Lookup indyLookup = (MethodHandles.Lookup) lookupExposer.getMethod("getLookup").invoke(null);
            // When calling findStatic now, the lookup class will be one that is loaded by the plugin class loader
            MethodHandle methodHandle = indyLookup.findStatic(adviceInPluginCL, adviceMethodName, adviceMethodType);
            return new ConstantCallSite(methodHandle);
        } catch (Exception e) {
            // must not be a static field as it would initialize logging before it's ready
            LoggerFactory.getLogger(IndyBootstrap.class).error(e.getMessage(), e);
            return null;
        }
    }

    private static List<String> getClassNamesFromBundledPlugin(String adviceClassName, ClassLoader adviceClassLoader) throws IOException, URISyntaxException {
        if (!adviceClassName.startsWith(EMBEDDED_PLUGINS_PACKAGE_PREFIX)) {
            throw new IllegalArgumentException("invalid advice class location : " + adviceClassName);
        }
        String pluginPackage = adviceClassName.substring(0, adviceClassName.indexOf('.', EMBEDDED_PLUGINS_PACKAGE_PREFIX.length()));
        List<String> pluginClasses = classesByPackage.get(pluginPackage);
        if (pluginClasses == null) {
            pluginClasses = new ArrayList<>();
            Collection<String> pluginClassLoaderRootPackages = ElasticApmAgent.getPluginClassLoaderRootPackages(pluginPackage);
            for (String pkg : pluginClassLoaderRootPackages) {
                pluginClasses.addAll(PackageScanner.getClassNames(pkg, adviceClassLoader));
            }
            classesByPackage.putIfAbsent(pluginPackage, pluginClasses);
            pluginClasses = classesByPackage.get(pluginPackage);
        }
        return pluginClasses;
    }

}
