/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.bci;

import co.elastic.apm.agent.impl.ElasticApmTracer;
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
import java.lang.reflect.Field;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 * @param <T> the type of the helper interface
 */
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
                "ClassLoader " + classOfTargetClassLoader.getClassLoader() + ". Will not try again for this ClassLoader", throwable);
            markFailedClassLoader(classOfTargetClassLoader.getClassLoader());
        }
        return ret;
    }

    @Nullable
    protected abstract T doGetForClassLoaderOfClass(Class<?> classOfTargetClassLoader) throws Exception;

    protected abstract void markFailedClassLoader(ClassLoader failedClassLoader);

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
     * @param <T> the type of the helper class interface
     */
    public static class ForSingleClassLoader<T> extends HelperClassManager<T> {

        @Nullable
        private volatile T helperImplementation;

        // No need to make volatile - at worst we will fail more than once, but avoid volatile cache invalidations for all the rest
        private boolean failed;

        private ForSingleClassLoader(ElasticApmTracer tracer, String implementation, String... additionalHelpers) {
            super(tracer, implementation, additionalHelpers);
        }

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
                        localHelper = createHelper(classOfTargetClassLoader.getClassLoader(), tracer, implementation, additionalHelpers);
                        this.helperImplementation = localHelper;
                    }
                }
            }
            return localHelper;
        }

        @Override
        protected void markFailedClassLoader(ClassLoader failedClassLoader) {
            failed = true;
        }
    }

    public static class ForAnyClassLoader<T> extends HelperClassManager<T> {

        private final ConcurrentHashMap<Integer, WeakReference<T>> classLoaderId2helperMap;
        private final ConcurrentHashMap<Integer, Boolean> failedClassLoaderSet;

        private ForAnyClassLoader(ElasticApmTracer tracer, String implementation, String... additionalHelpers) {
            super(tracer, implementation, additionalHelpers);
            classLoaderId2helperMap = new ConcurrentHashMap<>();
            failedClassLoaderSet = new ConcurrentHashMap<>();
        }

        public static <T> ForAnyClassLoader<T> of(ElasticApmTracer tracer, String implementation, String... additionalHelpers) {
            return new ForAnyClassLoader<>(tracer, implementation, additionalHelpers);
        }

        @Override
        @Nullable
        public T doGetForClassLoaderOfClass(Class<?> classOfTargetClassLoader) throws IllegalAccessException, NoSuchFieldException, IOException {
            final ClassLoader targetCl = classOfTargetClassLoader.getClassLoader();
            final Integer clIdentityHashCode = System.identityHashCode(targetCl);
            WeakReference<T> helperRef = classLoaderId2helperMap.get(clIdentityHashCode);
            if (helperRef == null) {
                return loadAndReferenceHelper(targetCl, classOfTargetClassLoader.getProtectionDomain(), clIdentityHashCode);
            } else {
                return helperRef.get();
            }
        }

        @Nullable
        private synchronized T loadAndReferenceHelper(ClassLoader targetCl, ProtectionDomain pd, Integer clIdentityHashCode) throws IOException, IllegalAccessException, NoSuchFieldException {
            T helper;
            WeakReference<T> helperRef = classLoaderId2helperMap.get(clIdentityHashCode);
            if (helperRef == null) {
                // clean stale entries first, but only if related class loader is not marked as failed. If failed- we want to leave null ref
                Iterator<Map.Entry<Integer, WeakReference<T>>> clRefIterator = classLoaderId2helperMap.entrySet().iterator();
                while (clRefIterator.hasNext()) {
                    Map.Entry<Integer, WeakReference<T>> refEntry = clRefIterator.next();
                    if (refEntry.getValue().get() == null && !failedClassLoaderSet.containsKey(refEntry.getKey())) {
                        clRefIterator.remove();
                        classLoaderId2helperMap.remove(refEntry.getKey());
                    }
                }

                helper = createHelper(targetCl, tracer, implementation, additionalHelpers);

                Class helperHolder = injectClass(targetCl, pd, "co.elastic.apm.agent.bci.HelperHolder");
                Field helperInstanceField = helperHolder.getField("helperInstance");
                helperInstanceField.set(null, helper);

                classLoaderId2helperMap.put(clIdentityHashCode, new WeakReference<>(helper));
            } else {
                helper = helperRef.get();
            }
            return helper;
        }

        @Override
        protected void markFailedClassLoader(ClassLoader failedClassLoader) {
            Integer classLoaderId = System.identityHashCode(failedClassLoader);
            WeakReference<T> former = classLoaderId2helperMap.putIfAbsent(classLoaderId, new WeakReference<>((T) null));
            if (former == null) {
                failedClassLoaderSet.putIfAbsent(classLoaderId, Boolean.TRUE);
            }
        }
    }

    static Class injectClass(ClassLoader targetClassLoader, ProtectionDomain pd, String className) throws IOException {
        ClassInjector classInjector = new ClassInjector.UsingReflection(targetClassLoader, pd, PackageDefinitionStrategy.NoOp.INSTANCE,
            true);
        final byte[] classBytes = getAgentClassBytes(className);
        final TypeDescription typeDesc =
            new TypeDescription.Latent(className, 0, null, Collections.<TypeDescription.Generic>emptyList());
        Map<TypeDescription, byte[]> typeMap = new HashMap<>();
        typeMap.put(typeDesc, classBytes);
        return classInjector.inject(typeMap).values().iterator().next();
    }

    private static <T> T createHelper(@Nullable ClassLoader targetClassLoader, ElasticApmTracer tracer, String implementation,
                                      String... additionalHelpers) {
        try {
            final Map<String, byte[]> typeDefinitions = getTypeDefinitions(asList(implementation, additionalHelpers));
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

    private static Map<String, byte[]> getTypeDefinitions(List<String> helperClassNames) throws IOException {
        Map<String, byte[]> typeDefinitions = new HashMap<>();
        for (final String helperName : helperClassNames) {
            final byte[] classBytes = getAgentClassBytes(helperName);
            typeDefinitions.put(helperName, classBytes);
        }
        return typeDefinitions;
    }

    private static byte[] getAgentClassBytes(String className) throws IOException {
        final ClassFileLocator locator = ClassFileLocator.ForClassLoader.of(ClassLoader.getSystemClassLoader());
        return locator.locate(className).resolve();
    }
}
