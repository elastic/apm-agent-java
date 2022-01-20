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
package co.elastic.apm.agent.util;

import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.Set;

public class DependencyInjectingServiceLoader<T> {

    private static final Logger logger = LoggerFactory.getLogger(DependencyInjectingServiceLoader.class);
    private final Class<T> clazz;
    private final Object[] constructorArguments;
    private final Class<?>[] constructorTypes;
    private final List<T> instances = new ArrayList<>();
    private final Set<Class<?>> implementationClassCache;
    private final Set<URL> resourcePathCache;

    private DependencyInjectingServiceLoader(Class<T> clazz, Object... constructorArguments) {
        this(clazz, Collections.singletonList(clazz.getClassLoader()), constructorArguments);
    }

    private DependencyInjectingServiceLoader(Class<T> clazz, List<ClassLoader> classLoaders, Object... constructorArguments) {
        this.clazz = clazz;
        this.constructorArguments = constructorArguments;
        List<Class<?>> types = new ArrayList<>(constructorArguments.length);
        for (Object constructorArgument : constructorArguments) {
            types.add(constructorArgument.getClass());
        }
        constructorTypes = types.toArray(new Class[]{});
        implementationClassCache = new HashSet<>();
        resourcePathCache = new HashSet<>();
        try {
            for (ClassLoader classLoader : classLoaders) {
                final Enumeration<URL> resources = getServiceDescriptors(classLoader, clazz);
                instantiate(classLoader, getImplementations(resources));
            }
        } catch (IOException e) {
            throw new ServiceConfigurationError(e.getMessage(), e);
        }
    }

    private Enumeration<URL> getServiceDescriptors(@Nullable ClassLoader classLoader, Class<T> clazz) throws IOException {
        if (classLoader != null) {
            return classLoader.getResources("META-INF/services/" + clazz.getName());
        } else {
            return ClassLoader.getSystemResources("META-INF/services/" + clazz.getName());
        }
    }

    public static <T> List<T> load(Class<T> clazz, Object... constructorArguments) {
        return new DependencyInjectingServiceLoader<>(clazz, constructorArguments).instances;
    }

    public static <T> List<T> load(Class<T> clazz, List<ClassLoader> classLoaders, Object... constructorArguments) {
        return new DependencyInjectingServiceLoader<>(clazz, classLoaders, constructorArguments).instances;
    }

    private static boolean isComment(String serviceImplementationClassName) {
        return serviceImplementationClassName.startsWith("#");
    }

    private Set<String> getImplementations(Enumeration<URL> resources) throws IOException {
        Set<String> implementations = new LinkedHashSet<>();
        while (resources.hasMoreElements()) {
            final URL url = resources.nextElement();
            // If this resource path was not yet used for service implementations discovery, read from it
            if (resourcePathCache.add(url)) {
                try (InputStream inputStream = url.openStream()) {
                    final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                    while (reader.ready()) {
                        String line = reader.readLine().trim();
                        if (!isComment(line) && !line.isEmpty()) {
                            implementations.add(line);
                        }
                    }
                }
            }
        }
        return implementations;
    }

    private void instantiate(ClassLoader classLoader, Set<String> implementations) {
        for (String implementation : implementations) {
            T instance = instantiate(classLoader, implementation);
            if (instance != null) {
                instances.add(instance);
            }
        }
    }

    @Nullable
    private T instantiate(ClassLoader classLoader, String implementation) {
        try {
            final Class<?> implementationClass = Class.forName(implementation, true, classLoader);
            if (!implementationClassCache.add(implementationClass)) {
                // If this service implementation class was already instantiated, no need for another instance
                return null;
            }
            checkClassModifiers(implementationClass);
            Constructor<?> constructor = getMatchingConstructor(implementationClass);
            if (constructor != null) {
                checkConstructorModifiers(constructor);
                return clazz.cast(constructor.newInstance(constructorArguments));
            } else {
                constructor = implementationClass.getConstructor();
                checkConstructorModifiers(constructor);
                return clazz.cast(constructor.newInstance());
            }
        } catch (InstantiationException e) {
            String msg = String.format("unable to instantiate '%s', please check descriptor in META-INF", implementation);
            throw new ServiceConfigurationError(msg, e);
        } catch(UnsupportedClassVersionError e) {
            logger.debug(String.format("unable to instantiate '%s', unsupported class version error: %s", implementation, e.getMessage()));
            return null;
        } catch (Exception e) {
            throw new ServiceConfigurationError(e.getMessage(), e);
        }
    }

    private static void checkConstructorModifiers(Constructor<?> constructor) {
        if (!Modifier.isPublic(constructor.getModifiers())) {
            throw new ServiceConfigurationError("constructor is not public : " + constructor);
        }
    }

    private static void checkClassModifiers(Class<?> clazz) {
        boolean isAbstract = Modifier.isAbstract(clazz.getModifiers());
        boolean isPublic = Modifier.isPublic(clazz.getModifiers());

        if (isAbstract || !isPublic) {
            throw new ServiceConfigurationError(String.format("unable to instantiate '%s' because it's either abstract or not public", clazz));
        }
    }

    @Nullable
    private Constructor<?> getMatchingConstructor(Class<?> implementationClass) {
        for (Constructor<?> constructor : implementationClass.getConstructors()) {
            if (isAllAssignableFrom(constructor.getParameterTypes(), constructorTypes)) {
                return constructor;
            }
        }
        return null;
    }

    private boolean isAllAssignableFrom(Class<?>[] types, Class<?>[] otherTypes) {
        if (types.length != otherTypes.length) {
            return false;
        }
        for (int i = 0; i < types.length; i++) {
            if (!types[i].isAssignableFrom(otherTypes[i])) {
                return false;
            }
        }
        return true;
    }

}
