/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.impl;

import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.configuration.PrefixingConfigurationSourceWrapper;
import co.elastic.apm.context.LifecycleListener;
import co.elastic.apm.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.impl.stacktrace.StacktraceFactory;
import co.elastic.apm.report.Reporter;
import co.elastic.apm.report.ReporterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.configuration.source.AbstractConfigurationSource;
import org.stagemonitor.configuration.source.ConfigurationSource;
import org.stagemonitor.configuration.source.EnvironmentVariableConfigurationSource;
import org.stagemonitor.configuration.source.PropertyFileConfigurationSource;
import org.stagemonitor.configuration.source.SimpleSource;
import org.stagemonitor.configuration.source.SystemPropertyConfigurationSource;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;

public class ElasticApmTracerBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ElasticApmTracerBuilder.class);

    @Nullable
    private ConfigurationRegistry configurationRegistry;
    @Nullable
    private Reporter reporter;
    @Nullable
    private StacktraceFactory stacktraceFactory;
    @Nullable
    private Iterable<LifecycleListener> lifecycleListeners;
    private Map<String, String> inlineConfig = new HashMap<>();

    public ElasticApmTracerBuilder configurationRegistry(ConfigurationRegistry configurationRegistry) {
        this.configurationRegistry = configurationRegistry;
        return this;
    }

    public ElasticApmTracerBuilder reporter(Reporter reporter) {
        this.reporter = reporter;
        return this;
    }

    public ElasticApmTracerBuilder stacktraceFactory(StacktraceFactory stacktraceFactory) {
        this.stacktraceFactory = stacktraceFactory;
        return this;
    }

    public ElasticApmTracerBuilder lifecycleListeners(List<LifecycleListener> lifecycleListeners) {
        this.lifecycleListeners = lifecycleListeners;
        return this;
    }

    public ElasticApmTracerBuilder withConfig(String key, String value) {
        inlineConfig.put(key, value);
        return this;
    }

    public ElasticApmTracer build() {
        if (configurationRegistry == null) {
            configurationRegistry = getDefaultConfigurationRegistry();
        }
        if (reporter == null) {
            reporter = new ReporterFactory().createReporter(configurationRegistry, null, null);
        }
        if (stacktraceFactory == null) {
            StacktraceConfiguration stackConfig = configurationRegistry.getConfig(StacktraceConfiguration.class);
            stacktraceFactory = new StacktraceFactory.CurrentThreadStackTraceFactory(stackConfig);
        }
        if (lifecycleListeners == null) {
            lifecycleListeners = ServiceLoader.load(LifecycleListener.class, getClass().getClassLoader());
        }
        return new ElasticApmTracer(configurationRegistry, reporter, stacktraceFactory, lifecycleListeners);
    }

    private ConfigurationRegistry getDefaultConfigurationRegistry() {
        try {
            final ConfigurationRegistry configurationRegistry = ConfigurationRegistry.builder()
                .configSources(getConfigSources())
                .optionProviders(ServiceLoader.load(ConfigurationOptionProvider.class, ElasticApmTracer.class.getClassLoader()))
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
                .optionProviders(ServiceLoader.load(ConfigurationOptionProvider.class, ElasticApmTracer.class.getClassLoader()))
                .build();
        }
    }

    private List<ConfigurationSource> getConfigSources() {
        List<ConfigurationSource> result = new ArrayList<>();
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
        if (PropertyFileConfigurationSource.isPresent("elasticapm.properties")) {
            result.add(new PropertyFileConfigurationSource("elasticapm.properties"));
        }
        return result;
    }

}
