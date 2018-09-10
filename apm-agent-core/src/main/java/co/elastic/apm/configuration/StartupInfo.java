/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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
package co.elastic.apm.configuration;

import co.elastic.apm.context.LifecycleListener;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.util.VersionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.List;

/**
 * Logs system information and configuration on startup.
 * <p>
 * Based on {@code org.stagemonitor.core.Stagemonitor} and {@code org.stagemonitor.core.configuration.ConfigurationLogger},
 * under Apache license 2.0.
 * </p>
 */
public class StartupInfo implements LifecycleListener {

    private static final Logger logger = LoggerFactory.getLogger(StartupInfo.class);
    private final String elasticApmVersion;

    public StartupInfo() {
        final String version = VersionUtils.getVersionFromPomProperties(getClass(), "co.elastic.apm", "elastic-apm-agent");
        if (version != null) {
            elasticApmVersion = version;
        } else {
            elasticApmVersion = "(unknown version)";
        }
    }

    private static String getJvmAndOsVersionString() {
        return "Java " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ") " +
            System.getProperty("os.name") + " " + System.getProperty("os.version");
    }

    @Override
    public void start(ElasticApmTracer tracer) {
        ConfigurationRegistry configurationRegistry = tracer.getConfigurationRegistry();
        logConfiguration(configurationRegistry, logger);
    }

    void logConfiguration(ConfigurationRegistry configurationRegistry, Logger logger) {
        final String serviceName = configurationRegistry.getConfig(CoreConfiguration.class).getServiceName();
        logger.info("Starting Elastic APM {} as {} on {}", elasticApmVersion, serviceName, getJvmAndOsVersionString());
        for (List<ConfigurationOption<?>> options : configurationRegistry.getConfigurationOptionsByCategory().values()) {
            for (ConfigurationOption<?> option : options) {
                if (!option.isDefault()) {
                    logConfigWithNonDefaultValue(logger, option);
                }
            }
        }
    }

    private void logConfigWithNonDefaultValue(Logger logger, ConfigurationOption<?> option) {
        logger.debug("{}: '{}' (source: {})", option.getKey(),
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
    }

    @Override
    public void stop() throws Exception {
        // noop
    }
}
