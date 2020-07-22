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
package co.elastic.apm.agent.metrics;

import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.report.ReporterConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetricRegistryTest {

    private MetricRegistry metricRegistry;
    private ReporterConfiguration config;

    @BeforeEach
    void setUp() {
        config = mock(ReporterConfiguration.class);
        metricRegistry = new MetricRegistry(config);
    }

    @Test
    void testDisabledMetrics() {
        when(config.getDisableMetrics()).thenReturn(List.of(WildcardMatcher.valueOf("jvm.gc.*")));
        final DoubleSupplier problematicMetric = () -> {
            throw new RuntimeException("Huston, we have a problem");
        };
        metricRegistry.addUnlessNegative("jvm.gc.count", Labels.EMPTY, problematicMetric);
        metricRegistry.addUnlessNan("jvm.gc.count", Labels.EMPTY, problematicMetric);
        metricRegistry.add("jvm.gc.count", Labels.EMPTY, problematicMetric);
        metricRegistry.switchBuffersAndReport(metricSets -> assertThat(metricSets).isEmpty());
    }

    @Test
    void testReportGaugeTwice() {
        metricRegistry.add("foo", Labels.EMPTY, () -> 42);
        metricRegistry.switchBuffersAndReport(metricSets -> assertThat(metricSets.get(Labels.EMPTY).getGauge("foo").get()).isEqualTo(42));
        // the active and inactive metricSets are now switched, verify that the previous inactive metricSets also contain the same gauges
        metricRegistry.switchBuffersAndReport(metricSets -> assertThat(metricSets.get(Labels.EMPTY).getGauge("foo").get()).isEqualTo(42));
    }

    @Test
    void testTimerResetWithReporting() {
        Labels.Mutable labels = Labels.Mutable.of("foo", "bar");
        metricRegistry.updateTimer("timer", labels, 20);
        metricRegistry.updateTimer("timer", labels, 22);
        metricRegistry.switchBuffersAndReport(metricSets -> verifyTimer(metricSets.get(labels), 2, 42));
        metricRegistry.switchBuffersAndReport(null);
        // Now we get the original buffer back
        metricRegistry.switchBuffersAndReport(metricSets -> verifyTimer(metricSets.get(labels), 0, 0));
    }

    @Test
    void testTimerResetWithoutReporting() {
        Labels.Mutable labels = Labels.Mutable.of("foo", "bar");
        metricRegistry.updateTimer("timer", labels, 20);
        metricRegistry.updateTimer("timer", labels, 22);
        metricRegistry.switchBuffersAndReport(null);
        metricRegistry.switchBuffersAndReport(null);
        // Now we get the original buffer back
        metricRegistry.switchBuffersAndReport(metricSets -> verifyTimer(metricSets.get(labels), 0, 0));
    }

    private void verifyTimer(MetricSet metricSet, int expectedCount, int expectedTotalDurationUs) {
        if (expectedCount > 0) {
            assertThat(metricSet.hasContent()).isTrue();
        } else {
            assertThat(metricSet.hasContent()).isFalse();
        }
        Timer timer = metricSet.getTimers().get("timer");
        assertThat(timer.getCount()).isEqualTo(expectedCount);
        assertThat(timer.getTotalTimeUs()).isEqualTo(expectedTotalDurationUs);
    }

    @Test
    void testCounterResetWithReporting() {
        Labels.Mutable labels = Labels.Mutable.of("foo", "bar");
        metricRegistry.incrementCounter("counter", labels);
        metricRegistry.incrementCounter("counter", labels);
        metricRegistry.switchBuffersAndReport(metricSets -> verifyCounter(metricSets.get(labels), 2));
        metricRegistry.switchBuffersAndReport(null);
        // Now we get the original buffer back
        metricRegistry.switchBuffersAndReport(metricSets -> verifyCounter(metricSets.get(labels), 0));
    }

    @Test
    void testCounterResetWithoutReporting() {
        Labels.Mutable labels = Labels.Mutable.of("foo", "bar");
        metricRegistry.incrementCounter("counter", labels);
        metricRegistry.incrementCounter("counter", labels);
        metricRegistry.switchBuffersAndReport(null);
        metricRegistry.switchBuffersAndReport(null);
        // Now we get the original buffer back
        metricRegistry.switchBuffersAndReport(metricSets -> verifyCounter(metricSets.get(labels), 0));
    }

    private void verifyCounter(MetricSet metricSet, int expectedCount) {
        if (expectedCount > 0) {
            assertThat(metricSet.hasContent()).isTrue();
        } else {
            assertThat(metricSet.hasContent()).isFalse();
        }
        assertThat(metricSet.getCounters().get("counter").get()).isEqualTo(expectedCount);
    }

    @Test
    void testLimitTimers() {
        IntStream.range(1, 505).forEach(i -> metricRegistry.updateTimer("timer" + i, Labels.Mutable.of("foo", Integer.toString(i)), 1));
        IntStream.range(1, 505).forEach(i -> metricRegistry.updateTimer("timer" + i, Labels.Mutable.of("bar", Integer.toString(i)), 1));

        metricRegistry.switchBuffersAndReport(metricSets -> assertThat(metricSets).hasSize(1000));
        // the active and inactive metricSets are now switched, also check the size of the previously inactive metricSets
        metricRegistry.switchBuffersAndReport(metricSets -> assertThat(metricSets).hasSize(1000));
    }

    @Test
    void testBuffersRotationWithReport() throws ExecutionException, InterruptedException {
        final CompletableFuture<Map<? extends Labels, MetricSet>> originalMetricSets = new CompletableFuture<>();
        metricRegistry.switchBuffersAndReport(originalMetricSets::complete);

        final CompletableFuture<Map<? extends Labels, MetricSet>> firstRotationMetricSets = new CompletableFuture<>();
        metricRegistry.switchBuffersAndReport(firstRotationMetricSets::complete);
        assertThat(firstRotationMetricSets.get()).isNotSameAs(originalMetricSets.get());

        final CompletableFuture<Map<? extends Labels, MetricSet>> secondRotationMetricSets = new CompletableFuture<>();
        metricRegistry.switchBuffersAndReport(secondRotationMetricSets::complete);
        assertThat(secondRotationMetricSets.get()).isNotSameAs(firstRotationMetricSets.get());
        assertThat(secondRotationMetricSets.get()).isSameAs(originalMetricSets.get());
    }

    @Test
    void testBuffersRotationWithoutReport() throws ExecutionException, InterruptedException {
        final CompletableFuture<Map<? extends Labels, MetricSet>> originalMetricSets = new CompletableFuture<>();
        metricRegistry.switchBuffersAndReport(originalMetricSets::complete);

        metricRegistry.switchBuffersAndReport(null);

        final CompletableFuture<Map<? extends Labels, MetricSet>> firstRotationMetricSets = new CompletableFuture<>();
        metricRegistry.switchBuffersAndReport(firstRotationMetricSets::complete);
        assertThat(firstRotationMetricSets.get()).isSameAs(originalMetricSets.get());
    }
}
