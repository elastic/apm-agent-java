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
package co.elastic.apm.agent.impl.circuitbreaker;

import co.elastic.apm.agent.configuration.converter.TimeDuration;
import co.elastic.apm.agent.configuration.converter.TimeDurationValueConverter;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;

import static co.elastic.apm.agent.configuration.validation.RangeValidator.isNotInRange;

public class CircuitBreakerConfiguration extends ConfigurationOptionProvider {
    public static final String CIRCUIT_BREAKER_CATEGORY = "Circuit-Breaker";

    private final ConfigurationOption<Boolean> circuitBreakerEnabled = ConfigurationOption.booleanOption()
        .key("circuit_breaker_enabled")
        .tags("added[1.14.0]")
        .configurationCategory(CIRCUIT_BREAKER_CATEGORY)
        .description("A boolean specifying whether the circuit breaker should be enabled or not. \n" +
            "When enabled, the agent periodically polls stress monitors to detect system/process/JVM stress state. \n" +
            "If ANY of the monitors detects a stress indication, the agent will become inactive, as if the \n" +
            "<<config-active>> configuration option has been set to `false`, thus reducing resource consumption to a minimum. \n" +
            "When inactive, the agent continues polling the same monitors in order to detect whether the stress state \n" +
            "has been relieved. If ALL monitors approve that the system/process/JVM is not under stress anymore, the \n" +
            "agent will resume and become fully functional.")
        .dynamic(true)
        .buildWithDefault(false);

    private final ConfigurationOption<TimeDuration> stressMonitoringInterval = TimeDurationValueConverter.durationOption("s")
        .key("stress_monitoring_interval")
        .tags("added[1.14.0]")
        .configurationCategory(CIRCUIT_BREAKER_CATEGORY)
        .description("The interval at which the agent polls the stress monitors. Must be at least `1s`.")
        .addValidator(isNotInRange(TimeDuration.of("0ms"), TimeDuration.of("999ms")))
        .buildWithDefault(TimeDuration.of("5s"));

    public boolean isCircuitBreakerEnabled() {
        return circuitBreakerEnabled.get();
    }

    public long getStressMonitoringPollingInterval() {
        return stressMonitoringInterval.get().getMillis();
    }
}
