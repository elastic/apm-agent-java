/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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

import co.elastic.apm.agent.context.AbstractLifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.metrics.DoubleSupplier;
import co.elastic.apm.agent.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.util.JmxUtils;
import org.stagemonitor.util.StringUtils;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static co.elastic.apm.agent.matcher.WildcardMatcher.caseSensitiveMatcher;

/**
 * Record metrics related to the CPU and memory, gathered by the JVM.
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
public class SystemMetrics extends AbstractLifecycleListener {

    private final OperatingSystemMXBean operatingSystemBean;

    private static String CGROUP1_MAX_MEMORY = "/sys/fs/cgroup/memory/memory.limit_in_bytes";
    private static String CGROUP1_USED_MEMORY = "/sys/fs/cgroup/memory/memory.usage_in_bytes";
    private static String CGROUP1_STAT_MEMORY = "/sys/fs/cgroup/memory/memory.stat";
    private static String CGROUP2_MAX_MEMORY = "memory.max";
    private static String CGROUP2_USED_MEMORY = "memory.current";
    private static String CGROUP2_STAT_MEMORY = "memory.stat";
    private static long UNLIMITED = 0x7FFFFFFFFFFFF000L;

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
    private final CgroupFiles cgroup1Files;
    private final CgroupFiles cgroup2Files;

    public SystemMetrics() {
        this(new File("/proc/meminfo"),
            new CgroupFiles(CGROUP1_MAX_MEMORY, CGROUP1_USED_MEMORY, CGROUP1_STAT_MEMORY),
            new Cgroup2Files(CGROUP2_MAX_MEMORY, CGROUP2_USED_MEMORY, CGROUP2_STAT_MEMORY));
    }

    SystemMetrics(File memInfoFile, CgroupFiles cgroup1Files, CgroupFiles cgroup2Files) {
        this.operatingSystemBean = ManagementFactory.getOperatingSystemMXBean();
        this.systemCpuUsage = JmxUtils.getOperatingSystemMBeanMethod(operatingSystemBean, "getSystemCpuLoad");
        this.processCpuUsage = JmxUtils.getOperatingSystemMBeanMethod(operatingSystemBean, "getProcessCpuLoad");
        this.freeMemory = JmxUtils.getOperatingSystemMBeanMethod(operatingSystemBean, "getFreePhysicalMemorySize");
        this.totalMemory = JmxUtils.getOperatingSystemMBeanMethod(operatingSystemBean, "getTotalPhysicalMemorySize");
        this.virtualProcessMemory = JmxUtils.getOperatingSystemMBeanMethod(operatingSystemBean, "getCommittedVirtualMemorySize");
        this.memInfoFile = memInfoFile;
        this.cgroup1Files = cgroup1Files;
        this.cgroup2Files = cgroup2Files;;
    }

    @Override
    public void start(ElasticApmTracer tracer) {
        bindTo(tracer.getMetricRegistry());
    }

    void bindTo(MetricRegistry metricRegistry) {
        // J9 always returns -1 on the first call
        metricRegistry.addUnlessNan("system.cpu.total.norm.pct", Labels.EMPTY, new DoubleSupplier() {
            @Override
            public double get() {
                return invoke(systemCpuUsage);
            }
        });

        metricRegistry.addUnlessNan("system.process.cpu.total.norm.pct", Labels.EMPTY, new DoubleSupplier() {
            @Override
            public double get() {
                return invoke(processCpuUsage);
            }
        });

        AtomicBoolean usingCgroups = new AtomicBoolean(false);
        for(CgroupFiles cgroupFiles_ : new CgroupFiles[]{cgroup1Files, cgroup2Files} ) {
            final CgroupFiles cgroupFiles = cgroupFiles_;
            if (!usingCgroups.get() && cgroupFiles.getMaxMemory().canRead() && cgroupFiles.getUsedMemory().canRead() && cgroupFiles.getStatMemory().canRead()) {
                try(BufferedReader fileReader = new BufferedReader(new FileReader(cgroupFiles.getMaxMemory()))) {
                    String memMaxLine = fileReader.readLine();
                    if ("max".equalsIgnoreCase(memMaxLine)) continue; // Cgroup2 use a string to disabled limits
                    long memMax = Long.parseLong(memMaxLine);
                    if (memMax < UNLIMITED) { // Cgroup1 use a contant to disabled limits
                        usingCgroups.set(true);
                        metricRegistry.addUnlessNan("system.memory.actual.free", Labels.EMPTY, new DoubleSupplier() {
                            @Override
                            public double get() {
                                try(BufferedReader fileReaderMemoryMax = new BufferedReader(new FileReader(cgroupFiles.getMaxMemory()));
                                    BufferedReader fileReaderMemoryUsed = new BufferedReader(new FileReader(cgroupFiles.getUsedMemory()))
                                ) {
                                    long memMax = Long.parseLong(fileReaderMemoryMax.readLine());
                                    long memUsed = Long.parseLong(fileReaderMemoryUsed.readLine());
                                    return memMax - memUsed;
                                } catch (Exception ignored) {
                                    return Double.NaN;
                                }
                            }
                        });
                        metricRegistry.addUnlessNan("system.memory.total", Labels.EMPTY, new DoubleSupplier() {
                            @Override
                            public double get() {
                                try(BufferedReader fileReaderMemoryMax = new BufferedReader(new FileReader(cgroupFiles.getMaxMemory()))) {
                                    long memMax = Long.parseLong(fileReaderMemoryMax.readLine());
                                    return memMax;
                                } catch (Exception ignored) {
                                    return Double.NaN;
                                }
                            }
                        });
                        metricRegistry.addUnlessNan("system.process.memory.rss.bytes", Labels.EMPTY, new DoubleSupplier() {
                            final List<WildcardMatcher> relevantLines = Arrays.asList(caseSensitiveMatcher("total_rss *"), caseSensitiveMatcher("anon *"));

                            @Override
                            public double get() {
                                try(BufferedReader fileReaderStatFile = new BufferedReader(new FileReader(cgroupFiles.getStatMemory()))) {
                                    long sum = 0;
                                    for (String statLine = fileReaderStatFile.readLine(); statLine != null && !statLine.isEmpty(); statLine = fileReaderStatFile.readLine()) {
                                        if (WildcardMatcher.isAnyMatch(relevantLines, statLine)) {
                                            final String[] statLineSplit = StringUtils.split(statLine, ' ');
                                            sum += Long.parseLong(statLineSplit[1]);
                                        }
                                    }
                                    return sum == 0 ? Double.NaN: sum;
                                } catch (Exception ignored) {
                                    return Double.NaN;
                                }
                            }
                        });
                    }
                } catch (Exception ignored) {
                }
            }
        };

        if (!usingCgroups.get() && memInfoFile.canRead()) {
            metricRegistry.addUnlessNan("system.memory.actual.free", Labels.EMPTY, new DoubleSupplier() {
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

            metricRegistry.addUnlessNan("system.memory.total", Labels.EMPTY, new DoubleSupplier() {
                @Override
                public double get() {
                    try (BufferedReader fileReader = new BufferedReader(new FileReader(memInfoFile))) {
                        for (String memInfoLine = fileReader.readLine(); memInfoLine != null && !memInfoLine.isEmpty(); memInfoLine = fileReader.readLine()) {
                            if (memInfoLine.startsWith("MemTotal:")) {
                                final String[] memInfoSplit = StringUtils.split(memInfoLine, ' ');
                                return Long.parseLong(memInfoSplit[1]) * 1024;
                            }
                        }
                        return Double.NaN;
                    } catch (Exception e) {
                        return Double.NaN;
                    }
                }
            });
        } else {
            metricRegistry.addUnlessNan("system.memory.actual.free", Labels.EMPTY, new DoubleSupplier() {
                @Override
                public double get() {
                    return invoke(freeMemory);
                }
            });
            metricRegistry.addUnlessNan("system.memory.total", Labels.EMPTY, new DoubleSupplier() {
                @Override
                public double get() {
                    return invoke(totalMemory);
                }
            });
        }

        metricRegistry.addUnlessNegative("system.process.memory.size", Labels.EMPTY, new DoubleSupplier() {
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

    public static class CgroupFiles {
        protected File maxMemory;
        protected File usedMemory;
        protected File statMemory;

        protected CgroupFiles() {

        }

        public CgroupFiles(String maxMemory, String usedMemory, String statMemory) {
            this.maxMemory = new File(maxMemory);
            this.usedMemory = new File(usedMemory);
            this.statMemory = new File(statMemory);
        }

        public CgroupFiles(File maxMemory, File usedMemory, File statMemory) {
            this.maxMemory = maxMemory;
            this.usedMemory = usedMemory;
            this.statMemory = statMemory;
        }

        public File getMaxMemory() {
            return maxMemory;
        }

        public File getUsedMemory() {
            return usedMemory;
        }

        public File getStatMemory() {
            return statMemory;
        }
    }
    public static class Cgroup2Files extends CgroupFiles {
        public Cgroup2Files(String maxMemory, String usedMemory, String statMemory) {
            try(BufferedReader fileReader = new BufferedReader(new FileReader("/proc/self/cgroup"))) {
                for (String cgroupLine = fileReader.readLine(); cgroupLine != null && !cgroupLine.isEmpty(); cgroupLine = fileReader.readLine()) {
                    if (cgroupLine.startsWith("0:")) {
                        final String[] cgroupSplit = StringUtils.split(cgroupLine, ':');
                        this.maxMemory = new File("/sys/fs/cgroup" + cgroupSplit[cgroupSplit.length - 1] + "/" + maxMemory);
                        this.usedMemory = new File("/sys/fs/cgroup" + cgroupSplit[cgroupSplit.length - 1] + "/" + usedMemory);
                        this.statMemory = new File("/sys/fs/cgroup" + cgroupSplit[cgroupSplit.length - 1] + "/" + statMemory);
                    }
                }
            }
            catch (Exception ignored) {
                this.maxMemory = new File(maxMemory);
                this.usedMemory = new File(usedMemory);
                this.statMemory = new File(statMemory);
            }
        }
    }
}
