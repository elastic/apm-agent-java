/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.configuration;

import co.elastic.apm.agent.configuration.source.PropertyFileConfigurationSource;
import org.mockito.Mockito;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.configuration.source.ConfigurationSource;
import org.stagemonitor.configuration.source.SimpleSource;

import java.util.ServiceLoader;

import static org.mockito.Mockito.spy;

public class SpyConfiguration {

    public static final String CONFIG_SOURCE_NAME = "test config source";

    /**
     * Creates a configuration registry where all {@link ConfigurationOptionProvider}s are wrapped with
     * {@link Mockito#spy(Object)}
     * <p>
     * That way, the default configuration values are returned but can be overridden by {@link Mockito#when(Object)}
     *
     * @return a syp configuration registry
     */
    public static ConfigurationRegistry createSpyConfig() {
        return createSpyConfig(new SimpleSource(CONFIG_SOURCE_NAME));
    }

    /**
     * Creates a configuration registry where all {@link ConfigurationOptionProvider}s are wrapped with
     * {@link org.mockito.Mockito#spy(Object)}
     * <p>
     * That way, the default configuration values are returned but can be overridden by {@link org.mockito.Mockito#when(Object)}
     *
     * @return a syp configuration registry
     * @param configurationSource
     */
    public static ConfigurationRegistry createSpyConfig(ConfigurationSource configurationSource) {
        ConfigurationRegistry.Builder builder = ConfigurationRegistry.builder();
        for (ConfigurationOptionProvider options : ServiceLoader.load(ConfigurationOptionProvider.class)) {
            builder.addOptionProvider(spy(options));
        }
        return builder
            .addConfigSource(configurationSource)
            .addConfigSource(new PropertyFileConfigurationSource("elasticapm.properties"))
            .build();
    }

    public static void reset(ConfigurationRegistry config) {
        for (ConfigurationOptionProvider provider : config.getConfigurationOptionProviders()) {
            Mockito.reset(provider);
        }
    }
}
