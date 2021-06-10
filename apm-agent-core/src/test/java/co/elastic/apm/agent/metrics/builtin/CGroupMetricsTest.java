/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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

import co.elastic.apm.agent.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.report.ReporterConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CGroupMetricsTest {

    private final MetricRegistry metricRegistry = new MetricRegistry(mock(ReporterConfiguration.class));

    private CGroupMetrics createUnlimitedSystemMetrics() throws URISyntaxException, IOException {
        File mountInfo = new File(getClass().getResource("/proc/unlimited/memory").toURI());
        File fileTmp = File.createTempFile("temp", null);
        fileTmp.deleteOnExit();
        FileWriter fw = new FileWriter(fileTmp);
        fw.write("39 30 0:35 / " + mountInfo.getAbsolutePath() + " rw,nosuid,nodev,noexec,relatime shared:10 - cgroup cgroup rw,seclabel,memory\n");
        fw.close();

        return new CGroupMetrics(new File(getClass().getResource("/proc/cgroup").toURI()),
            fileTmp
        );
    }

    @ParameterizedTest
    @CsvSource({
        "964778496, /proc/cgroup, /proc/limited/memory, 7964778496",
        "964778496, /proc/cgroup2, /proc/sys_cgroup2, 7964778496",
        "964778496, /proc/cgroup2_only_0, /proc/sys_cgroup2_unlimited, NaN",   // stat have different values to inactive_file and total_inactive_file
        "964778496, /proc/cgroup2_only_memory, /proc/sys_cgroup2_unlimited_stat_different_order, NaN"    // stat have different values to inactive_file and total_inactive_file different order
    })
    void testFreeCgroupMemory(long value, String selfCGroup, String sysFsGroup, String memLimit) throws Exception {
        File mountInfo = new File(getClass().getResource(sysFsGroup).toURI());
        File fileTmp = File.createTempFile("temp", null);
        fileTmp.deleteOnExit();
        FileWriter fw = new FileWriter(fileTmp);
        if (sysFsGroup.startsWith("/proc/sys_cgroup2")) {
            fw.write("30 23 0:26 / " + mountInfo.getAbsolutePath() + " rw,nosuid,nodev,noexec,relatime shared:4 - cgroup2 cgroup rw,seclabel\n");
        }
        else {
            fw.write("39 30 0:35 / " + mountInfo.getAbsolutePath() + " rw,nosuid,nodev,noexec,relatime shared:10 - cgroup cgroup rw,seclabel,memory\n");
        }
        fw.close();

        CGroupMetrics cgroupMetrics = new CGroupMetrics(new File(getClass().getResource(selfCGroup).toURI()),
            fileTmp
        );
        cgroupMetrics.bindTo(metricRegistry);

        assertThat(metricRegistry.getGaugeValue("system.process.cgroup.memory.mem.usage.bytes", Labels.EMPTY)).isEqualTo(value);
        assertThat(metricRegistry.getGaugeValue("system.process.cgroup.memory.mem.limit.bytes", Labels.EMPTY)).isEqualTo(Double.valueOf(memLimit));
    }
    @ParameterizedTest
    @ValueSource(strings ={
        "39 30 0:36 / /sys/fs/cgroup/memory rw,nosuid,nodev,noexec,relatime shared:10 - cgroup cgroup rw,seclabel,memory|/sys/fs/cgroup/memory",
    })
    void testCgroup1Regex(String testString) throws Exception {
        String[] split = testString.split("\\|");
        CGroupMetrics cgroupMetrics = createUnlimitedSystemMetrics();
        assertThat(cgroupMetrics.applyCgroupRegex(CGroupMetrics.CGROUP1_MOUNT_POINT, split[0])).isEqualTo(split[1]);

        cgroupMetrics.bindTo(metricRegistry);
        assertThat(metricRegistry.getGaugeValue("system.process.cgroup.memory.mem.limit.bytes", Labels.EMPTY))
            // Casting to Double is required so that comparison of two Double#NaN will be correct (see Double#equals javadoc for info)
            .isEqualTo(Double.valueOf(Double.NaN));
    }

    @ParameterizedTest
    @ValueSource(strings ={
        "39 30 0:36 / /sys/fs/cgroup/memory rw,nosuid,nodev,noexec,relatime shared:4 - cgroup2 cgroup rw,seclabel|/sys/fs/cgroup/memory",
    })
    void testCgroup2Regex(String testString) throws Exception {
        String [] split = testString.split("\\|");
        CGroupMetrics cgroupMetrics = createUnlimitedSystemMetrics();
        assertThat(cgroupMetrics.applyCgroupRegex(CGroupMetrics.CGROUP2_MOUNT_POINT, split[0])).isEqualTo(split[1]);
    }

    @Test
    void testUnlimitedCgroup1() throws Exception {
        CGroupMetrics cgroupMetrics = createUnlimitedSystemMetrics();
        cgroupMetrics.bindTo(metricRegistry);

        assertThat(metricRegistry.getGaugeValue("system.process.cgroup.memory.mem.limit.bytes", Labels.EMPTY))
            // Casting to Double is required so that comparison of two Double#NaN will be correct (see Double#equals javadoc for info)
            .isEqualTo(Double.valueOf(Double.NaN));
        assertThat(metricRegistry.getGaugeValue("system.process.cgroup.memory.mem.usage.bytes", Labels.EMPTY)).isEqualTo(964778496);
    }

    @Test
    void testUnlimitedCgroup2() throws Exception {
        CGroupMetrics cgroupMetrics = createUnlimitedSystemMetrics();
        cgroupMetrics.bindTo(metricRegistry);

        assertThat(metricRegistry.getGaugeValue("system.process.cgroup.memory.mem.limit.bytes", Labels.EMPTY))
        // Casting to Double is required so that comparison of two Double#NaN will be correct (see Double#equals javadoc for info)
        .isEqualTo(Double.valueOf(Double.NaN));
        assertThat(metricRegistry.getGaugeValue("system.process.cgroup.memory.mem.usage.bytes", Labels.EMPTY)).isEqualTo(964778496);
    }

}
