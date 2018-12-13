/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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

import co.elastic.apm.agent.context.LifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.metrics.DoubleSupplier;
import co.elastic.apm.agent.metrics.MetricRegistry;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.Collections;

public class JvmMemoryMetrics implements LifecycleListener {

    @Override
    public void start(ElasticApmTracer tracer) {
        bindTo(tracer.getMetricRegistry());
    }

    private void bindTo(final MetricRegistry registry) {
        register(registry, "nonheap", ManagementFactory.getPlatformMXBean(MemoryMXBean.class).getNonHeapMemoryUsage());
        register(registry, "heap", ManagementFactory.getPlatformMXBean(MemoryMXBean.class).getNonHeapMemoryUsage());
    }

    private void register(final MetricRegistry registry, final String area, final MemoryUsage memoryUsage) {
        registry.add("jvm.memory." + area + ".used", Collections.<String, String>emptyMap(), new DoubleSupplier() {
            @Override
            public double get() {
                return memoryUsage.getUsed();
            }
        });

        registry.add("jvm.memory." + area + ".committed", Collections.<String, String>emptyMap(), new DoubleSupplier() {
            @Override
            public double get() {
                return memoryUsage.getCommitted();
            }
        });

        registry.add("jvm.memory." + area + ".max", Collections.<String, String>emptyMap(), new DoubleSupplier() {
            @Override
            public double get() {
                return memoryUsage.getMax();
            }
        });
    }

    @Override
    public void stop() throws Exception {
    }
}
