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
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.metrics.DoubleSupplier;
import co.elastic.apm.agent.metrics.MetricRegistry;
import org.stagemonitor.util.StringUtils;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static co.elastic.apm.agent.matcher.WildcardMatcher.caseSensitiveMatcher;

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
    private final Method systemCpuUsage;

    @Nullable
    private final Method processCpuUsage;

    @Nullable
    private final Method freeMemory;

    @Nullable
    private final Method totalMemory;

    @Nullable
    private final Method virtualProcessMemory;
    private final File memInfoFile;

    public SystemMetrics() {
        this(new File("/proc/meminfo"));
    }

    SystemMetrics(File memInfoFile) {
        this.operatingSystemBean = ManagementFactory.getOperatingSystemMXBean();
        this.operatingSystemBeanClass = getFirstClassFound(OPERATING_SYSTEM_BEAN_CLASS_NAMES);
        this.systemCpuUsage = detectMethod("getSystemCpuLoad");
        this.processCpuUsage = detectMethod("getProcessCpuLoad");
        this.freeMemory = detectMethod("getFreePhysicalMemorySize");
        this.totalMemory = detectMethod("getTotalPhysicalMemorySize");
        this.virtualProcessMemory = detectMethod("getCommittedVirtualMemorySize");
        this.memInfoFile = memInfoFile;
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

        if (memInfoFile.canRead()) {
            metricRegistry.addUnlessNan("system.memory.actual.free", Collections.<String, String>emptyMap(), new DoubleSupplier() {
                final List<WildcardMatcher> relevantLines = Arrays.asList(
                    caseSensitiveMatcher("MemAvailable:*kB"),
                    caseSensitiveMatcher("MemFree:*kB"),
                    caseSensitiveMatcher("Buffers:*kB"),
                    caseSensitiveMatcher("Cached:*kB"));

                @Override
                public double get() {
                    Map<String, Long> memInfo = new HashMap<>();
                    try (BufferedReader fileReader = new BufferedReader(new FileReader(memInfoFile))) {
                        for (String memInfoLine = fileReader.readLine(); memInfoLine != null && !memInfoLine.isEmpty(); memInfoLine = fileReader.readLine()) {
                            if (WildcardMatcher.isAnyMatch(relevantLines, memInfoLine)) {
                                final String[] memInfoSplit = StringUtils.split(memInfoLine, ' ');
                                memInfo.put(memInfoSplit[0], Long.parseLong(memInfoSplit[1]) * 1024);
                            }
                        }
                        if (memInfo.containsKey("MemAvailable:")) {
                            return memInfo.get("MemAvailable:");
                        } else if (memInfo.containsKey("MemFree:")) {
                            return memInfo.get("MemFree:") + memInfo.get("Buffers:") + memInfo.get("Cached:");
                        } else {
                            return Double.NaN;
                        }
                    } catch (Exception e) {
                        return Double.NaN;
                    }
                }
            });
        } else {
            metricRegistry.addUnlessNan("system.memory.actual.free", Collections.<String, String>emptyMap(), new DoubleSupplier() {
                @Override
                public double get() {
                    return invoke(freeMemory);
                }
            });
        }

        metricRegistry.addUnlessNegative("system.process.memory.size", Collections.<String, String>emptyMap(), new DoubleSupplier() {
            @Override
            public double get() {
                return invoke(virtualProcessMemory);
            }
        });
    }

    private double invoke(@Nullable Method method) {
        try {
            return method != null ? ((Number) method.invoke(operatingSystemBean)).doubleValue() : Double.NaN;
        } catch (Throwable e) {
            return Double.NaN;
        }
    }

    @Nullable
    private Method detectMethod(String name) {
        if (operatingSystemBeanClass == null) {
            return null;
        }
        try {
            // ensure the Bean we have is actually an instance of the interface
            operatingSystemBeanClass.cast(operatingSystemBean);
            return operatingSystemBeanClass.getMethod(name);
        } catch (ClassCastException | NoSuchMethodException | SecurityException e) {
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
