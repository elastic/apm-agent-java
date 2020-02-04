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
package co.elastic.apm.agent.util;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.Set;

public class DependencyInjectingServiceLoader<T> {

    private final Class<T> clazz;
    private final Object[] constructorArguments;
    private final Class<?>[] constructorTypes;
    private final List<T> instances = new ArrayList<>();
    @Nullable
    private final ClassLoader classLoader;

    private DependencyInjectingServiceLoader(Class<T> clazz, Object... constructorArguments) {
        this.clazz = clazz;
        this.constructorArguments = constructorArguments;
        this.classLoader = clazz.getClassLoader();
        List<Class<?>> types = new ArrayList<>(constructorArguments.length);
        for (Object constructorArgument : constructorArguments) {
            types.add(constructorArgument.getClass());
        }
        constructorTypes = types.toArray(new Class[]{});
        try {
            final Enumeration<URL> resources = getServiceDescriptors(clazz);
            Set<String> implementations = getImplementations(resources);
            instantiate(implementations);
        } catch (IOException e) {
            throw new ServiceConfigurationError(e.getMessage(), e);
        }
    }

    private Enumeration<URL> getServiceDescriptors(Class<T> clazz) throws IOException {
        if (classLoader != null) {
            return classLoader.getResources("META-INF/services/" + clazz.getName());
        } else {
            return ClassLoader.getSystemResources("META-INF/services/" + clazz.getName());
        }
    }

    public static <T> List<T> load(Class<T> clazz, Object... constructorArguments) {
        return new DependencyInjectingServiceLoader<>(clazz, constructorArguments).instances;
    }

    private static boolean isComment(String serviceImplementationClassName) {
        return serviceImplementationClassName.startsWith("#");
    }

    private Set<String> getImplementations(Enumeration<URL> resources) throws IOException {
        Set<String> implementations = new LinkedHashSet<>();
        while (resources.hasMoreElements()) {
            final URL url = resources.nextElement();
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
        return implementations;
    }

    private void instantiate(Set<String> implementations) {
        for (String implementation : implementations) {
            instances.add(instantiate(implementation));
        }
    }

    private T instantiate(String implementation) {
        try {
            final Class<?> implementationClass = Class.forName(implementation, true, classLoader);
            Constructor<?> constructor = getMatchingConstructor(implementationClass);
            if (constructor != null) {
                return clazz.cast(constructor.newInstance(constructorArguments));
            } else {
                return clazz.cast(implementationClass.getConstructor().newInstance());
            }
        } catch (InstantiationException e) {
            throw new ServiceConfigurationError(String.format("unable to instantiate '%s' class, please check descriptor in META-INF and class declaration ", implementation), e);
        } catch (Exception e) {
            throw new ServiceConfigurationError(e.getMessage(), e);
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
