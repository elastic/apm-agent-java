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
package co.elastic.apm.agent.bci.classloading;

import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import net.bytebuddy.dynamic.loading.MultipleParentClassLoader;

import java.util.Arrays;
import java.util.Map;

/**
 * The plugin class loader has both the agent class loader and the target class loader as the parent.
 * This is important so that the plugin class loader has direct access to the agent class loader
 * otherwise, filtering class loaders (like OSGi) have a chance to interfere
 *
 * @see co.elastic.apm.agent.bci.IndyBootstrap
 */
public class IndyPluginClassLoader extends ByteArrayClassLoader.ChildFirst {
    public IndyPluginClassLoader(ClassLoader targetClassLoader, ClassLoader agentClassLoader, Map<String, byte[]> typeDefinitions) {
        super(new MultipleParentClassLoader(agentClassLoader, Arrays.asList(agentClassLoader, targetClassLoader)), true, typeDefinitions, PersistenceHandler.MANIFEST);
    }

    public IndyPluginClassLoader(ClassLoader agentClassLoader, Map<String, byte[]> typeDefinitions) {
        super(agentClassLoader, true, typeDefinitions, PersistenceHandler.MANIFEST);
    }
}
