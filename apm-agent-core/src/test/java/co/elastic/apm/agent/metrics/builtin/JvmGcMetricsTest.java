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

import co.elastic.apm.agent.metrics.MetricRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JvmGcMetricsTest {

    private final JvmGcMetrics jvmGcMetrics = new JvmGcMetrics();
    private MetricRegistry registry = mock(MetricRegistry.class);

    @Test
    void testGcMetrics() {
        jvmGcMetrics.bindTo(registry);
        verify(registry, atLeastOnce()).addUnlessNegative(eq("jvm.gc.count"), any(), any());
        verify(registry, atLeastOnce()).addUnlessNegative(eq("jvm.gc.time"), any(), any());
        verify(registry, atLeastOnce()).add(eq("jvm.gc.alloc"), any(), any());
    }

    @Test
    void testGetAllocatedBytes() {
        final double snapshot = JvmGcMetrics.HotspotAllocationSupplier.INSTANCE.get();
        assertThat(snapshot).isPositive();
        new Object();
        assertThat(JvmGcMetrics.HotspotAllocationSupplier.INSTANCE.get()).isGreaterThan(snapshot);
    }
}
