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

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

public class ThreadMetrics extends AbstractLifecycleListener {

    @Override
    public void start(Tracer tracer) {
        bindTo(tracer.require(ElasticApmTracer.class).getMetricRegistry());
    }

    void bindTo(final MetricRegistry registry) {
        final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        registry.add("jvm.thread.count", Labels.EMPTY, new DoubleSupplier() {
            @Override
            public double get() {
                return threadMXBean.getThreadCount();
            }
        });
    }
}
