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
package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.configuration.AgentArgumentsConfigurationSource;
import co.elastic.apm.agent.configuration.ApmServerConfigurationSource;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.PrefixingConfigurationSourceWrapper;
import co.elastic.apm.agent.configuration.source.ConfigSources;
import co.elastic.apm.agent.configuration.source.SystemPropertyConfigurationSource;
import co.elastic.apm.agent.context.ClosableLifecycleListenerAdapter;
import co.elastic.apm.agent.context.LifecycleListener;
import co.elastic.apm.agent.impl.metadata.MetaData;
import co.elastic.apm.agent.impl.metadata.MetaDataFuture;
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
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.configuration.source.ConfigurationSource;
import org.stagemonitor.configuration.source.EnvironmentVariableConfigurationSource;
import org.stagemonitor.configuration.source.SimpleSource;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;
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

    /**
     * Constructs a new builder instance with default configuration sources
     */
    public ElasticApmTracerBuilder() {
        this(getConfigSources(null, false));
    }

    /**
     * Constructs a new builder instance
     *
     * @param configSources configuration sources, obtained from calling {@link #getConfigSources(String, boolean)}
     */
    public ElasticApmTracerBuilder(List<ConfigurationSource> configSources) {
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

        ApmServerClient apmServerClient = new ApmServerClient(configurationRegistry.getConfig(ReporterConfiguration.class), configurationRegistry.getConfig(CoreConfiguration.class));
        MetaDataFuture metaDataFuture = MetaData.create(configurationRegistry, ephemeralId);
        if (addApmServerConfigSource) {
            // adding remote configuration source last will make it highest priority
            DslJsonSerializer payloadSerializer = new DslJsonSerializer(
                configurationRegistry.getConfig(StacktraceConfiguration.class),
                apmServerClient,
                metaDataFuture
            );
            ApmServerConfigurationSource configurationSource = new ApmServerConfigurationSource(payloadSerializer, apmServerClient);

            // unlike the ordering of configuration sources above, this will make it highest priority
            // as it's inserted first in the list.
            configurationRegistry.addConfigurationSource(configurationSource);

            lifecycleListeners.add(configurationSource);
        }

        if (reporter == null) {
            reporter = new ReporterFactory().createReporter(configurationRegistry, apmServerClient, metaDataFuture);
        }

        ElasticApmTracer tracer = new ElasticApmTracer(configurationRegistry, reporter, objectPoolFactory, apmServerClient, ephemeralId, metaDataFuture);
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
     * @param premain        {@literal false} when using runtime attach {@literal true} when using -javaagent
     * @return ordered list of configuration sources
     */
    // Must not initialize any loggers with this as the logger is configured based on configuration.
    public static List<ConfigurationSource> getConfigSources(@Nullable String agentArguments, boolean premain) {
        List<ConfigurationSource> result = new ArrayList<>();

        // highest priority : JVM system properties (before adding remote configuration)

        // java system properties
        result.add(new PrefixingConfigurationSourceWrapper(new SystemPropertyConfigurationSource(), "elastic.apm."));

        // environment variables
        result.add(new PrefixingConfigurationSourceWrapper(new EnvironmentVariableConfigurationSource(), "ELASTIC_APM_"));

        if (agentArguments != null && !agentArguments.isEmpty()) {
            // runtime attachment: self-attachment API and attacher jar
            // could also be used with -javagent setup option but not expected to be common
            //
            // configuration is stored in a temporary file whose path is provided in agent arguments
            AgentArgumentsConfigurationSource agentArgs = AgentArgumentsConfigurationSource.parse(agentArguments);

            ConfigurationSource attachmentConfig = ConfigSources.fromRuntimeAttachParameters(agentArgs.getValue(TEMP_PROPERTIES_FILE_KEY));
            if (attachmentConfig != null) {
                result.add(attachmentConfig);
            }
        }

        // Optionally loading agent configuration from external file, while it depends on sources above, it has higher
        // priority and is thus inserted before them.

        String configFileLocation = CoreConfiguration.getConfigFileLocation(result, premain);
        ConfigurationSource configFileSource = ConfigSources.fromFileSystem(configFileLocation);
        if (configFileSource != null) {
            result.add(0, configFileSource);
        }

        // Mostly used as a convenience for testing when 'elasticapm.properties' is at the root of the system classpath.
        // Might also be used when application has 'elasticapm.properties' in the system classpath, however this
        // can't be guaranteed, thus the attacher API (running from any part of the application) will copy configuration
        // from the classpath to runtime attach parameters.
        ConfigurationSource classpathSource = ConfigSources.fromClasspath("elasticapm.properties", ClassLoader.getSystemClassLoader());
        if (classpathSource != null) {
            result.add(classpathSource);
        }

        // lowest priority: implicit default configuration

        return result;
    }

}
