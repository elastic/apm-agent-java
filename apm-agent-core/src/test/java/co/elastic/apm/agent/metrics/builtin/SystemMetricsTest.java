/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.metrics.builtin;

import co.elastic.apm.agent.metrics.MetricRegistry;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class SystemMetricsTest {

    private MetricRegistry metricRegistry = new MetricRegistry();
    private SystemMetrics systemMetrics = new SystemMetrics();

    @Test
    void testSystemMetrics() throws InterruptedException {
        systemMetrics.bindTo(metricRegistry);
        // makes sure system.cpu.total.norm.pct does not return NaN
        consumeCpu();
        Thread.sleep(1000);
        assertThat(metricRegistry.get("system.cpu.total.norm.pct", Collections.emptyMap())).isBetween(0.0, 1.0);
        assertThat(metricRegistry.get("system.process.cpu.total.norm.pct", Collections.emptyMap())).isBetween(0.0, 1.0);
        assertThat(metricRegistry.get("system.memory.total", Collections.emptyMap())).isGreaterThan(0.0);
        assertThat(metricRegistry.get("system.memory.actual.free", Collections.emptyMap())).isGreaterThan(0.0);
        assertThat(metricRegistry.get("system.process.memory.size", Collections.emptyMap())).isGreaterThan(0.0);
    }

    private void consumeCpu() {
        int result = 1;
        for (int i = 0; i < 10000; i++) {
            result += Math.random() * i;
        }
        // forces a side-effect so that the JIT can't optimize away this code
        System.out.println(result);
    }
}
