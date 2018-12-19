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
import com.sun.management.ThreadMXBean;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JvmGcMetrics implements LifecycleListener {

    private final List<GarbageCollectorMXBean> garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();

    @Override
    public void start(ElasticApmTracer tracer) {
        bindTo(tracer.getMetricRegistry());
    }

    void bindTo(final MetricRegistry registry) {
        for (final GarbageCollectorMXBean garbageCollectorMXBean : garbageCollectorMXBeans) {
            final Map<String, String> tags = Collections.singletonMap("name", garbageCollectorMXBean.getName());
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
            Class.forName("com.sun.management.ThreadMXBean");
            // in reference to JMH's GC profiler (gc.alloc.rate)
            registry.add("jvm.gc.alloc", Collections.<String, String>emptyMap(),
                (DoubleSupplier) Class.forName(getClass().getName() + "$HotspotAllocationSupplier").getEnumConstants()[0]);
        } catch (ClassNotFoundException ignore) {
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

    @Override
    public void stop() throws Exception {
    }
}
