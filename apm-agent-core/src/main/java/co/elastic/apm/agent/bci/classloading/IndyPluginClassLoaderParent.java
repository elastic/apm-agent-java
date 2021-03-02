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

import net.bytebuddy.dynamic.loading.InjectionClassLoader;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

/**
 * This class loader always looks up agent classes through the agent class loader and any other class through the
 * target class loader, which is set as its "official" parent.
 * Resource lookup is done exactly the same for {@code .class} resources, to ensure consistency. The lookup for any other
 * resource first searches through the agent CL and if not found searches through the target CL.
 * This class loader is not used as a loading (or defining) loader for any class, only to facilitate proper delegation
 * of class loading.
 */
class IndyPluginClassLoaderParent extends InjectionClassLoader {

    private static String[] agentPackages;
    private static String[] agentClassResourceRoots;

    static {
        setAgentPackages(Collections.singletonList("co.elastic.apm.agent"));
    }

    /**
     * May be used in the future once we remove shading. Currently only used in tests, where libraries are not shaded.
     * Should not be used outside of this class, other than for testing purposes.
     *
     * @param agentPackagesToSet packages to set as agent packages
     */
    static void setAgentPackages(List<String> agentPackagesToSet) {
        agentPackages = new String[agentPackagesToSet.size()];
        agentClassResourceRoots = new String[agentPackagesToSet.size()];
        for (int i = 0; i < agentPackagesToSet.size(); i++) {
            String agentPackage = agentPackagesToSet.get(i);
            agentPackages[i] = agentPackage;
            agentClassResourceRoots[i] = agentPackage.replace('.', '/');
        }
    }

    private final ClassLoader agentClassLoader;

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
        super(targetClassLoader, true);
        //noinspection ConstantConditions
        if (agentClassLoader == null || targetClassLoader == null) {
            throw new NullPointerException("The bootstrap class loader cannot be used as one of this class loader parents. " +
                "Use a single-parent class loader instead.");
        }
        this.agentClassLoader = agentClassLoader;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        for (int i = 0; i < agentPackages.length; i++) {
            if (name.startsWith(agentPackages[i])) {
                Class<?> type = agentClassLoader.loadClass(name);
                if (resolve) {
                    resolveClass(type);
                }
                return type;
            }
        }
        return super.loadClass(name, resolve);
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
            return super.getResource(name);
        }

        URL resource = agentClassLoader.getResource(name);
        if (resource != null) {
            return resource;
        } else {
            return super.getResource(name);
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
            return super.getResources(name);
        }

        Enumeration<URL> resources = agentClassLoader.getResources(name);
        if (resources.hasMoreElements()) {
            return resources;
        } else {
            return super.getResources(name);
        }
    }

    /**
     * This method should never be called by {@link InjectionClassLoader#defineClasses} because we always set
     * {@code sealed == true}.
     */
    @Override
    protected Map<String, Class<?>> doDefineClasses(Map<String, byte[]> typeDefinitions) {
        throw new UnsupportedOperationException("This class loader does not load classes, it is only used for loading delegation.");
    }
}
