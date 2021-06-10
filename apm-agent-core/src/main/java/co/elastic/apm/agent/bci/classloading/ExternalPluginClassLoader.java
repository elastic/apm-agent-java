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

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Loads plugins from {@link CoreConfiguration#getPluginsDir() plugins_dir}
 *
 * @see co.elastic.apm.agent.bci.IndyBootstrap
 */
public class ExternalPluginClassLoader extends URLClassLoader {
    private final List<String> classNames;

    public ExternalPluginClassLoader(File pluginJar, ClassLoader agentClassLoader) throws IOException {
        super(new URL[]{pluginJar.toURI().toURL()}, agentClassLoader);
        classNames = Collections.unmodifiableList(scanForClasses(pluginJar));
        if (classNames.contains(ElasticApmInstrumentation.class.getName())) {
            throw new IllegalStateException("The plugin %s contains the plugin SDK. Please make sure the scope for the dependency apm-agent-plugin-sdk is set to provided.");
        }
    }

    private List<String> scanForClasses(File pluginJar) throws IOException {
        List<String> tempClassNames = new ArrayList<>();
        try (JarFile jarFile = new JarFile(pluginJar)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry jarEntry = entries.nextElement();
                if (jarEntry.getName().endsWith(".class")) {
                    tempClassNames.add(jarEntry.getName().replace('/', '.').substring(0, jarEntry.getName().length() - 6));
                }
            }
        }
        return tempClassNames;
    }

    public List<String> getClassNames() {
        return classNames;
    }

}
