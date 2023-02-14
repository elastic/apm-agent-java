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

import co.elastic.apm.agent.impl.Telemetry;
import co.elastic.apm.agent.report.Reporter;
import co.elastic.apm.agent.report.ReporterConfiguration;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigurationTelemetry {

    @Nullable
    private Reporter reporter;
    @Nullable
    private ReporterConfiguration config;
    @Nullable
    private volatile ConfigurationRegistry registry;

    private final ConcurrentHashMap<String, String> previousValues = new ConcurrentHashMap<>();

    public void init(Reporter reporter, ReporterConfiguration config, ConfigurationRegistry registry) {
        this.reporter = reporter;
        this.config = config;
        // must be last to provide visibility to other attributes
        this.registry = registry;


    }

    public void report() {
        if (reporter == null || config == null || registry == null) {
            return;
        }
        if (!config.isAgentTelemetryEnabled()) {
            return;
        }

        Telemetry telemetry = Telemetry.pooledInstance();

        Set<String> keys = new HashSet<>(previousValues.keySet());

        Map<String, ConfigurationOption<?>> configMap = registry.getConfigurationOptionsByKey();
        for (Map.Entry<String, ConfigurationOption<?>> entry : configMap.entrySet()) {

            String key = entry.getKey();
            ConfigurationOption<?> value = entry.getValue();
            if (!value.isDefault()) {
                keys.remove(key);
                String stringValue = value.isSensitive() ? "XXX" : value.getValueAsString();

                String previousValue = previousValues.put(key, stringValue);
                if (!stringValue.equals(previousValue)) {
                    telemetry.withEffectiveConfig(key, stringValue);
                }
            }
        }
        // remove stale keys from previous
        for (String key : keys) {
            previousValues.remove(key);
        }


        reporter.reportTelemetry(telemetry);

    }
}
