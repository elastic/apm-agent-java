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

import static co.elastic.apm.agent.configuration.validation.RangeValidator.isInRange;
import static co.elastic.apm.agent.configuration.validation.RangeValidator.isNotInRange;

public class CircuitBreakerConfiguration extends ConfigurationOptionProvider {
    public static final String CIRCUIT_BREAKER_CATEGORY = "Circuit-Breaker";

    private final ConfigurationOption<Boolean> circuitBreakerEnabled = ConfigurationOption.booleanOption()
        .key("circuit_breaker_enabled")
        .tags("added[1.14.0]")
        .tags("performance")
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
        .tags("performance")
        .configurationCategory(CIRCUIT_BREAKER_CATEGORY)
        .description("The interval at which the agent polls the stress monitors. Must be at least `1s`.")
        .addValidator(isNotInRange(TimeDuration.of("0ms"), TimeDuration.of("999ms")))
        .dynamic(false)
        .buildWithDefault(TimeDuration.of("5s"));

    private final ConfigurationOption<Double> gcStressThreshold = ConfigurationOption.doubleOption()
        .key("stress_monitor_gc_stress_threshold")
        .configurationCategory(CIRCUIT_BREAKER_CATEGORY)
        .tags("added[1.14.0]")
        .tags("performance")
        .description("The threshold used by the GC monitor to rely on for identifying heap stress.\n" +
            "The same threshold will be used for all heap pools, so that if ANY has a usage percentage that crosses it, \n" +
            "the agent will consider it as a heap stress. The GC monitor relies only on memory consumption measured \n" +
            "after a recent GC.")
        .dynamic(true)
        .addValidator(isInRange(0d, 1d))
        .buildWithDefault(0.95);

    private final ConfigurationOption<Double> gcReliefThreshold = ConfigurationOption.doubleOption()
        .key("stress_monitor_gc_relief_threshold")
        .configurationCategory(CIRCUIT_BREAKER_CATEGORY)
        .tags("added[1.14.0]")
        .tags("performance")
        .description("The threshold used by the GC monitor to rely on for identifying when the heap is not under stress .\n" +
            "If `stress_monitor_gc_stress_threshold` has been crossed, the agent will consider it a heap-stress state. \n" +
            "In order to determine that the stress state is over, percentage of occupied memory in ALL heap pools should \n" +
            "be lower than this threshold. The GC monitor relies only on memory consumption measured after a recent GC.")
        .dynamic(true)
        .addValidator(isInRange(0d, 1d))
        .buildWithDefault(0.75);

    private final ConfigurationOption<TimeDuration> cpuStressDurationThreshold = TimeDurationValueConverter.durationOption("m")
        .key("stress_monitor_cpu_duration_threshold")
        .configurationCategory(CIRCUIT_BREAKER_CATEGORY)
        .tags("added[1.14.0]")
        .tags("performance")
        .description("The minimal time required in order to determine whether the system is \n" +
            "either currently under stress, or that the stress detected previously has been relieved. \n" +
            "All measurements during this time must be consistent in comparison to the relevant threshold in \n" +
            "order to detect a change of stress state. Must be at least `1m`.")
        .addValidator(isNotInRange(TimeDuration.of("0ms"), TimeDuration.of("59s")))
        .dynamic(true)
        .buildWithDefault(TimeDuration.of("1m"));

    private final ConfigurationOption<Double> systemCpuStressThreshold = ConfigurationOption.doubleOption()
        .key("stress_monitor_system_cpu_stress_threshold")
        .configurationCategory(CIRCUIT_BREAKER_CATEGORY)
        .tags("added[1.14.0]")
        .tags("performance")
        .description("The threshold used by the system CPU monitor to detect system CPU stress. \n" +
            "If the system CPU crosses this threshold at least `stress_monitor_cpu_num_measurements` consecutive \n" +
            "measurements, the monitor considers this as a stress state.")
        .dynamic(true)
        .addValidator(isInRange(0d, 1d))
        .buildWithDefault(0.95);

    private final ConfigurationOption<Double> systemCpuReliefThreshold = ConfigurationOption.doubleOption()
        .key("stress_monitor_system_cpu_relief_threshold")
        .configurationCategory(CIRCUIT_BREAKER_CATEGORY)
        .tags("added[1.14.0]")
        .tags("performance")
        .description("The threshold used by the system CPU monitor to determine that the system is \n" +
            "not under CPU stress. If the monitor detected a CPU stress, the measured system CPU needs to be below \n" +
            "this threshold at least `stress_monitor_cpu_num_measurements` consecutive times in order for the \n" +
            "monitor to decide that the CPU stress has been relieved.")
        .dynamic(true)
        .addValidator(isInRange(0d, 1d))
        .buildWithDefault(0.80);

    public boolean isCircuitBreakerEnabled() {
        return circuitBreakerEnabled.get();
    }

    public long getStressMonitoringPollingIntervalMillis() {
        return stressMonitoringInterval.get().getMillis();
    }

    public double getGcStressThreshold() {
        return gcStressThreshold.get();
    }

    public double getGcReliefThreshold() {
        return gcReliefThreshold.get();
    }

    public long getCpuStressDurationThresholdMillis() {
        return cpuStressDurationThreshold.get().getMillis();
    }

    public double getSystemCpuStressThreshold() {
        return systemCpuStressThreshold.get();
    }

    public double getSystemCpuReliefThreshold() {
        return systemCpuReliefThreshold.get();
    }
}
