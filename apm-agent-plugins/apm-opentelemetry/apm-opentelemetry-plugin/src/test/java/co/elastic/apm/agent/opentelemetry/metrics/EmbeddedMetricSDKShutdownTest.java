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
package co.elastic.apm.agent.opentelemetry.metrics;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.opentelemetry.OtelTestUtils;
import co.elastic.apm.agent.report.ReporterConfiguration;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.AfterClass;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThatMetricSets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class EmbeddedMetricSDKShutdownTest {

    @Test
    public void verifyMetricsFlushedOnAgentShutdown() {
        OtelTestUtils.resetElasticOpenTelemetry();
        OtelTestUtils.clearGlobalOpenTelemetry();

        MockTracer.MockInstrumentationSetup mockInstrumentationSetup = MockTracer.createMockInstrumentationSetup();
        ElasticApmTracer tracer = mockInstrumentationSetup.getTracer();
        MockReporter reporter = mockInstrumentationSetup.getReporter();

        ReporterConfiguration reporterConfig = mockInstrumentationSetup.getConfig().getConfig(ReporterConfiguration.class);
        doReturn(1_000_000L).when(reporterConfig).getMetricsIntervalMs();
        assertThat(tracer.isRunning()).isTrue();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        Meter meter = GlobalOpenTelemetry.getMeter("test");
        LongCounter counter = meter.counterBuilder("my_counter").build();

        counter.add(42);

        tracer.stop();

        assertThatMetricSets(reporter.getBytes())
            .hasMetricsetWithLabelsSatisfying("otel_instrumentation_scope_name", "test", metrics ->
                metrics
                    .containsValueMetric("my_counter", 42)
                    .hasMetricsCount(1)
            );
    }

    @AfterAll
    @AfterClass
    public static synchronized void afterAll() {
        ElasticApmAgent.reset();
    }
}
