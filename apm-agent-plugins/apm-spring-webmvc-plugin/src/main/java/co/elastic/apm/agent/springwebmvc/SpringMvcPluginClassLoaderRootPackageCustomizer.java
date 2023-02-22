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
package co.elastic.apm.agent.springwebmvc;

import co.elastic.apm.agent.sdk.PluginClassLoaderRootPackageCustomizer;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class SpringMvcPluginClassLoaderRootPackageCustomizer implements PluginClassLoaderRootPackageCustomizer {

    @Override
    public boolean isIncludePluginPackage() {
        return true;
    }

    @Override
    public Collection<String> pluginClassLoaderRootPackages() {
        return Collections.singleton("co.elastic.apm.agent.servlet");
    }

    @Override
    public Map<String, ? extends Collection<String>> requiredModuleOpens() {
        return Collections.<String, Collection<String>>emptyMap();
    }
}
