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
package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.configuration.AgentArgumentsConfigurationSource;
import co.elastic.apm.agent.configuration.ApmServerConfigurationSource;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.PrefixingConfigurationSourceWrapper;
import co.elastic.apm.agent.configuration.source.PropertyFileConfigurationSource;
import co.elastic.apm.agent.configuration.source.SystemPropertyConfigurationSource;
import co.elastic.apm.agent.context.ClosableLifecycleListenerAdapter;
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
import co.elastic.apm.agent.util.ExecutorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.configuration.source.ConfigurationSource;
import org.stagemonitor.configuration.source.EnvironmentVariableConfigurationSource;
import org.stagemonitor.configuration.source.SimpleSource;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ElasticApmTracerBuilder {
    /**
     * See {@link co.elastic.apm.attach.ElasticApmAttacher#TEMP_PROPERTIES_FILE_KEY}
     */
    @SuppressWarnings("JavadocReference")
    private static final String TEMP_PROPERTIES_FILE_KEY = "c";

    private final Logger logger;
    private final String ephemeralId;

    @Nullable
    private ConfigurationRegistry configurationRegistry;

    @Nullable
    private Reporter reporter;

    private ObjectPoolFactory objectPoolFactory;

    private final List<LifecycleListener> extraLifecycleListeners;

    private final List<ConfigurationSource> configSources;

    public ElasticApmTracerBuilder() {
        this(getConfigSources(null));
    }

    public ElasticApmTracerBuilder(List<ConfigurationSource> configSources){
        this.configSources = configSources;
        this.ephemeralId = UUID.randomUUID().toString();
        LoggingConfiguration.init(configSources, ephemeralId);
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


    public ElasticApmTracerBuilder withObjectPoolFactory(ObjectPoolFactory objectPoolFactory) {
        this.objectPoolFactory = objectPoolFactory;
        return this;
    }

    public ElasticApmTracerBuilder withLifecycleListener(LifecycleListener listener) {
        this.extraLifecycleListeners.add(listener);
        return this;
    }

    public ElasticApmTracer build() {
        return build(false);
    }

    /**
     * NOTE: THIS IS A CONVENIENCE METHOD ONLY TO BE USED WITHIN TESTS
     *
     * @return the built and started tracer
     */
    public ElasticApmTracer buildAndStart() {
        return build(true);
    }

    private ElasticApmTracer build(boolean startTracer) {
        boolean addApmServerConfigSource = false;
        List<LifecycleListener> lifecycleListeners = new ArrayList<>();

        if (configurationRegistry == null) {
            // setup default config registry, should be already set when testing
            addApmServerConfigSource = true;
            configurationRegistry = getDefaultConfigurationRegistry(configSources);
            lifecycleListeners.add(scheduleReloadAtRate(configurationRegistry, 30, TimeUnit.SECONDS));
        }

        ApmServerClient apmServerClient = new ApmServerClient(configurationRegistry.getConfig(ReporterConfiguration.class));
        MetaData metaData = MetaData.create(configurationRegistry, ephemeralId);
        if (addApmServerConfigSource) {
            // adding remote configuration source last will make it highest priority
            DslJsonSerializer payloadSerializer = new DslJsonSerializer(configurationRegistry.getConfig(StacktraceConfiguration.class), apmServerClient);
            ApmServerConfigurationSource configurationSource = new ApmServerConfigurationSource(payloadSerializer, metaData, apmServerClient);

            // unlike the ordering of configuration sources above, this will make it highest priority
            // as it's inserted first in the list.
            configurationRegistry.addConfigurationSource(configurationSource);

            lifecycleListeners.add(configurationSource);
        }

        if (reporter == null) {
            reporter = new ReporterFactory().createReporter(configurationRegistry, apmServerClient, metaData);
        }

        ElasticApmTracer tracer = new ElasticApmTracer(configurationRegistry, reporter, objectPoolFactory, apmServerClient, metaData);
        lifecycleListeners.addAll(DependencyInjectingServiceLoader.load(LifecycleListener.class, tracer));
        lifecycleListeners.addAll(extraLifecycleListeners);
        tracer.init(lifecycleListeners);
        if (startTracer) {
            tracer.start(false);
        }
        return tracer;
    }

    private LifecycleListener scheduleReloadAtRate(final ConfigurationRegistry configurationRegistry, final int rate, TimeUnit seconds) {
        final ScheduledThreadPoolExecutor configurationReloader = ExecutorUtils.createSingleThreadSchedulingDaemonPool("configuration-reloader");
        configurationReloader.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                logger.debug("Beginning scheduled configuration reload (interval is {} sec)...", rate);
                configurationRegistry.reloadDynamicConfigurationOptions();
                logger.debug("Finished scheduled configuration reload");
            }
        }, rate, rate, seconds);
        return ClosableLifecycleListenerAdapter.of(new Closeable() {
            @Override
            public void close() {
                configurationReloader.shutdown();
            }
        });
    }

    private ConfigurationRegistry getDefaultConfigurationRegistry(List<ConfigurationSource> configSources) {
        List<ConfigurationOptionProvider> providers = DependencyInjectingServiceLoader.load(ConfigurationOptionProvider.class);
        try {
            return ConfigurationRegistry.builder()
                .configSources(configSources)
                .optionProviders(providers)
                .failOnMissingRequiredValues(true)
                .build();
        } catch (IllegalStateException e) {
            logger.warn(e.getMessage());

            // provide a default no-op configuration in case of invalid configuration (missing required value for example)
            return ConfigurationRegistry.builder()
                .addConfigSource(new SimpleSource("Noop Configuration")
                    .add(TracerConfiguration.RECORDING, "false")
                    .add(CoreConfiguration.INSTRUMENT, "false")
                    .add(CoreConfiguration.SERVICE_NAME, "none")
                    .add(CoreConfiguration.SAMPLE_RATE, "0"))
                .optionProviders(providers)
                .build();
        }
    }

    /**
     * Provides an ordered list of local configuration sources, sorted in decreasing priority (first wins)
     *
     * @param agentArguments agent arguments (if any)
     * @return ordered list of configuration sources
     */
    // Must not initialize any loggers with this as the logger is configured based on configuration.
    public static List<ConfigurationSource> getConfigSources(@Nullable String agentArguments) {
        List<ConfigurationSource> result = new ArrayList<>();

        // highest priority : JVM system properties (before adding remote configuration)

        // java system properties
        result.add(new PrefixingConfigurationSourceWrapper(new SystemPropertyConfigurationSource(), "elastic.apm."));

        // environment variables
        result.add(new PrefixingConfigurationSourceWrapper(new EnvironmentVariableConfigurationSource(), "ELASTIC_APM_"));

        // loads properties file next to agent jar or with path provided from config.
        // while it depends on sources above, it has higher priority and is thus inserted before them
        String configFileLocation = CoreConfiguration.getConfigFileLocation(result);
        if (configFileLocation != null && PropertyFileConfigurationSource.getFromFileSystem(configFileLocation) != null) {
            result.add(0, new PropertyFileConfigurationSource(configFileLocation));
        }

        if (agentArguments != null && !agentArguments.isEmpty()) {
            // runtime attachment: self-attachment API and attacher jar
            // configuration is stored in a temporary file to pass it to the agent
            AgentArgumentsConfigurationSource agentArgs = AgentArgumentsConfigurationSource.parse(agentArguments);
            ConfigurationSource attachmentConfig = getAttachmentConfigSource(agentArgs.getValue(TEMP_PROPERTIES_FILE_KEY));
            if (attachmentConfig != null) {
                result.add(attachmentConfig);
            }
        }

        // only used for testing, will not load elasticapm.properties from app classpath as this code is
        // running in the bootstrap classloader. When testing, it loads elasticapm.properties only because agent classes
        // are loaded by the system classloader and not the bootstrap classloader
        if (PropertyFileConfigurationSource.isPresent("elasticapm.properties")) {
            result.add(new PropertyFileConfigurationSource("elasticapm.properties"));
        }

        // lowest priority: implicit default configuration

        return result;
    }

    /**
     * Loads the configuration from the temporary properties file created by ElasticApmAttacher
     */
    @Nullable
    private static ConfigurationSource getAttachmentConfigSource(@Nullable String configFileLocation) {
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
