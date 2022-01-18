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
package co.elastic.apm.agent.configuration;

import co.elastic.apm.agent.configuration.converter.TimeDuration;
import co.elastic.apm.agent.context.AbstractLifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.util.VersionUtils;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * Logs system information and configuration on startup.
 * <p>
 * Based on {@code org.stagemonitor.core.Stagemonitor} and {@code org.stagemonitor.core.configuration.ConfigurationLogger},
 * under Apache license 2.0.
 * </p>
 */
public class StartupInfo extends AbstractLifecycleListener {

    private static final Logger logger = LoggerFactory.getLogger(StartupInfo.class);
    private final String elasticApmVersion;

    public StartupInfo() {
        elasticApmVersion = VersionUtils.getAgentVersion();
    }

    private static String getJvmAndOsVersionString() {
        return "Java " + System.getProperty("java.version") +
            " Runtime version: "+ System.getProperty("java.runtime.version") +
            " VM version: "+ System.getProperty("java.vm.version") +
            " (" + System.getProperty("java.vendor") + ") " +
            System.getProperty("os.name") + " " + System.getProperty("os.version");
    }

    @Override
    public void init(ElasticApmTracer tracer) {
        ConfigurationRegistry configurationRegistry = tracer.getConfigurationRegistry();
        logConfiguration(configurationRegistry, logger);
    }

    void logConfiguration(ConfigurationRegistry configurationRegistry, Logger logger) {
        final String serviceName = configurationRegistry.getConfig(CoreConfiguration.class).getServiceName();
        final String serviceVersion = configurationRegistry.getConfig(CoreConfiguration.class).getServiceVersion();

        StringBuilder serviceNameAndVersion = new StringBuilder(serviceName);
        if (serviceVersion != null) {
            serviceNameAndVersion.append(" (").append(serviceVersion).append(")");
        }

        logger.info("Starting Elastic APM {} as {} on {}",
            elasticApmVersion,
            serviceNameAndVersion,
            getJvmAndOsVersionString());
        logger.debug("VM Arguments: {}", ManagementFactory.getRuntimeMXBean().getInputArguments());
        for (List<ConfigurationOption<?>> options : configurationRegistry.getConfigurationOptionsByCategory().values()) {
            for (ConfigurationOption<?> option : options) {
                if (!option.isDefault()) {
                    logConfigWithNonDefaultValue(logger, option);
                }
            }
        }
        if (configurationRegistry.getConfig(StacktraceConfiguration.class).getApplicationPackages().isEmpty()) {
            logger.warn("To enable all features and decrease startup time, please configure {}",
                StacktraceConfiguration.APPLICATION_PACKAGES);
        }
    }

    private void logConfigWithNonDefaultValue(Logger logger, ConfigurationOption<?> option) {
        logger.info("{}: '{}' (source: {})", option.getKey(),
            option.isSensitive() ? "XXXX" : option.getValueAsSafeString(),
            option.getNameOfCurrentConfigurationSource());

        if (option.getTags().contains("deprecated")) {
            logger.warn("Detected usage of deprecated configuration option '{}'. " +
                "This option might be removed in the future. " +
                "Please refer to the documentation about alternatives.", option.getKey());
        }
        if (!option.getKey().equals(option.getUsedKey())) {
            logger.warn("Detected usage of an old configuration key: '{}'. Please use '{}' instead.",
                option.getUsedKey(), option.getKey());
        }
        if (option.getValue() instanceof TimeDuration && !TimeDuration.DURATION_PATTERN.matcher(option.getValueAsString()).matches()) {
            logger.warn("DEPRECATION WARNING: {}: '{}' (source: {}) is not using a time unit. Please use one of 'ms', 's' or 'm'.",
                option.getKey(),
                option.getValueAsString(),
                option.getNameOfCurrentConfigurationSource());
        }
    }
}
