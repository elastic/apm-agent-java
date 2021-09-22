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

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.report.ReporterConfiguration;
import org.assertj.core.api.AbstractDoubleAssert;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class JvmMemoryMetricsTest {

    private final JvmMemoryMetrics jvmMemoryMetrics = new JvmMemoryMetrics();

    @Test
    void testMetrics() {
        final MetricRegistry registry = new MetricRegistry(spy(CoreConfiguration.class), mock(ReporterConfiguration.class));
        jvmMemoryMetrics.bindTo(registry);

        Stream.of(
            "heap.used",
            "heap.committed",
            "heap.max",
            "non_heap.used",
            "non_heap.committed",
            "non_heap.max")
            .forEach(s ->
                assertMetric(registry, "jvm.memory." + s, Labels.EMPTY)
                    .isNotZero());

        List<String> memoryPoolNames = getMemoryPoolNames();
        for (String memoryPoolName : memoryPoolNames) {
            final Labels spaceLabel = Labels.Mutable.of("name", memoryPoolName);

            Stream.of(
                "used",
                "committed",
                "max")
                .forEach(s -> assertMetric(registry, "jvm.memory.heap.pool." + s, spaceLabel)
                    .isNotNaN()
                    .isGreaterThanOrEqualTo(s.equals("max") ? -1D : 0D)); // max is often not set with a short-lived JVM
        }
    }

    private AbstractDoubleAssert<?> assertMetric(MetricRegistry registry, String name, Labels labels) {
        return assertThat(registry.getGaugeValue(name, labels))
            .describedAs("metric = '%s', labels = [%s]", name, labels);
    }

    private List<String> getMemoryPoolNames() {
        return ManagementFactory.getMemoryPoolMXBeans()
            .stream()
            .filter(k -> k.getType().equals(MemoryType.HEAP))
            .map(MemoryPoolMXBean::getName)
            .collect(Collectors.toList());
    }
}
