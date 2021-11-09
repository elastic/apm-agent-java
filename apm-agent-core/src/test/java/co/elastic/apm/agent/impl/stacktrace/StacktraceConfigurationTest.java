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
package co.elastic.apm.agent.impl.stacktrace;

import co.elastic.apm.agent.configuration.SpyConfiguration;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.configuration.source.SimpleSource;

import static org.assertj.core.api.Assertions.assertThat;

class StacktraceConfigurationTest {

    @Test
    void testGetSpanStackTraceMinDurationMs_whenSpanStackTraceMinDurationNonDefault_thenGetValueFromSpanStackTraceMinDuration() {
        ConfigurationRegistry configRegistry = SpyConfiguration.createSpyConfig(SimpleSource.forTest("span_frames_min_duration", "10ms").add("span_stack_trace_min_duration", "15ms"));

        long actual = configRegistry.getConfig(StacktraceConfiguration.class).getSpanStackTraceMinDurationMs();
        assertThat(actual).isEqualTo(15);
    }

    @Test
    void testGetSpanStackTraceMinDurationMs_whenSpanStackTraceDefault_whenSpanFramesNonDefault_thenGetValueFromSpanFramesMinDuration() {
        ConfigurationRegistry configRegistry = SpyConfiguration.createSpyConfig(SimpleSource.forTest("span_frames_min_duration", "15ms"));

        long actual = configRegistry.getConfig(StacktraceConfiguration.class).getSpanStackTraceMinDurationMs();
        assertThat(actual).isEqualTo(15);
    }

    @Test
    void testGetSpanStackTraceMinDurationMs_whenSpanStackTraceDefault_whenSpanFramesMinDurationIsZero_thenGetSwappedValueOfSpanFramesMinDuration() {
        ConfigurationRegistry configRegistry = SpyConfiguration.createSpyConfig(SimpleSource.forTest("span_frames_min_duration", "0ms"));

        long actual = configRegistry.getConfig(StacktraceConfiguration.class).getSpanStackTraceMinDurationMs();
        assertThat(actual).isEqualTo(-1);
    }

    @Test
    void testGetSpanStackTraceMinDurationMs_whenSpanStackTraceDefault_whenSpanFramesMinDurationIsNegative_thenGetSwappedValueOfSpanFramesMinDuration() {
        ConfigurationRegistry configRegistry = SpyConfiguration.createSpyConfig(SimpleSource.forTest("span_frames_min_duration", "-1ms"));

        long actual = configRegistry.getConfig(StacktraceConfiguration.class).getSpanStackTraceMinDurationMs();
        assertThat(actual).isEqualTo(0);
    }

    @Test
    void testGetSpanStackTraceMinDurationMs_defaultValue() {
        ConfigurationRegistry configRegistry = SpyConfiguration.createSpyConfig();

        long actual = configRegistry.getConfig(StacktraceConfiguration.class).getSpanStackTraceMinDurationMs();
        assertThat(actual).isEqualTo(5);
    }
}
