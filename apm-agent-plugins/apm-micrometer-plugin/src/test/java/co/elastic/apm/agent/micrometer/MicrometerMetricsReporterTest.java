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
package co.elastic.apm.agent.micrometer;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.report.ReporterConfiguration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.CountingMode;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;


class MicrometerMetricsReporterTest {

    private MeterRegistry meterRegistry;
    private MicrometerMetricsReporter metricsReporter;
    private MockReporter reporter;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        SimpleMeterRegistry simpleMeterRegistry = new SimpleMeterRegistry();
        CompositeMeterRegistry nestedCompositeMeterRegistry = new CompositeMeterRegistry(Clock.SYSTEM, List.of(simpleMeterRegistry));
        meterRegistry = new CompositeMeterRegistry(Clock.SYSTEM, List.of(nestedCompositeMeterRegistry));
        reporter = new MockReporter();
        ElasticApmTracer tracer = MockTracer.createRealTracer(reporter);
        doReturn(0L).when(tracer.getConfig(ReporterConfiguration.class)).getMetricsIntervalMs();
        metricsReporter = new MicrometerMetricsReporter(tracer);
        metricsReporter.registerMeterRegistry(meterRegistry);
        metricsReporter.registerMeterRegistry(nestedCompositeMeterRegistry);
        metricsReporter.registerMeterRegistry(simpleMeterRegistry);
        assertThat(metricsReporter.getMeterRegistries()).doesNotContain(nestedCompositeMeterRegistry);
        assertThat(metricsReporter.getMeterRegistries()).doesNotContain(meterRegistry);
        assertThat(metricsReporter.getMeterRegistries()).contains(simpleMeterRegistry);
    }

    @Test
    void testSameMetricSet() {
        meterRegistry.counter("counter", List.of(Tag.of("foo", "bar"))).increment(42);
        meterRegistry.gauge("gauge", List.of(Tag.of("foo", "bar")), 42, v -> 42);

        JsonNode metricSet = getSingleMetricSet();
        assertThat(metricSet.get("metricset").get("tags").get("foo").textValue()).isEqualTo("bar");
        assertThat(metricSet.get("metricset").get("samples").get("counter").get("value").doubleValue()).isEqualTo(42);
        assertThat(metricSet.get("metricset").get("samples").get("gauge").get("value").doubleValue()).isEqualTo(42);
    }

    @Test
    void testDifferentMetricSets() {
        meterRegistry.counter("counter", List.of(Tag.of("foo", "bar"))).increment(42);
        meterRegistry.gauge("gauge", List.of(Tag.of("baz", "qux")), 42, v -> 42);

        List<JsonNode> metricSet = getMetricSets();
        assertThat(metricSet).hasSize(2);
    }

    @Test
    void testCounter() {
        meterRegistry.counter("counter", List.of(Tag.of("foo", "bar"), Tag.of("baz", "qux"))).increment(42);

        JsonNode metricSet = getSingleMetricSet();
        assertThat(metricSet.get("metricset").get("tags").get("foo").textValue()).isEqualTo("bar");
        assertThat(metricSet.get("metricset").get("tags").get("baz").textValue()).isEqualTo("qux");
        assertThat(metricSet.get("metricset").get("samples").get("counter").get("value").doubleValue()).isEqualTo(42);
    }

    @Test
    void testFunctionCounter() {
        FunctionCounter.builder("counter", 42, i -> i).tag("foo", "bar").register(meterRegistry);

        JsonNode metricSet = getSingleMetricSet();
        assertThat(metricSet.get("metricset").get("tags").get("foo").textValue()).isEqualTo("bar");
        assertThat(metricSet.get("metricset").get("samples").get("counter").get("value").doubleValue()).isEqualTo(42);
    }

    @Test
    void testCounterReset() {
        MockClock clock = new MockClock();
        meterRegistry = new SimpleMeterRegistry(new SimpleConfig() {

            @Override
            public CountingMode mode() {
                return CountingMode.STEP;
            }

            @Override
            public Duration step() {
                return Duration.ofSeconds(30);
            }

            @Override
            public String get(@Nonnull String key) {
                return null;
            }
        }, clock);
        metricsReporter.registerMeterRegistry(meterRegistry);
        meterRegistry.counter("counter").increment();

        clock.addSeconds(30);
        assertThat(getSingleMetricSet().get("metricset").get("samples").get("counter").get("value").doubleValue()).isEqualTo(1);

        clock.addSeconds(30);
        assertThat(getSingleMetricSet().get("metricset").get("samples").get("counter").get("value").doubleValue()).isEqualTo(0);
    }

    @Test
    void testGauge() {
        meterRegistry.gauge("gauge", List.of(Tag.of("foo", "bar")), 42, v -> 42);

        JsonNode metricSet = getSingleMetricSet();
        assertThat(metricSet.get("metricset").get("tags").get("foo").textValue()).isEqualTo("bar");
        assertThat(metricSet.get("metricset").get("samples").get("gauge").get("value").doubleValue()).isEqualTo(42);
    }

    @Test
    void testTimer() {
        Timer timer = Timer.builder("timer").tag("foo", "bar").register(meterRegistry);
        timer.record(1, TimeUnit.MICROSECONDS);
        timer.record(2, TimeUnit.MICROSECONDS);

        JsonNode metricSet = getSingleMetricSet();
        assertThat(metricSet.get("metricset").get("tags").get("foo").textValue()).isEqualTo("bar");
        assertThat(metricSet.get("metricset").get("samples").get("timer.count").get("value").intValue()).isEqualTo(2);
        assertThat(metricSet.get("metricset").get("samples").get("timer.sum.us").get("value").longValue()).isEqualTo(3);
    }

    @Test
    void testFunctionTimer() {
        FunctionTimer.builder("timer", 42, i -> i, i -> i, TimeUnit.MICROSECONDS)
            .tag("foo", "bar")
            .register(meterRegistry);

        JsonNode metricSet = getSingleMetricSet();
        assertThat(metricSet.get("metricset").get("tags").get("foo").textValue()).isEqualTo("bar");
        assertThat(metricSet.get("metricset").get("samples").get("timer.count").get("value").intValue()).isEqualTo(42);
        assertThat(metricSet.get("metricset").get("samples").get("timer.sum.us").get("value").longValue()).isEqualTo(42);

    }

    @Test
    void testLongTaskTimer() {
        LongTaskTimer timer = LongTaskTimer.builder("timer").tag("foo", "bar").register(meterRegistry);
        timer.start();

        JsonNode metricSet = getSingleMetricSet();
        assertThat(metricSet.get("metricset").get("tags").get("foo").textValue()).isEqualTo("bar");
        assertThat(metricSet.get("metricset").get("samples").get("timer.count").get("value").intValue()).isEqualTo(1);
        assertThat(metricSet.get("metricset").get("samples").get("timer.sum.us").get("value").longValue()).isNotNegative();
    }

    @Test
    void testDistributionSummary() {
        DistributionSummary timer = DistributionSummary.builder("timer").tag("foo", "bar").register(meterRegistry);
        timer.record(1);
        timer.record(2);


        JsonNode metricSet = getSingleMetricSet();
        assertThat(metricSet.get("metricset").get("tags").get("foo").textValue()).isEqualTo("bar");
        assertThat(metricSet.get("metricset").get("samples").get("timer.count").get("value").intValue()).isEqualTo(2);
        assertThat(metricSet.get("metricset").get("samples").get("timer.sum").get("value").longValue()).isEqualTo(3);
    }

    private JsonNode getSingleMetricSet() {
        List<JsonNode> metricSets = getMetricSets();
        assertThat(metricSets).hasSize(1);
        return metricSets.get(0);
    }

    private List<JsonNode> getMetricSets() {
        metricsReporter.run();
        List<JsonNode> metricSets = reporter.getBytes()
            .stream()
            .map(String::new)
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
