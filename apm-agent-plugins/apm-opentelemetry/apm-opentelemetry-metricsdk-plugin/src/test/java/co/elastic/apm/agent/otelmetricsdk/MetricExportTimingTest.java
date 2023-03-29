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
package co.elastic.apm.agent.otelmetricsdk;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.report.ReporterConfiguration;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class MetricExportTimingTest extends AbstractInstrumentationTest {

    /**
     * This test has been separated from the other because we don't want it to run in the integration tests: It would be too slow.
     */
    @Test
    void testMetricExportIntervalRespected() throws Exception {
        ReporterConfiguration reporterConfig = tracer.getConfig(ReporterConfiguration.class);
        doReturn(50L).when(reporterConfig).getMetricsIntervalMs();

        try (SdkMeterProvider meterProvider = SdkMeterProvider.builder().build()) {
            Meter meter = meterProvider.meterBuilder("test").build();

            meter.gaugeBuilder("my_gauge").buildWithCallback(obs -> obs.record(42.0));

            Thread.sleep(1000);

            //To account for CI slowness, we try to check that the number of exports is just in the correct ballpark
            assertThat(reporter.getBytes().size())
                .isBetween(10, 21);

        }

    }
}
