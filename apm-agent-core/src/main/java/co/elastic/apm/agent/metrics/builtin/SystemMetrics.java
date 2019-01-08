/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018-2019 Elastic and contributors
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

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Record metrics related to the CPU, gathered by the JVM.
 * <p>
 * Supported JVM implementations:
 * <ul>
 * <li>HotSpot</li>
 * <li>J9</li>
 * </ul>
 * <p>
 * This implementation is based on io.micrometer.core.instrument.binder.system.ProcessorMetrics,
 * under Apache License 2.0
 */
public class SystemMetrics implements LifecycleListener {

    /**
     * List of public, exported interface class names from supported JVM implementations.
     */
    private static final List<String> OPERATING_SYSTEM_BEAN_CLASS_NAMES = Arrays.asList(
        "com.sun.management.OperatingSystemMXBean", // HotSpot
        "com.ibm.lang.management.OperatingSystemMXBean" // J9
    );

    private final OperatingSystemMXBean operatingSystemBean;

    @Nullable
    private final Class<?> operatingSystemBeanClass;

    @Nullable
    private final MethodHandle systemCpuUsage;

    @Nullable
    private final MethodHandle processCpuUsage;

    @Nullable
    private final MethodHandle freeMemory;

    @Nullable
    private final MethodHandle totalMemory;

    @Nullable
    private final MethodHandle virtualProcessMemory;

    public SystemMetrics() {
        this.operatingSystemBean = ManagementFactory.getOperatingSystemMXBean();
        this.operatingSystemBeanClass = getFirstClassFound(OPERATING_SYSTEM_BEAN_CLASS_NAMES);
        this.systemCpuUsage = detectMethod("getSystemCpuLoad", double.class);
        this.processCpuUsage = detectMethod("getProcessCpuLoad", double.class);
        this.freeMemory = detectMethod("getFreePhysicalMemorySize", long.class);
        this.totalMemory = detectMethod("getTotalPhysicalMemorySize", long.class);
        this.virtualProcessMemory = detectMethod("getCommittedVirtualMemorySize", long.class);
    }

    @Override
    public void start(ElasticApmTracer tracer) {
        bindTo(tracer.getMetricRegistry());
    }

    void bindTo(MetricRegistry metricRegistry) {
        metricRegistry.addUnlessNegative("system.cpu.total.norm.pct", Collections.<String, String>emptyMap(), new DoubleSupplier() {
            @Override
            public double get() {
                return invoke(systemCpuUsage);
            }
        });

        metricRegistry.addUnlessNegative("system.process.cpu.total.norm.pct", Collections.<String, String>emptyMap(), new DoubleSupplier() {
            @Override
            public double get() {
                return invoke(processCpuUsage);
            }
        });

        metricRegistry.addUnlessNan("system.memory.total", Collections.<String, String>emptyMap(), new DoubleSupplier() {
            @Override
            public double get() {
                return invoke(totalMemory);
            }
        });

        metricRegistry.addUnlessNan("system.memory.actual.free", Collections.<String, String>emptyMap(), new DoubleSupplier() {
            @Override
            public double get() {
                return invoke(freeMemory);
            }
        });

        metricRegistry.addUnlessNegative("system.process.memory.size", Collections.<String, String>emptyMap(), new DoubleSupplier() {
            @Override
            public double get() {
                return invoke(virtualProcessMemory);
            }
        });
    }

    private double invoke(@Nullable MethodHandle method) {
        try {
            return method != null ? (double) method.invoke(operatingSystemBean) : Double.NaN;
        } catch (Throwable e) {
            return Double.NaN;
        }
    }

    @Nullable
    private MethodHandle detectMethod(String name, Class<?> returnType) {
        if (operatingSystemBeanClass == null) {
            return null;
        }
        try {
            // ensure the Bean we have is actually an instance of the interface
            operatingSystemBeanClass.cast(operatingSystemBean);
            return MethodHandles.lookup().findVirtual(operatingSystemBeanClass, name, MethodType.methodType(returnType));
        } catch (ClassCastException | NoSuchMethodException | SecurityException | IllegalAccessException e) {
            return null;
        }
    }

    @Nullable
    private Class<?> getFirstClassFound(List<String> classNames) {
        for (String className : classNames) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException ignore) {
            }
        }
        return null;
    }

    @Override
    public void stop() throws Exception {
    }
}
