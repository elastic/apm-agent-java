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
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    public static final String PROC_SELF_CGROUP = "/proc/self/cgroup";
    public static final String SYS_FS_CGROUP = "/sys/fs/cgroup";
    public static final String PROC_SELF_MOUNTINFO = "/proc/self/mountinfo";
    private final OperatingSystemMXBean operatingSystemBean;

    private static String CGROUP1_MAX_MEMORY = "memory.limit_in_bytes";
    private static String CGROUP1_USED_MEMORY = "memory.usage_in_bytes";
    private static String CGROUP2_MAX_MEMORY = "memory.max";
    private static String CGROUP2_USED_MEMORY = "memory.current";
    private static long UNLIMITED = 0x7FFFFFFFFFFFF000L;

    private Pattern MEMORY_CGROUP = Pattern.compile("^\\d+\\:memory\\:.*");
    private Pattern CGROUP1_MOUNT_POINT = Pattern.compile("^\\d+? \\d+? .+? .+? (.*?) .*cgroup.*cgroup.*memory.*");
    private Pattern CGROUP2_MOUNT_POINT = Pattern.compile("^\\d+? \\d+? .+? .+? (.*?) .*cgroup2.*cgroup.*");

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
    @Nullable
    private final CgroupFiles cgroupFiles;

    public SystemMetrics() {
        this(new File("/proc/meminfo"), new File(PROC_SELF_CGROUP), new File(PROC_SELF_MOUNTINFO));
    }

    SystemMetrics(File memInfoFile, File procSelfCgroup, File mountInfo) {
        this.operatingSystemBean = ManagementFactory.getOperatingSystemMXBean();
        this.systemCpuUsage = JmxUtils.getOperatingSystemMBeanMethod(operatingSystemBean, "getSystemCpuLoad");
        this.processCpuUsage = JmxUtils.getOperatingSystemMBeanMethod(operatingSystemBean, "getProcessCpuLoad");
        this.freeMemory = JmxUtils.getOperatingSystemMBeanMethod(operatingSystemBean, "getFreePhysicalMemorySize");
        this.totalMemory = JmxUtils.getOperatingSystemMBeanMethod(operatingSystemBean, "getTotalPhysicalMemorySize");
        this.virtualProcessMemory = JmxUtils.getOperatingSystemMBeanMethod(operatingSystemBean, "getCommittedVirtualMemorySize");
        this.memInfoFile = memInfoFile;

        cgroupFiles = verifyCgroupEnabled(procSelfCgroup, mountInfo);
    }

    public CgroupFiles verifyCgroupEnabled(File procSelfCgroup, File mountInfo) {
        if (procSelfCgroup.canRead() && mountInfo.canRead()) {
            try(BufferedReader fileReader = new BufferedReader(new FileReader(procSelfCgroup))) {
                String lineCgroup = null;
                for (String cgroupLine = fileReader.readLine(); cgroupLine != null && !cgroupLine.isEmpty(); cgroupLine = fileReader.readLine()) {
                    if (lineCgroup == null && cgroupLine.startsWith("0:")) {
                        lineCgroup = cgroupLine;
                    }
                    if (MEMORY_CGROUP.matcher(cgroupLine).matches()) {
                        lineCgroup = cgroupLine;
                    }
                }
                if (lineCgroup != null) {
                    CgroupFiles cgroupFilesTest = null;
                    try(BufferedReader fileMountInfoReader = new BufferedReader(new FileReader(mountInfo))) {
                        for (String mountLine = fileMountInfoReader.readLine(); mountLine != null && !mountLine.isEmpty(); mountLine = fileMountInfoReader.readLine()) {
                            Matcher matcher = CGROUP2_MOUNT_POINT.matcher(mountLine);
                            if (matcher.matches()) {
                                cgroupFilesTest = verifyCgroup2Available(lineCgroup, new File(matcher.group(1)));
                                if (cgroupFilesTest != null) return cgroupFilesTest;
                            }
                            matcher = CGROUP1_MOUNT_POINT.matcher(mountLine);
                            if (matcher.matches()) {
                                cgroupFilesTest = verifyCgroup1Available(new File(matcher.group(1)));
                                if (cgroupFilesTest != null) return cgroupFilesTest;
                            }
                        }
                    }
                    // Fall back to /sys/fs/cgroup if not found on mountinfo
                    cgroupFilesTest = verifyCgroup2Available(lineCgroup, new File(SYS_FS_CGROUP));
                    if (cgroupFilesTest != null) return cgroupFilesTest;
                    cgroupFilesTest = verifyCgroup1Available( new File(SYS_FS_CGROUP + File.pathSeparator + "memory"));
                    if (cgroupFilesTest != null) return cgroupFilesTest;

                }
            } catch (Exception e) {
            }
        }
        return null;
    }

    private CgroupFiles verifyCgroup2Available(String lineCgroup, File mountDiscovered) throws IOException {
        final String[] cgroupSplit = StringUtils.split(lineCgroup, ':');
        // Checking cgroup2
        File maxMemory = new File(mountDiscovered, cgroupSplit[cgroupSplit.length - 1] + "/" + CGROUP2_MAX_MEMORY);
        if (maxMemory.canRead()) {
            try(BufferedReader fileReaderMem = new BufferedReader(new FileReader(maxMemory))) {
                String memMaxLine = fileReaderMem.readLine();
                if (!"max".equalsIgnoreCase(memMaxLine)) {
                    return new CgroupFiles(maxMemory,
                        new File(mountDiscovered, cgroupSplit[cgroupSplit.length - 1] + "/" + CGROUP2_USED_MEMORY));
                }
            }
        }
        return null;
    }

    private CgroupFiles verifyCgroup1Available(File mountDiscovered) throws IOException {
        // Checking cgroup1
        File maxMemory = new File(mountDiscovered, CGROUP1_MAX_MEMORY);
        if (maxMemory.canRead()) {
            try(BufferedReader fileReaderMem = new BufferedReader(new FileReader(maxMemory))) {
                String memMaxLine = fileReaderMem.readLine();
                long memMax = Long.parseLong(memMaxLine);
                if (memMax < UNLIMITED) { // Cgroup1 use a contant to disabled limits
                    return new CgroupFiles(maxMemory,
                        new File(mountDiscovered, CGROUP1_USED_MEMORY));
                }
            }
        }
        return null;
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

        if (cgroupFiles != null) {
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
        }
        else if (memInfoFile.canRead()) {
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

        public CgroupFiles(File maxMemory, File usedMemory) {
            this.maxMemory = maxMemory;
            this.usedMemory = usedMemory;
        }

        public File getMaxMemory() {
            return maxMemory;
        }

        public File getUsedMemory() {
            return usedMemory;
        }
    }
}
