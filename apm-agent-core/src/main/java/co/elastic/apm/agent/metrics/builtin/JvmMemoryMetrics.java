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

import co.elastic.apm.agent.tracer.AbstractLifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.tracer.metrics.DoubleSupplier;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;

public class JvmMemoryMetrics extends AbstractLifecycleListener {
    private static final Logger logger = LoggerFactory.getLogger(JvmMemoryMetrics.class);

    @Override
    public void start(Tracer tracer) {
        bindTo(tracer.require(ElasticApmTracer.class).getMetricRegistry());
    }

    void bindTo(final MetricRegistry registry) {
        final MemoryMXBean platformMXBean = ManagementFactory.getPlatformMXBean(MemoryMXBean.class);
        registerMemoryUsage(registry, "jvm.memory.heap", Labels.EMPTY, new MemoryUsageGetter() {
            @Override
            public MemoryUsage get() {
                return platformMXBean.getHeapMemoryUsage();
            }
        });
        registerMemoryUsage(registry, "jvm.memory.non_heap", Labels.EMPTY, new MemoryUsageGetter() {
            @Override
            public MemoryUsage get() {
                return platformMXBean.getNonHeapMemoryUsage();
            }
        });


        List<MemoryPoolMXBean> memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();

        for (final MemoryPoolMXBean memoryPoolMXBean : memoryPoolMXBeans) {
            String type = memoryPoolMXBean.getType().name().toLowerCase();

            final Labels labels = Labels.Mutable.of("name", memoryPoolMXBean.getName());
            try {
                registerMemoryUsage(registry, String.format("jvm.memory.%s.pool", type), labels, new MemoryUsageGetter() {
                    @Override
                    public MemoryUsage get() {
                        return memoryPoolMXBean.getUsage();
                    }
                });
            } catch (Exception e) {
                logger.error("Cannot fetch memory metrics of memory pool " + memoryPoolMXBean.getName(), e);
            }
        }
    }

    private interface MemoryUsageGetter {
        MemoryUsage get();
    }

    private static void registerMemoryUsage(MetricRegistry registry, String prefix, Labels labels, final MemoryUsageGetter memoryUsageGetter) {
        registry.add(prefix + ".used", labels, new DoubleSupplier() {
            @Override
            public double get() {
                return memoryUsageGetter.get().getUsed();
            }
        });
        registry.add(prefix + ".committed", labels, new DoubleSupplier() {
            @Override
            public double get() {
                return memoryUsageGetter.get().getCommitted();
            }
        });
        registry.addUnlessNegative(prefix + ".max", labels, new DoubleSupplier() {
            @Override
            public double get() {
                return memoryUsageGetter.get().getMax();
            }
        });
    }
}
