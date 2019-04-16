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

import co.elastic.apm.agent.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.report.ReporterConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ThreadMetricsTest {

    private static final double NUM_ADDED_THREADS = 12.0;
    private final ThreadMetrics threadMetrics = new ThreadMetrics();
    private MetricRegistry registry = new MetricRegistry(mock(ReporterConfiguration.class));

    @Test
    void testThreadCount() {
        threadMetrics.bindTo(registry);
        double numThreads = registry.get("jvm.thread.count", Labels.empty());
        assertThat(numThreads).isNotZero();
        for (int i = 0; i < NUM_ADDED_THREADS; i++) {
            Thread thread = new Thread(() -> {
                try {
                    while (true) {
                        Thread.sleep(1000);
                    }
                } catch (InterruptedException e) {
                }
            });
            thread.setDaemon(true);
            thread.start();
        }
        assertThat(registry.get("jvm.thread.count", Labels.empty())).isEqualTo(numThreads + NUM_ADDED_THREADS);
    }
}
