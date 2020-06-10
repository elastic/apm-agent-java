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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JvmMemoryMetricsTest {

    private final JvmMemoryMetrics jvmMemoryMetrics = new JvmMemoryMetrics();

    @Test
    void testMetrics() {
        final MetricRegistry registry = new MetricRegistry(mock(ReporterConfiguration.class));
        jvmMemoryMetrics.bindTo(registry);
        System.out.println(registry.toString());
        assertThat(registry.getGaugeValue("jvm.memory.heap.used", Labels.EMPTY)).isNotZero();

        assertThat(registry.getGaugeValue("jvm.memory.heap.committed", Labels.EMPTY)).isNotZero();

        assertThat(registry.getGaugeValue("jvm.memory.heap.max", Labels.EMPTY)).isNotZero();

        assertThat(registry.getGaugeValue("jvm.memory.non_heap.used", Labels.EMPTY)).isNotZero();

        assertThat(registry.getGaugeValue("jvm.memory.non_heap.committed", Labels.EMPTY)).isNotZero();

        assertThat(registry.getGaugeValue("jvm.memory.non_heap.max", Labels.EMPTY)).isNotZero();

        final Labels edenSpaceLabel = Labels.Mutable.of("name", "G1 Eden Space");
        assertThat(registry.getGaugeValue("jvm.memory.heap.pool.used", edenSpaceLabel)).isNotZero();
        assertThat(registry.getGaugeValue("jvm.memory.heap.pool.used", edenSpaceLabel)).isNotNaN();
        assertThat(registry.getGaugeValue("jvm.memory.heap.pool.committed", edenSpaceLabel)).isNotZero();
        assertThat(registry.getGaugeValue("jvm.memory.heap.pool.committed", edenSpaceLabel)).isNotNaN();
        assertThat(registry.getGaugeValue("jvm.memory.heap.pool.max", edenSpaceLabel)).isNotZero();
        assertThat(registry.getGaugeValue("jvm.memory.heap.pool.max", edenSpaceLabel)).isNotNaN();

        final Labels olgGenLabel = Labels.Mutable.of("name", "G1 Old Gen");
        assertThat(registry.getGaugeValue("jvm.memory.heap.pool.used", olgGenLabel)).isNotZero();
        assertThat(registry.getGaugeValue("jvm.memory.heap.pool.used", olgGenLabel)).isNotNaN();
        assertThat(registry.getGaugeValue("jvm.memory.heap.pool.committed", olgGenLabel)).isNotZero();
        assertThat(registry.getGaugeValue("jvm.memory.heap.pool.committed", olgGenLabel)).isNotNaN();
        assertThat(registry.getGaugeValue("jvm.memory.heap.pool.max", olgGenLabel)).isNotZero();
        assertThat(registry.getGaugeValue("jvm.memory.heap.pool.max", olgGenLabel)).isNotNaN();

        final Labels survivorSpaceLabel = Labels.Mutable.of("name", "G1 Survivor Space");
        assertThat(registry.getGaugeValue("jvm.memory.heap.pool.used", survivorSpaceLabel)).isNotZero();
        assertThat(registry.getGaugeValue("jvm.memory.heap.pool.used", survivorSpaceLabel)).isNotNaN();
        assertThat(registry.getGaugeValue("jvm.memory.heap.pool.committed", survivorSpaceLabel)).isNotZero();
        assertThat(registry.getGaugeValue("jvm.memory.heap.pool.committed", survivorSpaceLabel)).isNotNaN();
        assertThat(registry.getGaugeValue("jvm.memory.heap.pool.max", survivorSpaceLabel)).isNotZero();
        assertThat(registry.getGaugeValue("jvm.memory.heap.pool.max", survivorSpaceLabel)).isNotNaN();

        final long[] longs = new long[1000000];
        System.out.println(registry.toString());
    }
}
