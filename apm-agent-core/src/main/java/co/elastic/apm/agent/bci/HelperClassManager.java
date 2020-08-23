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

import co.elastic.apm.agent.bci.classloading.IndyPluginClassLoader;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.dynamic.loading.PackageDefinitionStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * This class helps to overcome the fact that the agent classes can't access the classes they want to instrument.
 * <p>
 * For example,
 * when the agent wants to access the classes of the Servlet API it can't,
 * as the agent is loaded by the bootstrap classloader and the Servlet API is loaded by a child classloader.
 * </p>
 * <p>
 * This class creates a helper classloader whose parent is the classloader of the classes the agent needs to have access to.
 * The classes loaded by this helper classloader are called helper classes.
 * These helper classes can access the desired classes,
 * as they are defined by a parent classloader.
 * </p>
 * <p>
 * As the agent, which is loaded by the bootstrap classloader can't directly access the helper classes,
 * the agent defines a helper interface,
 * which is implemented by the helper class.
 * Therefore, the agent does not need to have access to the implementation type,
 * but can just refer to the interface.
 * </p>
 * <p>
 * Because the helper classes are also available from the bootstrap classloader,
 * the classloaders created in this class have child-first semantics.
 * This makes sure that the loaded classes indeed have access to the types the helper classes refer to.
 * </p>
 * <p>
 * Note: trying to load the helper class implementations from the bootstrap classloader leads to {@link NoClassDefFoundError}s.
 * </p>
 *
 * @deprecated Use {@link TracerAwareInstrumentation#indyPlugin() indy plugins} instead.
 * As the whole package the indy plugin is defined it is loaded from {@link IndyPluginClassLoader},
 * a separate helper class manager is not needed anymore.
 * @param <T> the type of the helper interface
 */
@Deprecated
@VisibleForAdvice
public abstract class HelperClassManager<T> {
    private static final Logger logger = LoggerFactory.getLogger(HelperClassManager.class);

    protected final ElasticApmTracer tracer;
    protected final String implementation;
    protected final String[] additionalHelpers;

    protected HelperClassManager(ElasticApmTracer tracer, String implementation, String[] additionalHelpers) {
        this.tracer = tracer;
        this.implementation = implementation;
        this.additionalHelpers = additionalHelpers;
    }

    @Nullable
    public T getForClassLoaderOfClass(Class<?> classOfTargetClassLoader) {
        T ret = null;
        try {
            ret = doGetForClassLoaderOfClass(classOfTargetClassLoader);
        } catch (Throwable throwable) {
            logger.error("Failed to load Helper class " + implementation + " for class " + classOfTargetClassLoader + ", loaded by " +
                "ClassLoader " + classOfTargetClassLoader.getClassLoader(), throwable);
        }
        return ret;
    }

    /**
     * Implementations of this method are expected to load helper classes lazily, meaning when invoked for the first time.
     * By delaying the helper class loading only to when actually required (normally from the injected code) we ensure proper classpath
     * is already in place.
     *
     * @param classOfTargetClassLoader a class loaded by the class loader we want to load the helper class from
     * @return helper instance
     * @throws Exception most likely related to first time loading error
     */
    @Nullable
    protected abstract T doGetForClassLoaderOfClass(Class<?> classOfTargetClassLoader) throws Exception;

    /**
     * This helper class manager assumes that there is only one {@link ClassLoader} which loads the classes,
     * the helper class references.
     * <p>
     * This is true, for example, for the JDBC and the Servlet API.
     * There is only one canonical class for these types.
     * </p>
     * <p>
     * However, this does not work if the helper class references types,
     * which can be loaded by multiple class loaders.
     * An example would be framework or library types,
     * which are part of an application deployed to a servlet container.
     * There could be multiple applications,
     * using the same type,
     * loaded by different application class loaders,
     * which makes them distinct runtime types.
     * </p>
     * <p>
     * Note that the {@link #helperImplementation}'s classloader references the target classloader as it's parent.
     * This class does not weakly reference the {@link #helperImplementation}.
     * It is assumed that because there is only one classloader involved,
     * there can't be a leakage of this classloader by keeping around the {@link #helperImplementation}.
     * The situation would be differently when referring to types loaded by a web application classloader,
     * which has to be collectible when the web application is un-deployed.
     * </p>
     *
     * @deprecated Use {@link TracerAwareInstrumentation#indyPlugin() indy plugins} instead.
     * As the whole package the indy plugin is defined it is loaded from {@link IndyPluginClassLoader},
     * a separate helper class manager is not needed anymore.
     * @param <T> the type of the helper class interface
     */
    @Deprecated
    public static class ForSingleClassLoader<T> extends HelperClassManager<T> {

        @Nullable
        private volatile T helperImplementation;

        // No need to make volatile - at worst we will fail more than once, but avoid volatile cache invalidations for all the rest
        private boolean failed;

        private ForSingleClassLoader(ElasticApmTracer tracer, String implementation, String... additionalHelpers) {
            super(tracer, implementation, additionalHelpers);
        }

        /**
         * Not loading and instantiating helper class yet, just prepares the manager
         */
        public static <T> ForSingleClassLoader<T> of(ElasticApmTracer tracer, String implementation, String... additionalHelpers) {
            return new ForSingleClassLoader<>(tracer, implementation, additionalHelpers);
        }

        @Override
        @Nullable
        public T doGetForClassLoaderOfClass(Class<?> classOfTargetClassLoader) {
            if (failed) {
                return null;
            }
            // local variable helps to avoid multiple volatile reads
            T localHelper = this.helperImplementation;
            if (localHelper == null) {
                synchronized (this) {
                    localHelper = this.helperImplementation;
                    if (localHelper == null) {
                        try {
                            localHelper = createHelper(classOfTargetClassLoader.getClassLoader(), tracer, implementation, additionalHelpers);
                        } catch (Throwable throwable) {
                            failed = true;
                            throw throwable;
                        }
                        this.helperImplementation = localHelper;
                    }
                }
            }
            return localHelper;
        }
    }

    /**
     * This helper class manager is for usage of confined context class loaders, like web app class loaders.
     * It injects a minimal class to the target class loader and keeps loaded helper instances in a static field of that class.
     * This way, helper instances have a hard reference as long as the class loader is alive, when they become GC-eligible, that without
     * holding hard references to the class loader or any of the user/library classes.
     * This manager supports multiple helpers for each class loader through a list of hard references held statically by the injected class.
     * Failures in helper class loading are cached so that no loading attempts will be made over and over.
     * In order to optimize performance, stale entries are removed only when new helpers are being loaded (normally at the beginning of
     * startup time and when new applications are deployed). These maps shouldn't grow big as they have an entry per class loader.
     *
     * @deprecated Use {@link TracerAwareInstrumentation#indyPlugin() indy plugins} instead.
     * As the whole package the indy plugin is defined it is loaded from {@link IndyPluginClassLoader},
     * a separate helper class manager is not needed anymore.
     * @param <T>
     */
    @Deprecated
    public static class ForAnyClassLoader<T> extends HelperClassManager<T> {

        // doesn't need to be concurrent - invoked only from a synchronized context
        static final Map<ClassLoader, WeakReference<List<Object>>> clId2helperImplListMap = new WeakHashMap<>();

        final WeakConcurrentMap<ClassLoader, WeakReference<T>> clId2helperMap;

        private ForAnyClassLoader(ElasticApmTracer tracer, String implementation, String... additionalHelpers) {
            super(tracer, implementation, additionalHelpers);
            // deliberately doesn't use WeakMapSupplier as this class manages the cleanup manually
            clId2helperMap = new WeakConcurrentMap<>(false);
        }

        /**
         * Not loading and instantiating helper class yet, just prepares the manager
         */
        public static <T> ForAnyClassLoader<T> of(ElasticApmTracer tracer, String implementation, String... additionalHelpers) {
            return new ForAnyClassLoader<>(tracer, implementation, additionalHelpers);
        }

        @Override
        @Nullable
        public T doGetForClassLoaderOfClass(Class<?> classOfTargetClassLoader) throws Exception {
            final ClassLoader targetCl = classOfTargetClassLoader.getClassLoader();
            WeakReference<T> helperRef = clId2helperMap.get(targetCl);
            if (helperRef == null) {
                return loadAndReferenceHelper(classOfTargetClassLoader);
            } else {
                return helperRef.get();
            }
        }

        @SuppressWarnings("Java8CollectionRemoveIf")
        @Nullable
        private synchronized T loadAndReferenceHelper(Class<?> classOfTargetClassLoader) throws Exception {
            T helper;
            ClassLoader targetCl = classOfTargetClassLoader.getClassLoader();
            WeakReference<T> helperRef = clId2helperMap.get(targetCl);
            if (helperRef == null) {
                try {
                    // clean stale entries first
                    clId2helperMap.expungeStaleEntries();

                    helper = createHelper(targetCl, tracer, implementation, additionalHelpers);

                    List<Object> helperImplList = null;
                    WeakReference<List<Object>> helperImplListRef = clId2helperImplListMap.get(targetCl);
                    if (helperImplListRef != null) {
                        helperImplList = helperImplListRef.get();
                    }
                    if (helperImplList == null) {
                        // Currently using the target class's ProtectionDomain, still need to validate that this is a valid approach
                        Class<?> helperHolderClass = injectClass(targetCl, classOfTargetClassLoader.getProtectionDomain(), "co.elastic.apm.agent.bci.HelperHolder"
                            , true);
                        //noinspection unchecked
                        helperImplList = (List<Object>) helperHolderClass.getField("helperInstanceList").get(null);
                        clId2helperImplListMap.put(targetCl, new WeakReference<>(helperImplList));
                    }

                    helperImplList.add(helper);
                    clId2helperMap.put(targetCl, new WeakReference<>(helper));
                } catch (Throwable throwable) {
                    clId2helperMap.putIfAbsent(targetCl, new WeakReference<>((T) null));
                    throw throwable;
                }
            } else {
                helper = helperRef.get();
            }
            return helper;
        }
    }

    static Class injectClass(@Nullable ClassLoader targetClassLoader, @Nullable ProtectionDomain pd, String className, boolean isBootstrapClass) throws IOException, ClassNotFoundException {
        if (targetClassLoader == null) {
            if (isBootstrapClass) {
                return Class.forName(className, false, null);
            } else {
                throw new UnsupportedOperationException("Cannot load non-bootstrap class from bootstrap class loader");
            }
        }

        ClassInjector classInjector;
        if (targetClassLoader == ClassLoader.getSystemClassLoader()) {
            classInjector = ClassInjector.UsingReflection.ofSystemClassLoader();
        } else {
            classInjector = new ClassInjector.UsingReflection(targetClassLoader, pd, PackageDefinitionStrategy.NoOp.INSTANCE,
                true);
        }
        final byte[] classBytes = ClassFileLocator.ForClassLoader.of(ElasticApmAgent.getAgentClassLoader()).locate(className).resolve();
        final TypeDescription typeDesc =
            new TypeDescription.Latent(className, 0, null, Collections.<TypeDescription.Generic>emptyList());
        Map<TypeDescription, byte[]> typeMap = new HashMap<>();
        typeMap.put(typeDesc, classBytes);
        return classInjector.inject(typeMap).values().iterator().next();
    }

    private static <T> T createHelper(@Nullable ClassLoader targetClassLoader, ElasticApmTracer tracer, String implementation,
                                      String... additionalHelpers) {
        try {
            final Map<String, byte[]> typeDefinitions = getTypeDefinitions(asList(implementation, additionalHelpers), ElasticApmAgent.getAgentClassLoader());
            Class<? extends T> helperClass;
            try {
                helperClass = loadHelperClass(targetClassLoader, implementation, typeDefinitions);
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                // in the unit tests, the agent is not added to the bootstrap class loader
                helperClass = loadHelperClass(ClassLoader.getSystemClassLoader(), implementation, typeDefinitions);
            }
            // the helper class may have a no-arg or a ElasticApmTracer constructor
            // this is preferable to a init method,
            // as it allows the tracer instance variable to be non-null
            try {
                return helperClass.getDeclaredConstructor(ElasticApmTracer.class).newInstance(tracer);
            } catch (NoSuchMethodException e) {
                return helperClass.getDeclaredConstructor().newInstance();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static List<String> asList(String implementation, String[] additionalHelpers) {
        final ArrayList<String> list = new ArrayList<>(additionalHelpers.length + 1);
        list.add(implementation);
        list.addAll(Arrays.asList(additionalHelpers));
        return list;
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> loadHelperClass(@Nullable ClassLoader targetClassLoader, String implementation,
                                                Map<String, byte[]> typeDefinitions) throws ClassNotFoundException {
        final ClassLoader helperCL = new ByteArrayClassLoader.ChildFirst(targetClassLoader, true, typeDefinitions);
        return (Class<T>) helperCL.loadClass(implementation);
    }

    private static Map<String, byte[]> getTypeDefinitions(List<String> helperClassNames, ClassLoader classLoader) throws IOException {
        Map<String, byte[]> typeDefinitions = new HashMap<>();
        for (final String helperName : helperClassNames) {
            final byte[] classBytes = ClassFileLocator.ForClassLoader.of(classLoader).locate(helperName).resolve();
            typeDefinitions.put(helperName, classBytes);
        }
        return typeDefinitions;
    }

}
