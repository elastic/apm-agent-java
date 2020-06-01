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

import java.io.File;

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
        "/proc/meminfo,     6235127808, /proc/memory.limit_in_bytes        , /proc/memory.usage_in_bytes, /proc/memory.stat, 0",
        "/proc/meminfo-3.14, 556630016, /proc/memory.limit_in_bytes        , /proc/memory.usage_in_bytes, /proc/memory.stat, 0",
        "/proc/meminfo,     7000000000, /proc/memory.limit_in_bytes-limited, /proc/memory.usage_in_bytes, /proc/memory.stat, 778842112"
    })
    void testFreeMemoryMeminfo(String file, long value, String cgroupLimit, String cgroupUsage, String cgroupStat, long rss) throws Exception {
        SystemMetrics.CgroupFiles cgroupFiles = new SystemMetrics.CgroupFiles(new File(getClass().getResource(cgroupLimit).toURI()),
            new File(getClass().getResource(cgroupUsage).toURI()),
            new File(getClass().getResource(cgroupStat).toURI()) );
        SystemMetrics systemMetrics = new SystemMetrics(new File(getClass().getResource(file).toURI()), cgroupFiles, cgroupFiles);
        systemMetrics.bindTo(metricRegistry);

        assertThat(metricRegistry.getGaugeValue("system.memory.actual.free", Labels.EMPTY)).isEqualTo(value);
        assertThat(metricRegistry.getGaugeValue("system.memory.total", Labels.EMPTY)).isEqualTo(7964778496L);
        if (rss > 0) {
            assertThat(metricRegistry.getGaugeValue("system.process.memory.rss.bytes", Labels.EMPTY)).isEqualTo(rss);
        }
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
