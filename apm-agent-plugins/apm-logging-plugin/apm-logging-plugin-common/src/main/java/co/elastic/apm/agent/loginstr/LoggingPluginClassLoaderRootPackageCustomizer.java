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
package co.elastic.apm.agent.loginstr;

import co.elastic.apm.agent.bci.PluginClassLoaderRootPackageCustomizer;

import java.util.Arrays;
import java.util.Collection;

/**
 * An abstract class providing the common implementation for all logging plugin submodules.
 * This class is abstract because it cannot be used as is, which is also why it is not listed as a {@link PluginClassLoaderRootPackageCustomizer}
 * service provider - it would be loaded by the service loader, but since it's not in the same package as all actual
 * plugins, it won't be used, as the lookup depends on the plugin package (e.g. {@code co.elastic.apm.agent.log4j2}).
 * In order to use it by a logging plugin, the plugin needs to extend it and list the implementation as a service provider.
 */
public abstract class LoggingPluginClassLoaderRootPackageCustomizer extends PluginClassLoaderRootPackageCustomizer {

    @Override
    public Collection<String> pluginClassLoaderRootPackages() {
        return Arrays.asList(getPluginPackage(), "co.elastic.logging");
    }
}
