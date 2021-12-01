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
package co.elastic.apm.agent.micrometer;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.report.ReporterConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class MicrometerInstrumentationTest {

    private MockReporter reporter;
    private ConfigurationRegistry config;
    private int lastMeasuredMetricSetNumber;
    private int lastFooSamples;

    @Before
    public void setUp() {
        config = SpyConfiguration.createSpyConfig();
        doReturn(50L).when(config.getConfig(ReporterConfiguration.class)).getMetricsIntervalMs();
        reporter = new MockReporter();
        lastMeasuredMetricSetNumber = 0;
        lastFooSamples = 0;
    }

    @After
    public void tearDown() {
        ElasticApmAgent.reset();
    }

    @Test
    public void testRegisterMeterRegistry() {
        ElasticApmAgent.initInstrumentation(MockTracer.createRealTracer(reporter, config), ByteBuddyAgent.install());
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.counter("foo").increment();
        reporter.awaitUntilAsserted(() -> assertThat(countFooSamples()).isGreaterThanOrEqualTo(1));
    }

    @Test
    public void testReportedWhenInstrumentConfigDisabled() {
        doReturn(false).when(config.getConfig(CoreConfiguration.class)).isInstrument();
        ElasticApmAgent.initInstrumentation(MockTracer.createRealTracer(reporter, config), ByteBuddyAgent.install());
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.counter("foo").increment();
        reporter.awaitUntilAsserted(3000, () -> assertThat(countFooSamples()).isGreaterThanOrEqualTo(1));
    }

    private int countFooSamples() {
        final ObjectMapper objectMapper = new ObjectMapper();
        List<byte[]> metricSets = reporter.getBytes();
        if (metricSets.size() > lastMeasuredMetricSetNumber) {
            lastMeasuredMetricSetNumber = metricSets.size();
            lastFooSamples = (int) metricSets.stream().filter((serializedMetricSet) -> {
                try {
                    boolean isFooMetric = false;
                    JsonNode metricSetTree = objectMapper.readTree(serializedMetricSet);
                    JsonNode metricSet = metricSetTree.get("metricset");
                    if (metricSet != null) {
                        JsonNode samples = metricSet.get("samples");
                        if (samples != null) {
                            isFooMetric = samples.get("foo") != null;
                        }
                    }
                    return isFooMetric;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return true;
            }).count();
        }
        return lastFooSamples;
    }
}
