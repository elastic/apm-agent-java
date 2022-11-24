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

import co.elastic.apm.agent.bci.classloading.IndyPluginClassLoader;

import javax.annotation.Nullable;

public class ClassLoaderUtils {

    public static boolean isAgentClassLoader(@Nullable ClassLoader classLoader) {
        return (classLoader != null && classLoader.getClass().getName().startsWith("co.elastic.apm")) ||
            // This one also covers unit tests, where the app class loader loads the agent
            PrivilegedActionUtils.getClassLoader(ClassLoaderUtils.class).equals(classLoader);
    }

    public static boolean isBootstrapClassLoader(@Nullable ClassLoader classLoader) {
        return classLoader == null;
    }

    public static boolean isInternalPluginClassLoader(@Nullable ClassLoader classLoader) {
        if (classLoader == null) {
            return false;
        }
        return IndyPluginClassLoader.class.getName().equals(classLoader.getClass().getName());
    }
}
