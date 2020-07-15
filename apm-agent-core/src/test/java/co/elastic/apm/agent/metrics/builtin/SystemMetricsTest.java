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

import co.elastic.apm.agent.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.report.ReporterConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SystemMetricsTest {

    private MetricRegistry metricRegistry = new MetricRegistry(mock(ReporterConfiguration.class));
    private SystemMetrics systemMetrics = new SystemMetrics();

    @Test
    @DisabledOnOs(OS.MAC)
    void testSystemMetrics() throws InterruptedException {
        systemMetrics.bindTo(metricRegistry);
        // makes sure system.cpu.total.norm.pct does not return NaN
        consumeCpu();
        Thread.sleep(1000);
        assertThat(metricRegistry.getGaugeValue("system.cpu.total.norm.pct", Labels.EMPTY)).isBetween(0.0, 1.0);
        assertThat(metricRegistry.getGaugeValue("system.process.cpu.total.norm.pct", Labels.EMPTY)).isBetween(0.0, 1.0);
        assertThat(metricRegistry.getGaugeValue("system.memory.total", Labels.EMPTY)).isGreaterThan(0.0);
        assertThat(metricRegistry.getGaugeValue("system.memory.actual.free", Labels.EMPTY)).isGreaterThan(0.0);
        assertThat(metricRegistry.getGaugeValue("system.process.memory.size", Labels.EMPTY)).isGreaterThan(0.0);
    }

    @ParameterizedTest
    @CsvSource({
        "/proc/meminfo,     6235127808",
        "/proc/meminfo-3.14, 556630016"
    })
    void testFreeMemoryMeminfo(String file, long value) throws Exception {
        SystemMetrics systemMetrics = createUnlimitedSystemMetrics(file);
        systemMetrics.bindTo(metricRegistry);

        assertThat(metricRegistry.getGaugeValue("system.memory.actual.free", Labels.EMPTY)).isEqualTo(value);
        assertThat(metricRegistry.getGaugeValue("system.memory.total", Labels.EMPTY)).isEqualTo(7964778496L);

        assertThat(metricRegistry.getGaugeValue("system.process.cgroup.memory.mem.limit.bytes", Labels.EMPTY)).isEqualTo(9223372036854771712L);
    }

    private SystemMetrics createUnlimitedSystemMetrics(String memInfoFile) throws URISyntaxException, IOException {
        File mountInfo = new File(getClass().getResource("/proc/unlimited/memory").toURI());
        File fileTmp = File.createTempFile("temp", null);
        fileTmp.deleteOnExit();
        FileWriter fw = new FileWriter(fileTmp);
        fw.write("39 30 0:35 / " + mountInfo.getAbsolutePath() + " rw,nosuid,nodev,noexec,relatime shared:10 - cgroup cgroup rw,seclabel,memory\n");
        fw.close();

        return new SystemMetrics(new File(getClass().getResource(memInfoFile).toURI()),
            new File(getClass().getResource("/proc/cgroup").toURI()),
            fileTmp
        );
    }

    @ParameterizedTest
    @CsvSource({
        "/proc/meminfo,     964778496, /proc/cgroup, /proc/limited/memory",
        "/proc/meminfo,     964778496, /proc/cgroup2, /proc/sys_cgroup2"
    })
    void testFreeCgroupMemoryMeminfo(String file, long value, String selfCGroup, String sysFsGroup) throws Exception {
        File mountInfo = new File(getClass().getResource(sysFsGroup).toURI());
        File fileTmp = File.createTempFile("temp", null);
        fileTmp.deleteOnExit();
        FileWriter fw = new FileWriter(fileTmp);
        if ("/proc/sys_cgroup2".equalsIgnoreCase(sysFsGroup)) {
            fw.write("30 23 0:26 / " + mountInfo.getAbsolutePath() + " rw,nosuid,nodev,noexec,relatime shared:4 - cgroup2 cgroup rw,seclabel\n");
        }
        else {
            fw.write("39 30 0:35 / " + mountInfo.getAbsolutePath() + " rw,nosuid,nodev,noexec,relatime shared:10 - cgroup cgroup rw,seclabel,memory\n");
        }
        fw.close();

        SystemMetrics systemMetrics = new SystemMetrics(new File(getClass().getResource(file).toURI()),
            new File(getClass().getResource(selfCGroup).toURI()),
            fileTmp
        );
        systemMetrics.bindTo(metricRegistry);

        assertThat(metricRegistry.getGaugeValue("system.process.cgroup.memory.mem.usage.bytes", Labels.EMPTY)).isEqualTo(value);
        assertThat(metricRegistry.getGaugeValue("system.process.cgroup.memory.mem.limit.bytes", Labels.EMPTY)).isEqualTo(7964778496L);
        assertThat(metricRegistry.getGaugeValue("system.process.cgroup.memory.stats.inactive_file.bytes", Labels.EMPTY)).isEqualTo(10407936L);
    }
    @ParameterizedTest
    @ValueSource(strings ={
        "39 30 0:36 / /sys/fs/cgroup/memory rw,nosuid,nodev,noexec,relatime shared:10 - cgroup cgroup rw,seclabel,memory|/sys/fs/cgroup/memory",
    })
    void testCgroup1Regex(String testString) throws Exception {
        String[] split = testString.split("\\|");
        SystemMetrics systemMetrics = createUnlimitedSystemMetrics("/proc/meminfo");
        assertThat(systemMetrics.applyCgroupRegex(SystemMetrics.CGROUP1_MOUNT_POINT, split[0])).isEqualTo(split[1]);

        systemMetrics.bindTo(metricRegistry);
        assertThat(metricRegistry.getGaugeValue("system.process.cgroup.memory.mem.limit.bytes", Labels.EMPTY)).isEqualTo(9223372036854771712L);
    }

    @ParameterizedTest
    @ValueSource(strings ={
        "39 30 0:36 / /sys/fs/cgroup/memory rw,nosuid,nodev,noexec,relatime shared:4 - cgroup2 cgroup rw,seclabel|/sys/fs/cgroup/memory",
    })
    void testCgroup2Regex(String testString) throws Exception {
        String [] split = testString.split("\\|");
        SystemMetrics systemMetrics = createUnlimitedSystemMetrics("/proc/meminfo");
        assertThat(systemMetrics.applyCgroupRegex(SystemMetrics.CGROUP2_MOUNT_POINT, split[0])).isEqualTo(split[1]);
    }

    private void consumeCpu() {
        int result = 1;
        for (int i = 0; i < 10000; i++) {
            result += Math.random() * i;
        }
        // forces a side-effect so that the JIT can't optimize away this code
        System.out.println(result);
    }
}
