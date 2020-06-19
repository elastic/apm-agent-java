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

import co.elastic.apm.agent.util.PackageScanner;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.loading.ClassInjector;
import org.slf4j.LoggerFactory;
import org.stagemonitor.util.IOUtils;

import javax.annotation.Nullable;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * When {@link ElasticApmInstrumentation#indyPlugin()} returns {@code true},
 * we instruct Byte Buddy (via {@link Advice.WithCustomMapping#bootstrap(java.lang.reflect.Method)})
 * to dispatch {@linkplain Advice.OnMethodEnter#inline() non-inlined advices} via an invokedynamic (indy) instruction.
 * The target method is linked to a dynamically created plugin class loader that is specific to an instrumentation plugin
 * and the class loader of the instrumented method.
 * <p>
 * The first invocation of an {@code INVOKEDYNAMIC} causes the JVM to dynamically link a {@link CallSite}.
 * In this case, it will use the {@link #bootstrap} method to do that.
 * This will also create the plugin class loader.
 * </p>
 * <pre>
 *   Bootstrap CL ←──────────────────────────── Agent CL
 *       ↑ └java.lang.IndyBootstrapDispatcher ─ ↑ ─→ └ {@link IndyBootstrap#bootstrap}
 *     Ext/Platform CL               ↑          │       ╷
 *       ↑                           ╷          │       ↓
 *     System CL                     ╷          │ {@link HelperClassManager.ForIndyPlugin#getOrCreatePluginClassLoader}
 *       ↑                           ╷          │       ╷
 *     Common               linking of CallSite │       ╷
 *     ↑    ↑             (on first invocation) │    creates
 * WebApp1  WebApp2                  ╷          │       ╷
 *          ↑ - InstrumentedClass    ╷          │       ╷
 *          │                ╷       ╷          │       ╷
 *          │                INVOKEDYNAMIC      │       ↓
 *          └────────────────┼──────────────────Plugin CL
 *                           └╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶→ ├ AdviceClass
 *                                              └ AdviceHelper
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
 *         See also {@link AgentMain#isJavaVersionSupported}
 *     </li>
 *     <li>
 *         There are some things to watch out for when writing plugins,
 *         as explained in {@link ElasticApmInstrumentation#indyPlugin()}
 *     </li>
 * </ul>
 * @see ElasticApmInstrumentation#indyPlugin()
 */
public class IndyBootstrap {

    /**
     * Starts with {@code java.lang} so that OSGi class loaders don't restrict access to it
     */
    private static final String INDY_BOOTSTRAP_CLASS_NAME = "java.lang.IndyBootstrapDispatcher";
    /**
     * The class file of {@code java.lang.IndyBootstrapDispatcher}.
     * Ends with {@code clazz} because if it ended with {@code clazz}, it would be loaded like a regular class.
     */
    private static final String INDY_BOOTSTRAP_RESOURCE = "bootstrap/IndyBootstrapDispatcher.clazz";
    /**
     * Caches the names of classes that are defined within a package and it's subpackages
     */
    private static final ConcurrentMap<String, List<String>> classesByPackage = new ConcurrentHashMap<>();
    @Nullable
    static Method indyBootstrapMethod;

    public static Method getIndyBootstrapMethod() {
        if (indyBootstrapMethod != null) {
            return indyBootstrapMethod;
        }
        try {
            Class<?> indyBootstrapClass = initIndyBootstrap();
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
    private static Class<?> initIndyBootstrap() throws Exception {
        try {
            return Class.forName(INDY_BOOTSTRAP_CLASS_NAME, false, null);
        } catch (ClassNotFoundException e) {
            byte[] bootstrapClass = IOUtils.readToBytes(ClassLoader.getSystemClassLoader().getResourceAsStream(INDY_BOOTSTRAP_RESOURCE));
            ClassInjector.UsingUnsafe.ofBootLoader().injectRaw(Collections.singletonMap(INDY_BOOTSTRAP_CLASS_NAME, bootstrapClass));
        }
        return Class.forName(INDY_BOOTSTRAP_CLASS_NAME, false, null);
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
     */
    @Nullable
    public static ConstantCallSite bootstrap(MethodHandles.Lookup lookup,
                                             String adviceMethodName,
                                             MethodType adviceMethodType,
                                             Object... args) throws Exception {
        try {
            String adviceClassName = (String) args[0];
            int enter = (Integer) args[1];
            Class<?> instrumentedType = (Class<?>) args[2];
            String instrumentedMethodName = (String) args[3];
            MethodHandle instrumentedMethod = args.length >= 5 ? (MethodHandle) args[4] : null;
            Class<?> adviceClass = Class.forName(adviceClassName);
            String packageName = adviceClass.getPackage().getName();
            List<String> pluginClasses = classesByPackage.get(packageName);
            if (pluginClasses == null) {
                classesByPackage.putIfAbsent(packageName, PackageScanner.getClassNames(packageName));
                pluginClasses = classesByPackage.get(packageName);
            }
            ClassLoader pluginClassLoader = HelperClassManager.ForIndyPlugin.getOrCreatePluginClassLoader(
                lookup.lookupClass().getClassLoader(),
                pluginClasses,
                isAnnotatedWith(named(GlobalState.class.getName()))
                    // no plugin CL necessary as all types are available form bootstrap CL
                    // also, this plugin is used as a dependency in other plugins
                    .or(nameStartsWith("co.elastic.apm.agent.concurrent")));
            Class<?> adviceInPluginCL = pluginClassLoader.loadClass(adviceClassName);
            MethodHandle methodHandle = MethodHandles.lookup().findStatic(adviceInPluginCL, adviceMethodName, adviceMethodType);
            return new ConstantCallSite(methodHandle);
        } catch (Exception e) {
            // must not be a static field as it would initialize logging before it's ready
            LoggerFactory.getLogger(IndyBootstrap.class).error(e.getMessage(), e);
            return null;
        }
    }
}
