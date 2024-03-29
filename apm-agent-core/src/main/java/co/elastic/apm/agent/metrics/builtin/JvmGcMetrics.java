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
import com.sun.management.ThreadMXBean;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

public class JvmGcMetrics extends AbstractLifecycleListener {

    private final List<GarbageCollectorMXBean> garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();

    @Override
    public void start(Tracer tracer) {
        bindTo(tracer.require(ElasticApmTracer.class).getMetricRegistry());
    }

    void bindTo(final MetricRegistry registry) {
        for (final GarbageCollectorMXBean garbageCollectorMXBean : garbageCollectorMXBeans) {
            final Labels tags = Labels.Mutable.of("name", garbageCollectorMXBean.getName());
            registry.addUnlessNegative("jvm.gc.count", tags, new DoubleSupplier() {
                @Override
                public double get() {
                    return garbageCollectorMXBean.getCollectionCount();
                }
            });
            registry.addUnlessNegative("jvm.gc.time", tags, new DoubleSupplier() {
                @Override
                public double get() {
                    return garbageCollectorMXBean.getCollectionTime();
                }
            });
        }

        try {
            // only refer to hotspot specific class via reflection to avoid linkage errors
            final Class<?> sunBeanClass = Class.forName("com.sun.management.ThreadMXBean");
            // J9 does contain com.sun.management.ThreadMXBean in classpath
            // but the actual MBean it uses (com.ibm.lang.management.internal.ExtendedThreadMXBeanImpl) does not implement it
            if (sunBeanClass.isInstance(ManagementFactory.getThreadMXBean())) {

                DoubleSupplier supplier = (DoubleSupplier) Class.forName(getClass().getName() + "$HotspotAllocationSupplier").getEnumConstants()[0];

                // attempt to read it at least once before registering
                supplier.get();

                // in reference to JMH's GC profiler (gc.alloc.rate)
                registry.add("jvm.gc.alloc", Labels.EMPTY, supplier);
            }
        } catch (ClassNotFoundException ignore) {
        } catch (UnsupportedOperationException ignore){
        }
    }

    @IgnoreJRERequirement
    enum HotspotAllocationSupplier implements DoubleSupplier {
        INSTANCE;

        final ThreadMXBean threadMXBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();

        @Override
        public double get() {
            long allocatedBytes = 0;
            for (final long threadAllocatedBytes : threadMXBean.getThreadAllocatedBytes(threadMXBean.getAllThreadIds())) {
                if (threadAllocatedBytes > 0) {
                    allocatedBytes += threadAllocatedBytes;
                }
            }
            return allocatedBytes;
        }
    }
}
