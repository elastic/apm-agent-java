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
package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.configuration.AgentArgumentsConfigurationSource;
import co.elastic.apm.agent.configuration.ApmServerConfigurationSource;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.PrefixingConfigurationSourceWrapper;
import co.elastic.apm.agent.configuration.source.PropertyFileConfigurationSource;
import co.elastic.apm.agent.configuration.source.SystemPropertyConfigurationSource;
import co.elastic.apm.agent.context.LifecycleListener;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.logging.LoggingConfiguration;
import co.elastic.apm.agent.objectpool.ObjectPoolFactory;
import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.report.Reporter;
import co.elastic.apm.agent.report.ReporterConfiguration;
import co.elastic.apm.agent.report.ReporterFactory;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import co.elastic.apm.agent.util.DependencyInjectingServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.configuration.source.AbstractConfigurationSource;
import org.stagemonitor.configuration.source.ConfigurationSource;
import org.stagemonitor.configuration.source.EnvironmentVariableConfigurationSource;
import org.stagemonitor.configuration.source.SimpleSource;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class ElasticApmTracerBuilder {

    /**
     * See {@link co.elastic.apm.attach.ElasticApmAttacher#TEMP_PROPERTIES_FILE_KEY}
     */
    private static final String TEMP_PROPERTIES_FILE_KEY = "c";
    private final Logger logger;
    @Nullable
    private ConfigurationRegistry configurationRegistry;
    @Nullable
    private Reporter reporter;
    private Map<String, String> inlineConfig = new HashMap<>();
    @Nullable
    private final String agentArguments;
    private ObjectPoolFactory objectPoolFactory;
    private List<LifecycleListener> extraLifecycleListeners;

    public ElasticApmTracerBuilder() {
        this(null);
    }

    public ElasticApmTracerBuilder(@Nullable String agentArguments) {
        this.agentArguments = agentArguments;
        final List<ConfigurationSource> configSources = getConfigSources(this.agentArguments);
        LoggingConfiguration.init(configSources);
        logger = LoggerFactory.getLogger(getClass());
        objectPoolFactory = new ObjectPoolFactory();
        extraLifecycleListeners = new ArrayList<>();
    }

    public ElasticApmTracerBuilder configurationRegistry(ConfigurationRegistry configurationRegistry) {
        this.configurationRegistry = configurationRegistry;
        return this;
    }

    public ElasticApmTracerBuilder reporter(Reporter reporter) {
        this.reporter = reporter;
        return this;
    }

    public ElasticApmTracerBuilder withConfig(String key, String value) {
        inlineConfig.put(key, value);
        return this;
    }

    public ElasticApmTracerBuilder withObjectPoolFactory(ObjectPoolFactory objectPoolFactory) {
        this.objectPoolFactory = objectPoolFactory;
        return this;
    }

    public ElasticApmTracerBuilder withLifecycleListener(LifecycleListener listener) {
        this.extraLifecycleListeners.add(listener);
        return this;
    }

    public ElasticApmTracer build() {
        boolean addApmServerConfigSource = false;
        if (configurationRegistry == null) {
            addApmServerConfigSource = true;
            final List<ConfigurationSource> configSources = getConfigSources(agentArguments);
            configurationRegistry = getDefaultConfigurationRegistry(configSources);
        }
        final ApmServerClient apmServerClient = new ApmServerClient(configurationRegistry.getConfig(ReporterConfiguration.class));
        final DslJsonSerializer payloadSerializer = new DslJsonSerializer(configurationRegistry.getConfig(StacktraceConfiguration.class), apmServerClient);
        final MetaData metaData = MetaData.create(configurationRegistry, null, null);
        ApmServerConfigurationSource configurationSource = null;
        List<LifecycleListener> lifecycleListeners = new ArrayList<>();
        if (addApmServerConfigSource) {
            configurationSource = new ApmServerConfigurationSource(payloadSerializer, metaData, apmServerClient);
            configurationRegistry.addConfigurationSource(configurationSource);
            lifecycleListeners.add(configurationSource);
        }
        if (reporter == null) {
            reporter = new ReporterFactory().createReporter(configurationRegistry, apmServerClient, metaData);
        }
        ElasticApmTracer tracer = new ElasticApmTracer(configurationRegistry, reporter, objectPoolFactory);
        lifecycleListeners.addAll(DependencyInjectingServiceLoader.load(LifecycleListener.class, tracer));
        tracer.registerLifecycleListeners(lifecycleListeners);
        tracer.registerLifecycleListeners(extraLifecycleListeners);
        return tracer;
    }

    private ConfigurationRegistry getDefaultConfigurationRegistry(List<ConfigurationSource> configSources) {
        try {
            final ConfigurationRegistry configurationRegistry = ConfigurationRegistry.builder()
                .configSources(configSources)
                .optionProviders(DependencyInjectingServiceLoader.load(ConfigurationOptionProvider.class))
                .failOnMissingRequiredValues(true)
                .build();
            configurationRegistry.scheduleReloadAtRate(30, TimeUnit.SECONDS);
            return configurationRegistry;
        } catch (IllegalStateException e) {
            logger.warn(e.getMessage());
            return ConfigurationRegistry.builder()
                .addConfigSource(new SimpleSource("Noop Configuration")
                    .add(CoreConfiguration.ACTIVE, "false")
                    .add(CoreConfiguration.INSTRUMENT, "false")
                    .add(CoreConfiguration.SERVICE_NAME, "none")
                    .add(CoreConfiguration.SAMPLE_RATE, "0"))
                .optionProviders(DependencyInjectingServiceLoader.load(ConfigurationOptionProvider.class))
                .build();
        }
    }

    /*
     * Must not initialize any loggers with this as the logger is configured based on configuration.
     */
    private List<ConfigurationSource> getConfigSources(@Nullable String agentArguments) {
        List<ConfigurationSource> result = new ArrayList<>();
        if (agentArguments != null && !agentArguments.isEmpty()) {
            AgentArgumentsConfigurationSource agentArgs = AgentArgumentsConfigurationSource.parse(agentArguments);
            result.add(agentArgs);
            ConfigurationSource attachmentConfig = getAttachmentArguments(agentArgs.getValue(TEMP_PROPERTIES_FILE_KEY));
            if (attachmentConfig != null) {
                result.add(attachmentConfig);
            }
        }
        result.add(new PrefixingConfigurationSourceWrapper(new SystemPropertyConfigurationSource(), "elastic.apm."));
        result.add(new PrefixingConfigurationSourceWrapper(new EnvironmentVariableConfigurationSource(), "ELASTIC_APM_"));
        result.add(new AbstractConfigurationSource() {
            @Override
            public String getValue(String key) {
                return inlineConfig.get(key);
            }

            @Override
            public String getName() {
                return "Inline configuration";
            }
        });
        String configFileLocation = CoreConfiguration.getConfigFileLocation(result);
        if (configFileLocation != null && PropertyFileConfigurationSource.getFromFileSystem(configFileLocation) != null) {
            result.add(new PropertyFileConfigurationSource(configFileLocation));
        }
        // looks if we can find a elasticapm.properties on the classpath
        // mainly useful for unit tests
        if (PropertyFileConfigurationSource.isPresent("elasticapm.properties")) {
            result.add(new PropertyFileConfigurationSource("elasticapm.properties"));
        }
        return result;
    }

    /**
     * Loads the configuration from the temporary properties file created by ElasticApmAttacher
     */
    @Nullable
    private ConfigurationSource getAttachmentArguments(@Nullable String configFileLocation) {
        if (configFileLocation != null) {
            Properties fromFileSystem = PropertyFileConfigurationSource.getFromFileSystem(configFileLocation);
            if (fromFileSystem != null) {
                SimpleSource attachmentConfig = new SimpleSource("Attachment configuration");
                for (String key : fromFileSystem.stringPropertyNames()) {
                    attachmentConfig.add(key, fromFileSystem.getProperty(key));
                }
                return attachmentConfig;
            }
        }
        return null;
    }

}
