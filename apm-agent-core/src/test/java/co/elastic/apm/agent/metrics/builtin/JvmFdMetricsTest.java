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
package co.elastic.apm.agent.metrics.builtin;

import co.elastic.apm.agent.configuration.MetricsConfigurationImpl;
import co.elastic.apm.agent.report.ReporterConfigurationImpl;
import co.elastic.apm.agent.tracer.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricRegistry;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class JvmFdMetricsTest {

    private final JvmFdMetrics jvmFdMetrics = new JvmFdMetrics();


    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "jvm.fd.used",
        "jvm.fd.max"
    })
    @DisabledOnOs(OS.WINDOWS)
    void testMetrics(String metric) {
        MetricRegistry registry = new MetricRegistry(mock(ReporterConfigurationImpl.class), spy(MetricsConfigurationImpl.class));
        jvmFdMetrics.bindTo(registry);

        assertThat(registry.getGaugeValue(metric, Labels.EMPTY))
            .describedAs("metric '%s'", metric)
            .isNotNaN()
            .isPositive();

    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "jvm.fd.used",
        "jvm.fd.max"
    })
    @EnabledOnOs(OS.WINDOWS)
    void testMetricsDisabledOnWindows(String metric) {
        MetricRegistry registry = new MetricRegistry(mock(ReporterConfigurationImpl.class), spy(MetricsConfigurationImpl.class));
        jvmFdMetrics.bindTo(registry);

        assertThat(registry.getGauge(metric, Labels.EMPTY)).isNull();
    }

}
