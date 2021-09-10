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

import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import net.bytebuddy.dynamic.loading.MultipleParentClassLoader;

import javax.annotation.Nullable;
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

    private static final ClassLoader SYSTEM_CLASS_LOADER = ClassLoader.getSystemClassLoader();

    public IndyPluginClassLoader(@Nullable ClassLoader targetClassLoader, ClassLoader agentClassLoader, Map<String, byte[]> typeDefinitions) {
        super(getParent(targetClassLoader, agentClassLoader), true, typeDefinitions, PersistenceHandler.MANIFEST);
    }

    private static ClassLoader getParent(@Nullable ClassLoader targetClassLoader, ClassLoader agentClassLoader) {
        if (targetClassLoader == null) {
            // the MultipleParentClassLoader doesn't support null values
            // the agent class loader already has the bootstrap class loader as the parent
            return agentClassLoader;
        }
        if (agentClassLoader == SYSTEM_CLASS_LOADER) {
            // we're inside a unit test
            // in unit tests, we need to search the target class loader first, unless it's an agent class
            // that's because the system class loader may contain another version of the library under test
            // see also co.elastic.apm.agent.TestClassWithDependencyRunner
            return new IndyPluginClassLoaderParent(agentClassLoader, targetClassLoader);
        } else {
            // in prod, always search in the agent class loader first
            // this ensures that we're referencing the agent bundled classes in advices rather than the ones form the application
            // (for example for log4j, Byte Buddy, or even dependencies that are bundled in external plugins etc.)
            return new MultipleParentClassLoader(agentClassLoader, Arrays.asList(agentClassLoader, targetClassLoader));
        }
    }
}
