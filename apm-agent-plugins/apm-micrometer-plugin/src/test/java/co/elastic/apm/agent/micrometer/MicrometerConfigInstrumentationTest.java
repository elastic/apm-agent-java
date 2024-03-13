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
import co.elastic.apm.agent.configuration.CoreConfigurationImpl;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.report.ReporterConfigurationImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class MicrometerConfigInstrumentationTest {

    private MockReporter reporter;
    private ConfigurationRegistry config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void setUp() {
        config = SpyConfiguration.createSpyConfig();
        doReturn(50L).when(config.getConfig(ReporterConfigurationImpl.class)).getMetricsIntervalMs();
        reporter = new MockReporter();
    }

    @After
    public void tearDown() {
        ElasticApmAgent.reset();
    }

    @Test
    public void testConfigMapWasUpdated() {
        ElasticApmAgent.initInstrumentation(MockTracer.createRealTracer(reporter, config), ByteBuddyAgent.install());
        MicrometerMetricsReporter.OneSecondStepSimpleConfig oneSecondStepSimpleConfig = new MicrometerMetricsReporter.OneSecondStepSimpleConfig();
        SimpleMeterRegistry registryOneSecondStep = new SimpleMeterRegistry(oneSecondStepSimpleConfig, Clock.SYSTEM);
        registryOneSecondStep.counter("bar").increment();

        reporter.awaitUntilAsserted(15000, () ->
            assertThat(getSpecialCounterValue()).isEqualTo(oneSecondStepSimpleConfig.hashCode()));
    }

    @Test
    public void testConfigMapWasUpdatedWhenInstrumentConfigDisabled() {
        doReturn(false).when(config.getConfig(CoreConfigurationImpl.class)).isInstrument();
        ElasticApmAgent.initInstrumentation(MockTracer.createRealTracer(reporter, config), ByteBuddyAgent.install());
        MicrometerMetricsReporter.OneSecondStepSimpleConfig oneSecondStepSimpleConfig = new MicrometerMetricsReporter.OneSecondStepSimpleConfig();
        SimpleMeterRegistry registryOneSecondStep = new SimpleMeterRegistry(oneSecondStepSimpleConfig, Clock.SYSTEM);
        registryOneSecondStep.counter("bar").increment();

        reporter.awaitUntilAsserted(15000, () ->
            assertThat(getSpecialCounterValue()).isEqualTo(oneSecondStepSimpleConfig.hashCode()));
    }

    private Integer getSpecialCounterValue() {
        int count = 0;
        for (JsonNode metricSet : getMetricSets()) {
            JsonNode metricsetNode = metricSet.get("metricset");
            if (metricsetNode != null) {
                JsonNode samples = metricsetNode.get("samples");
                if (samples != null) {
                    //this is set by the instrumentation
                    JsonNode special = samples.get("MicrometerMetricsReporter_OneSecondStepSimpleConfig");
                    if (special != null) {
                        return special.get("value").asInt();
                    }
                }
            }
            count++;
        }
        return null;
    }

    private List<JsonNode> getMetricSets() {
        List<JsonNode> metricSets = reporter.getBytes()
            .stream()
            .map(k -> new String(k, StandardCharsets.UTF_8))
            .flatMap(s -> Arrays.stream(s.split("\n")))
            .map(this::deserialize)
            .collect(Collectors.toList());
        reporter.reset();
        return metricSets;
    }

    private JsonNode deserialize(String json) {
        System.out.println(json);
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
