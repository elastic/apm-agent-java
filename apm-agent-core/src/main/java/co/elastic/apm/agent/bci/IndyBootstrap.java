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
import org.stagemonitor.util.IOUtils;

import javax.annotation.Nullable;
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
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * When {@link ElasticApmInstrumentation#indyDispatch()} returns {@code true},
 * we instruct byte buddy to dispatch {@linkplain Advice.OnMethodEnter#inline()} non-inlined advices} via an invokedynamic (indy) instruction.
 * <p>
 *
 * </p>
 *
 * @see ElasticApmInstrumentation#indyDispatch()
 */
public class IndyBootstrap {
    private static final String INDY_BOOTSTRAP_CLASS_NAME = "java.lang.IndyBootstrap";
    private static final String INDY_BOOTSTRAP_RESOURCE = "bootstrap/IndyBootstrap.clazz";
    private static final ConcurrentMap<String, List<String>> classesByPackage = new ConcurrentHashMap<>();
    @Nullable
    private static Method indyBootstrapMethod;

    public static Method getIndyBootstrapMethod() {
        if (indyBootstrapMethod != null) {
            return indyBootstrapMethod;
        }
        try {
            Class<?> indyBootstrapClass = initIndyBootstrap();
            indyBootstrapClass
                .getField("bootstrap")
                .set(null, IndyBootstrap.class.getMethod("bootstrap", MethodHandles.Lookup.class, String.class, MethodType.class,
                    String.class, Class.class, MethodHandle.class, String.class, int.class));
            return indyBootstrapMethod = indyBootstrapClass.getMethod("bootstrap", MethodHandles.Lookup.class, String.class,
                MethodType.class, String.class, Class.class, MethodHandle.class, String.class, int.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

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
     * Is called by {@code java.lang.IndyBootstrap#bootstrap} via reflection.
     * <p>
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
     * Exceptions and {@code null} return values are handled by caller.
     * </p>
     */
    @Nullable
    public static ConstantCallSite bootstrap(MethodHandles.Lookup lookup,
                                             String adviceMethodName,
                                             MethodType adviceMethodType,
                                             String adviceClassName,
                                             Class<?> instrumentedType,
                                             MethodHandle instrumentedMethod,
                                             String instrumentedMethodName,
                                             int enter) throws Exception {
        Class<?> adviceClass = Class.forName(adviceClassName);
        String packageName = adviceClass.getPackage().getName();
        List<String> helperClasses = classesByPackage.get(packageName);
        if (helperClasses == null) {
            classesByPackage.putIfAbsent(packageName, PackageScanner.getClassNames(packageName));
            helperClasses = classesByPackage.get(packageName);
        }
        ClassLoader helperClassLoader = HelperClassManager.ForDispatcher.inject(lookup.lookupClass().getClassLoader(), instrumentedType.getProtectionDomain(), helperClasses, isAnnotatedWith(named(GlobalState.class.getName())));
        if (helperClassLoader != null) {
            Class<?> adviceInHelperCL = helperClassLoader.loadClass(adviceClassName);
            MethodHandle methodHandle = MethodHandles.lookup().findStatic(adviceInHelperCL, adviceMethodName, adviceMethodType);
            return new ConstantCallSite(methodHandle);
        }
        return null;
    }
}
