/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.agent.bci.classloading;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

/**
 * This class loader always looks up agent-related classes through the agent class loader and any other class through the
 * target class loader, which is set as its "official" parent.
 * Resource lookup is done exactly the same for {@code .class} resources, to ensure consistency. The lookup for any other
 * resource first searches through the agent CL and if not found searches through the target CL.
 * This class loader should not be used as the defining loader for any class. Its sole purpose is to facilitate proper
 * delegation of class loading to the right parent.
 */
class IndyPluginClassLoaderParent extends ClassLoader {

    private final static String[] agentPackages;
    private final static String[] agentClassResourceRoots;

    static {
        registerAsParallelCapable();

        /*
         * This package list should be extended in the future once we remove shading.
         * Currently, only the agent root package is required in runtime, however the Byte Buddy package is required for
         * tests, where libraries are not shaded.
         */
        agentPackages = new String[]{
            "co.elastic.apm.agent",
            "net.bytebuddy"
        };
        agentClassResourceRoots = new String[agentPackages.length];
        for (int i = 0; i < agentPackages.length; i++) {
            String agentPackage = agentPackages[i];
            agentClassResourceRoots[i] = agentPackage.replace('.', '/');
        }
    }

    private final ClassLoader agentClassLoader;
    private final ClassLoader targetClassLoader;

    /**
     * Constructs a parent loader for {@link IndyPluginClassLoader} to enable class loading delegation to two parents.
     * Neither parent may be the bootstrap class loader, which is the implicit root parent of any class loader. In such
     * case, a single-parent class loader should be used.
     *
     * @param agentClassLoader  will be used to delegate loading of agent classes
     * @param targetClassLoader will be used to delegate loading of non-agent classes (typically - application or
     *                          library classes).
     * @throws NullPointerException if any of the given parents is {@code null}, implying for the bootstrap class loader
     */
    IndyPluginClassLoaderParent(ClassLoader agentClassLoader, ClassLoader targetClassLoader) throws NullPointerException {
        // We should not delegate class loading to super but to one of the parents, however this is preferable over using
        // null, which implies for the bootstrap CL
        super(targetClassLoader);
        //noinspection ConstantConditions
        if (agentClassLoader == null || targetClassLoader == null) {
            throw new NullPointerException("The bootstrap class loader cannot be used as one of this class loader parents. " +
                "Use a single-parent class loader instead.");
        }
        this.agentClassLoader = agentClassLoader;
        this.targetClassLoader = targetClassLoader;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            for (int i = 0; i < agentPackages.length; i++) {
                if (name.startsWith(agentPackages[i])) {
                    Class<?> type = agentClassLoader.loadClass(name);
                    if (resolve) {
                        resolveClass(type);
                    }
                    return type;
                }
            }
            // This means we never resolve class on loading, but it ensures this is not the defining class loader as it
            // would be if we invoked super.loadClass
            return targetClassLoader.loadClass(name);
        }
    }

    /**
     * Resource lookup is done exactly the same as in {@link #loadClass(String, boolean)} for {@code .class} resources,
     * to ensure consistency with class loading.
     * The lookup for any other resource first searches through the agent CL and if not found searches through the target CL.
     * {@inheritDoc}
     */
    public URL getResource(String name) {
        if (name.endsWith(".class")) {
            for (int i = 0; i < agentClassResourceRoots.length; i++) {
                if (name.startsWith(agentClassResourceRoots[i])) {
                    return agentClassLoader.getResource(name);
                }
            }
            return targetClassLoader.getResource(name);
        }

        URL resource = agentClassLoader.getResource(name);
        if (resource != null) {
            return resource;
        } else {
            return targetClassLoader.getResource(name);
        }
    }

    /**
     * Resource lookup is done exactly the same as in {@link #loadClass(String, boolean)} for {@code .class} resources,
     * to ensure consistency with class loading.
     * The lookup for any other resource first searches through the agent CL and if not found searches through the target CL.
     * We can potentially combine search results for non {@code .class} resources, but currently this doesn't seem required.
     * {@inheritDoc}
     */
    public Enumeration<URL> getResources(String name) throws IOException {
        if (name.endsWith(".class")) {
            for (int i = 0; i < agentClassResourceRoots.length; i++) {
                if (name.startsWith(agentClassResourceRoots[i])) {
                    return agentClassLoader.getResources(name);
                }
            }
            return targetClassLoader.getResources(name);
        }

        Enumeration<URL> resources = agentClassLoader.getResources(name);
        if (resources.hasMoreElements()) {
            return resources;
        } else {
            return targetClassLoader.getResources(name);
        }
    }
}
