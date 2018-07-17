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

import co.elastic.apm.impl.ElasticApmTracer;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
public interface HelperClassManager<T> {

    T getForClassLoaderOfClass(Class<?> classOfTargetClassLoader);

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
    class ForSingleClassLoader<T> implements HelperClassManager<T> {

        private final ElasticApmTracer tracer;
        private final String implementation;
        private final String[] additionalHelpers;
        @Nullable
        private T helperImplementation;

        private ForSingleClassLoader(ElasticApmTracer tracer, String implementation, String... additionalHelpers) {
            this.tracer = tracer;
            this.implementation = implementation;
            this.additionalHelpers = additionalHelpers;
        }

        public static <T> ForSingleClassLoader<T> of(ElasticApmTracer tracer, String implementation, String... additionalHelpers) {
            return new ForSingleClassLoader<>(tracer, implementation, additionalHelpers);
        }

        @Override
        public T getForClassLoaderOfClass(Class<?> classOfTargetClassLoader) {
            if (helperImplementation == null) {
                synchronized (this) {
                    if (helperImplementation == null) {
                        helperImplementation = createHelper(classOfTargetClassLoader.getClassLoader(), tracer,
                            implementation, additionalHelpers);
                    }
                }
            }
            return helperImplementation;
        }

        private static <T> T createHelper(@Nullable ClassLoader targetClassLoader, ElasticApmTracer tracer, String implementation, String... additionalHelpers) {
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
                final ClassFileLocator locator = ClassFileLocator.ForClassLoader.of(ClassLoader.getSystemClassLoader());
                final byte[] classBytes = locator.locate(helperName).resolve();
                typeDefinitions.put(helperName, classBytes);
            }
            return typeDefinitions;
        }
    }

}
