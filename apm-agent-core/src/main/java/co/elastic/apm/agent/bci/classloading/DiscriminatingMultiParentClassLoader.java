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
package co.elastic.apm.agent.bci.classloading;

import net.bytebuddy.matcher.ElementMatcher;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * This class loader works similar to {@link net.bytebuddy.dynamic.loading.MultipleParentClassLoader}.
 * The difference is that an {@link ElementMatcher} can be supplied that determines (based on the class name) which class lookups are delegated to each parent.
 * This is used, for example, to avoid looking up log4j2 classes from the agent class loader in the context of an advice.
 */
class DiscriminatingMultiParentClassLoader extends ClassLoader {

    private static final String CLASS_EXTENSION = ".class";
    private final List<ClassLoader> parents;
    private final List<ElementMatcher<String>> discriminators;

    static {
        registerAsParallelCapable();
    }

    DiscriminatingMultiParentClassLoader(ClassLoader singleParent, ElementMatcher<String> classesToLoadFromParent) throws NullPointerException {
        super(singleParent);
        //noinspection ConstantConditions
        if (singleParent == null) {
            throw new NullPointerException("The bootstrap class loader cannot be used as one of this class loader parents. " +
                "Use a single-parent class loader instead.");
        }
        this.parents = Arrays.asList(singleParent);
        this.discriminators = Arrays.asList(classesToLoadFromParent);
    }

    DiscriminatingMultiParentClassLoader(ClassLoader agentClassLoader, ElementMatcher<String> classesToLoadFromAgentClassLoader,
                                         ClassLoader targetClassLoader, ElementMatcher<String> classesToLoadFromTargetClassLoader) throws NullPointerException {
        // We should not delegate class loading to super but to one of the parents, however this is preferable over using
        // null, which implies for the bootstrap CL
        super(targetClassLoader);
        //noinspection ConstantConditions
        if (agentClassLoader == null || targetClassLoader == null) {
            throw new NullPointerException("The bootstrap class loader cannot be used as one of this class loader parents. " +
                "Use a single-parent class loader instead.");
        }
        this.parents = Arrays.asList(agentClassLoader, targetClassLoader);
        this.discriminators = Arrays.asList(classesToLoadFromAgentClassLoader, classesToLoadFromTargetClassLoader);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            for (int i = 0, parentsSize = parents.size(); i < parentsSize; i++) {
                ClassLoader parent = parents.get(i);
                ElementMatcher<String> discriminator = discriminators.get(i);
                if (discriminator.matches(name)) {
                    try {
                        Class<?> type = parent.loadClass(name);
                        if (resolve) {
                            resolveClass(type);
                        }
                        return type;
                    } catch (ClassNotFoundException ignored) {
                        /* try next class loader */
                    }
                }
            }
            throw new ClassNotFoundException(name);
        }
    }

    /**
     * Resource lookup is done exactly the same as in {@link #loadClass(String, boolean)} for {@code .class} resources,
     * to ensure consistency with class loading.
     * The lookup for any other resource first searches through the agent CL and if not found searches through the target CL.
     * {@inheritDoc}
     */
    public URL getResource(String name) {
        String className = null;
        if (name.endsWith(CLASS_EXTENSION)) {
            className = name.replace('/', '.').substring(0, name.length() - CLASS_EXTENSION.length());
        }
        for (int i = 0, parentsSize = parents.size(); i < parentsSize; i++) {
            ClassLoader parent = parents.get(i);
            ElementMatcher<String> discriminator = discriminators.get(i);
            if (className == null || discriminator.matches(className)) {
                URL url = parent.getResource(name);
                if (url != null) {
                    return url;
                }
            }
        }
        return null;
    }

    /**
     * Resource lookup is done exactly the same as in {@link #loadClass(String, boolean)} for {@code .class} resources,
     * to ensure consistency with class loading.
     * The lookup for any other resource first searches through the agent CL and if not found searches through the target CL.
     * We can potentially combine search results for non {@code .class} resources, but currently this doesn't seem required.
     * {@inheritDoc}
     */
    public Enumeration<URL> getResources(String name) throws IOException {
        String className = null;
        if (name.endsWith(CLASS_EXTENSION)) {
            className = name.replace('/', '.').substring(0, name.length() - CLASS_EXTENSION.length());
        }
        for (int i = 0, parentsSize = parents.size(); i < parentsSize; i++) {
            ClassLoader parent = parents.get(i);
            ElementMatcher<String> discriminator = discriminators.get(i);
            if (className == null || discriminator.matches(className)) {
                Enumeration<URL> resources = parent.getResources(name);
                if (resources.hasMoreElements()) {
                    return resources;
                }
            }
        }
        return Collections.emptyEnumeration();
    }

    @Override
    public String toString() {
        return "DiscriminatingMultiParentClassLoader{" +
            "agentClassLoader = " + parents.get(0) + " discriminator = "+ discriminators.get(0) +
            ", targetClassLoader =" + parents.get(1) + " discriminator = " + discriminators.get(1) +
            '}';
    }
}
