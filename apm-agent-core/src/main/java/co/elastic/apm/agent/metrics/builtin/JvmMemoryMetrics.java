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

import co.elastic.apm.agent.context.AbstractLifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.metrics.DoubleSupplier;
import co.elastic.apm.agent.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.List;

public class JvmMemoryMetrics extends AbstractLifecycleListener {
    private static final Logger logger = LoggerFactory.getLogger(JvmMemoryMetrics.class);

    @Override
    public void start(ElasticApmTracer tracer) {
        bindTo(tracer.getMetricRegistry());
    }

    void bindTo(final MetricRegistry registry) {
        final MemoryMXBean platformMXBean = ManagementFactory.getPlatformMXBean(MemoryMXBean.class);
        registry.add("jvm.memory.heap.used", Labels.EMPTY, new DoubleSupplier() {
            @Override
            public double get() {
                return platformMXBean.getHeapMemoryUsage().getUsed();
            }
        });
        registry.add("jvm.memory.heap.committed", Labels.EMPTY, new DoubleSupplier() {
            @Override
            public double get() {
                return platformMXBean.getHeapMemoryUsage().getCommitted();
            }
        });
        registry.add("jvm.memory.heap.max", Labels.EMPTY, new DoubleSupplier() {
            @Override
            public double get() {
                return platformMXBean.getHeapMemoryUsage().getMax();
            }
        });
        registry.add("jvm.memory.non_heap.used", Labels.EMPTY, new DoubleSupplier() {
            @Override
            public double get() {
                return platformMXBean.getNonHeapMemoryUsage().getUsed();
            }
        });
        registry.add("jvm.memory.non_heap.committed", Labels.EMPTY, new DoubleSupplier() {
            @Override
            public double get() {
                return platformMXBean.getNonHeapMemoryUsage().getCommitted();
            }
        });
        registry.add("jvm.memory.non_heap.max", Labels.EMPTY, new DoubleSupplier() {
            @Override
            public double get() {
                return platformMXBean.getNonHeapMemoryUsage().getMax();
            }
        });

        List<MemoryPoolMXBean> memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();

        for (final MemoryPoolMXBean memoryPoolMXBean : memoryPoolMXBeans) {
            if (memoryPoolMXBean.getType() != MemoryType.HEAP) {
                continue;
            }
            final Labels memoryPoolTags = Labels.Mutable.of("name", memoryPoolMXBean.getName());
            try {
                registry.add("jvm.memory.heap.pool.used", memoryPoolTags, new DoubleSupplier() {
                    @Override
                    public double get() {
                        return memoryPoolMXBean.getUsage().getUsed();
                    }
                });
                registry.add("jvm.memory.heap.pool.committed", memoryPoolTags, new DoubleSupplier() {
                    @Override
                    public double get() {
                        return memoryPoolMXBean.getUsage().getCommitted();
                    }
                });
                registry.add("jvm.memory.heap.pool.max", memoryPoolTags, new DoubleSupplier() {
                    @Override
                    public double get() {
                        return memoryPoolMXBean.getUsage().getMax();
                    }
                });
            } catch (Exception e) {
                logger.error("Cannot fetch memory metrics of memory pool " + memoryPoolMXBean.getName(), e);
            }
        }
    }
}
