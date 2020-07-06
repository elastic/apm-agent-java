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
package co.elastic.apm.agent.metrics.builtin;

import co.elastic.apm.agent.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.report.ReporterConfiguration;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JvmMemoryMetricsTest {

    private final JvmMemoryMetrics jvmMemoryMetrics = new JvmMemoryMetrics();

    @Test
    void testMetrics() {
        final MetricRegistry registry = new MetricRegistry(mock(ReporterConfiguration.class));
        jvmMemoryMetrics.bindTo(registry);

        assertThat(registry.getGaugeValue("jvm.memory.heap.used", Labels.EMPTY)).isNotZero();
        assertThat(registry.getGaugeValue("jvm.memory.heap.committed", Labels.EMPTY)).isNotZero();
        assertThat(registry.getGaugeValue("jvm.memory.heap.max", Labels.EMPTY)).isNotZero();
        assertThat(registry.getGaugeValue("jvm.memory.non_heap.used", Labels.EMPTY)).isNotZero();
        assertThat(registry.getGaugeValue("jvm.memory.non_heap.committed", Labels.EMPTY)).isNotZero();
        assertThat(registry.getGaugeValue("jvm.memory.non_heap.max", Labels.EMPTY)).isNotZero();

        List<String> memoryPoolNames = getMemoryPoolNames();
        for (String memoryPoolName : memoryPoolNames) {
            final Labels spaceLabel = Labels.Mutable.of("name", memoryPoolName);
            assertThat(registry.getGaugeValue("jvm.memory.heap.pool.used", spaceLabel)).isNotZero();
            assertThat(registry.getGaugeValue("jvm.memory.heap.pool.used", spaceLabel)).isNotNaN();
            assertThat(registry.getGaugeValue("jvm.memory.heap.pool.committed", spaceLabel)).isNotZero();
            assertThat(registry.getGaugeValue("jvm.memory.heap.pool.committed", spaceLabel)).isNotNaN();
            assertThat(registry.getGaugeValue("jvm.memory.heap.pool.max", spaceLabel)).isNotZero();
            assertThat(registry.getGaugeValue("jvm.memory.heap.pool.max", spaceLabel)).isNotNaN();
        }
        final long[] longs = new long[1000000];
    }

    private List<String> getMemoryPoolNames() {
        List<MemoryPoolMXBean> memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();
        return memoryPoolMXBeans.stream().filter(k -> k.getType().equals(MemoryType.HEAP)).map(k -> k.getName()).collect(Collectors.toList());
    }
}
