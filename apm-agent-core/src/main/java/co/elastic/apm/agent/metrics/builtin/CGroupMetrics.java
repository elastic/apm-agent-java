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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Record metrics related to the CGroup Usage.
 */
public class CGroupMetrics extends AbstractLifecycleListener {

    public static final String PROC_SELF_CGROUP = "/proc/self/cgroup";
    public static final String PROC_SELF_MOUNTINFO = "/proc/self/mountinfo";
    public static final String DEFAULT_SYS_FS_CGROUP = "/sys/fs/cgroup";

    static final String CGROUP1_MAX_MEMORY = "memory.limit_in_bytes";
    static final String CGROUP1_USED_MEMORY = "memory.usage_in_bytes";
    static final String CGROUP2_MAX_MEMORY = "memory.max";
    static final String CGROUP2_USED_MEMORY = "memory.current";
    static final String CGROUP_MEMORY_STAT = "memory.stat";

    static final Pattern MEMORY_CGROUP = Pattern.compile("^\\d+\\:memory\\:.*");
    static final Pattern CGROUP1_MOUNT_POINT = Pattern.compile("^\\d+? \\d+? .+? .+? (.*?) .*cgroup.*memory.*");
    static final Pattern CGROUP2_MOUNT_POINT = Pattern.compile("^\\d+? \\d+? .+? .+? (.*?) .*cgroup2.*cgroup.*");

    private static final Logger logger = LoggerFactory.getLogger(CGroupMetrics.class);


    @Nullable
    private final CgroupFiles cgroupFiles;

    public CGroupMetrics() {
        this(new File(PROC_SELF_CGROUP), new File(PROC_SELF_MOUNTINFO));
    }

    CGroupMetrics( File procSelfCgroup, File mountInfo) {
        cgroupFiles = findCgroupFiles(procSelfCgroup, mountInfo);
    }

    /**
     * Implementing the cgroup metrics spec - https://github.com/elastic/apm/blob/master/docs/agents/agent-development.md#cgroup-metrics
     *
     * @param procSelfCgroup /proc/self/cgroup file
     * @param mountInfo      /proc/self/mountinfo file
     * @return a holder for the memory cgroup files if found or {@code null} if not found
     */
    @Nullable
    public CgroupFiles findCgroupFiles(File procSelfCgroup, File mountInfo) {
        if (procSelfCgroup.canRead()) {
            String cgroupLine = null;
            try (BufferedReader fileReader = new BufferedReader(new FileReader(procSelfCgroup))) {
                String currentLine = fileReader.readLine();
                while (currentLine != null) {
                    if (cgroupLine == null && currentLine.startsWith("0:")) {
                        cgroupLine = currentLine;
                    }
                    if (MEMORY_CGROUP.matcher(currentLine).matches()) {
                        cgroupLine = currentLine;
                        break;
                    }
                    currentLine = fileReader.readLine();
                }

                if (cgroupLine != null) {
                    CgroupFiles cgroupFiles;

                    // Try to discover the cgroup fs path from the mountinfo file
                    if (mountInfo.canRead()) {
                        String mountLine = null;
                        try (BufferedReader fileMountInfoReader = new BufferedReader(new FileReader(mountInfo))) {
                            mountLine = fileMountInfoReader.readLine();
                            while (mountLine != null) {
                                // cgroup v2
                                String rootCgroupFsPath = applyCgroupRegex(CGROUP2_MOUNT_POINT, mountLine);
                                if (rootCgroupFsPath != null) {
                                    cgroupFiles = createCgroup2Files(cgroupLine, new File(rootCgroupFsPath));
                                    if (cgroupFiles != null) {
                                        return cgroupFiles;
                                    }
                                }

                                // cgroup v1
                                String memoryMountPath = applyCgroupRegex(CGROUP1_MOUNT_POINT, mountLine);
                                if (memoryMountPath != null) {
                                    cgroupFiles = createCgroup1Files(
                                        new File(memoryMountPath)
                                    );
                                    if (cgroupFiles != null) {
                                        return cgroupFiles;
                                    }
                                }

                                mountLine = fileMountInfoReader.readLine();
                            }
                        } catch (Exception e) {
                            logger.info("Failed to discover memory mount files path based on mountinfo line '{}'.", mountLine);
                        }
                    } else {
                        logger.info("Failed to find/read /proc/self/mountinfo file. Looking for memory files in /sys/fs/cgroup.");
                    }

                    // Failed to auto-discover the cgroup fs path from mountinfo, fall back to /sys/fs/cgroup
                    // cgroup v2
                    cgroupFiles = createCgroup2Files(cgroupLine, new File(DEFAULT_SYS_FS_CGROUP));
                    if (cgroupFiles != null) {
                        return cgroupFiles;
                    }
                    // cgroup v1
                    cgroupFiles = createCgroup1Files(new File(DEFAULT_SYS_FS_CGROUP + File.pathSeparator + "memory"));
                    if (cgroupFiles != null) {
                        return cgroupFiles;
                    }
                } else {
                    logger.warn("No /proc/self/cgroup file line matched the tested patterns. Cgroup metrics will not be reported.");
                }
            } catch (Exception e) {
                logger.error("Failed to discover memory mount files path based on cgroup line '" + cgroupLine +
                    "'. Cgroup metrics will not be reported,", e);
            }
        } else {
            logger.debug("Cannot find/read /proc/self/cgroup file. Cgroup metrics will not be reported.");
        }
        return null;
    }

