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
package co.elastic.test;

import co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * A Child-first class loader used for tests.
 * Specifically, used within {@link co.elastic.apm.agent.TestClassWithDependencyRunner} for tests that require encapsulated
 * test classpath, for example - for testing specific library versions.
 * In order for classes that are loaded by this class loader to be instrumented, it must be outside of the {@code co.elastic.apm}
 * package, otherwise it may be excluded if tested through {@link CustomElementMatchers#isAgentClassLoader()}.
 */
public class ChildFirstURLClassLoader extends URLClassLoader {

    private final List<URL> urls;

    public ChildFirstURLClassLoader(List<URL> urls) {
        super(urls.toArray(new URL[]{}));
        this.urls = urls;
    }

    @Override
    public String getName() {
        return "Test class class loader: " + urls;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            try {
                // First, check if the class has already been loaded
                Class<?> c = findLoadedClass(name);
                if (c == null) {
                    c = findClass(name);
                    if (resolve) {
                        resolveClass(c);
                    }
                }
                return c;
            } catch (ClassNotFoundException e) {
                return super.loadClass(name, resolve);
            }
        }
    }


    @Override
    public URL findResource(String name) {
        return super.findResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        Enumeration<URL> resources = super.getResources(name);
        List<URL> resourcesList = new ArrayList<>();
        while (resources.hasMoreElements()) {
            resourcesList.add(resources.nextElement());
        }
        Collections.reverse(resourcesList);
        return Collections.enumeration(resourcesList);
    }

    @Override
    public String toString() {
        return getName();
    }

}
