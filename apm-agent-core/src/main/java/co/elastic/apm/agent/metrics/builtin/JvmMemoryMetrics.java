/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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

import co.elastic.apm.agent.context.LifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.metrics.DoubleSupplier;
import co.elastic.apm.agent.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricRegistry;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

public class JvmMemoryMetrics implements LifecycleListener {

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
    }

    @Override
    public void stop() throws Exception {
    }
}