    @Nullable
    String applyCgroupRegex(Pattern regex, String mountLine) {
        Matcher matcher = regex.matcher(mountLine);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

    @Nullable
    private CgroupFiles createCgroup2Files(String cgroupLine, File rootCgroupFsPath) throws IOException {
        final String[] cgroupLineParts = StringUtils.split(cgroupLine, ':');
        String sliceSubdir = cgroupLineParts[cgroupLineParts.length - 1];
        File maxMemoryFile = new File(rootCgroupFsPath, sliceSubdir + File.separatorChar + CGROUP2_MAX_MEMORY);
        if (maxMemoryFile.canRead()) {
            try (BufferedReader maxFileReader = new BufferedReader(new FileReader(maxMemoryFile))) {
                String memMaxLine = maxFileReader.readLine();
                if ("max".equalsIgnoreCase(memMaxLine)) {
                    // Make sure we don't send the max metric when cgroup is not bound to a memory limit
                    maxMemoryFile = null;
                }
            }
            return new CgroupFiles(
                maxMemoryFile,
                new File(rootCgroupFsPath, sliceSubdir + File.separator + CGROUP2_USED_MEMORY),
                new File(rootCgroupFsPath, sliceSubdir + File.separator + CGROUP_MEMORY_STAT)
            );
        }
        return null;
    }

    @Nullable
    private CgroupFiles createCgroup1Files(File memoryMountPath) {
        File maxMemoryFile = new File(memoryMountPath, CGroupMetrics.CGROUP1_MAX_MEMORY);
        if (maxMemoryFile.canRead()) {
            // No need for special treatment for the special "unlimited" value (0x7ffffffffffff000) - omitted by the UI
            return new CgroupFiles(
                maxMemoryFile,
                new File(memoryMountPath, CGroupMetrics.CGROUP1_USED_MEMORY),
                new File(memoryMountPath, CGroupMetrics.CGROUP_MEMORY_STAT)
            );
        }
        return null;
    }

    @Override
    public void start(ElasticApmTracer tracer) {
        bindTo(tracer.getMetricRegistry());
    }

    void bindTo(MetricRegistry metricRegistry) {
        if (cgroupFiles != null) {
            metricRegistry.addUnlessNan("system.process.cgroup.memory.stats.inactive_file.bytes", Labels.EMPTY, new DoubleSupplier() {
                @Override
                public double get() {
                    try (BufferedReader fileReaderStatFile = new BufferedReader(new FileReader(cgroupFiles.getStatMemoryFile()))) {
                        String statLine = fileReaderStatFile.readLine();
                        String inactiveBytes = null;
                        while (statLine != null) {
                            final String[] statLineSplit = StringUtils.split(statLine, ' ');
                            if (statLineSplit.length > 1) {
                                if ("total_inactive_file".equals(statLineSplit[0])) {
                                    inactiveBytes = statLineSplit[1];
                                    break;
                                } else if ("inactive_file".equals(statLineSplit[0])) {
                                    inactiveBytes = statLineSplit[1];
                                }
                            }
                            statLine = fileReaderStatFile.readLine();
                        }
                        return inactiveBytes != null ? Long.parseLong(inactiveBytes) : Double.NaN;
                    } catch (Exception e) {
                        logger.debug("Failed to read " + cgroupFiles.getStatMemoryFile().getAbsolutePath() + " file", e);
                        return Double.NaN;
                    }
                }
            });

            metricRegistry.addUnlessNan("system.process.cgroup.memory.mem.usage.bytes", Labels.EMPTY, new DoubleSupplier() {
                @Override
                public double get() {
                    try (BufferedReader fileReaderMemoryUsed = new BufferedReader(new FileReader(cgroupFiles.getUsedMemoryFile()))) {
                        return Long.parseLong(fileReaderMemoryUsed.readLine());
                    } catch (Exception e) {
                        logger.debug("Failed to read " + cgroupFiles.getUsedMemoryFile().getAbsolutePath() + " file", e);
                        return Double.NaN;
                    }
                }
            });

            final File maxMemoryFile = cgroupFiles.getMaxMemoryFile();
            if (maxMemoryFile != null) {
                metricRegistry.addUnlessNan("system.process.cgroup.memory.mem.limit.bytes", Labels.EMPTY, new DoubleSupplier() {
                    @Override
                    public double get() {
                        try (BufferedReader fileReaderMemoryMax = new BufferedReader(new FileReader(maxMemoryFile))) {
                            return Long.parseLong(fileReaderMemoryMax.readLine());
                        } catch (Exception e) {
                            logger.debug("Failed to read " + maxMemoryFile + " file", e);
                            return Double.NaN;
                        }
                    }
                });
            }
        }
    }

    public static class CgroupFiles {

        @Nullable // may be null if memory mount is found for the cgroup, but memory is unlimited
        protected File maxMemoryFile;
        protected File usedMemoryFile;
        protected File statMemoryFile;

        public CgroupFiles(@Nullable File maxMemoryFile, File usedMemoryFile, File statMemoryFile) {
            this.maxMemoryFile = maxMemoryFile;
            this.usedMemoryFile = usedMemoryFile;
            this.statMemoryFile = statMemoryFile;
        }

        @Nullable
        public File getMaxMemoryFile() {
            return maxMemoryFile;
        }

        public File getUsedMemoryFile() {
            return usedMemoryFile;
        }

        public File getStatMemoryFile() {
            return statMemoryFile;
        }
    }
}
