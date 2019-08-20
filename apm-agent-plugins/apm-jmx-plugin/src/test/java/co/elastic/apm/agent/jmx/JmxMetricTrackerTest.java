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
package co.elastic.apm.agent.jmx;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JmxMetricTrackerTest {

    private MetricRegistry metricRegistry;
    private JmxConfiguration config;

    @BeforeEach
    void setUp() {
        ElasticApmTracer tracer = MockTracer.createRealTracer();
        metricRegistry = tracer.getMetricRegistry();
        config = tracer.getConfig(JmxConfiguration.class);
        new JmxMetricTracker(tracer).start(tracer);
    }

    @Test
    void testAvailableProcessors() throws Exception {
        addJmxMetric(new JmxMetric("java.lang:type=OperatingSystem", "AvailableProcessors", "available_processors"));
        assertThat(metricRegistry.getGauge("jvm.jmx.available_processors", Labels.Mutable.of("type", "OperatingSystem"))).isEqualTo(ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors());
    }

    @Test
    void testHeap() throws Exception {
        addJmxMetric(new JmxMetric("java.lang:type=Memory", "HeapMemoryUsage", "heap"));
        assertThat(metricRegistry.getGauge("jvm.jmx.heap.committed", Labels.Mutable.of("type", "Memory"))).isPositive();
        assertThat(metricRegistry.getGauge("jvm.jmx.heap.init", Labels.Mutable.of("type", "Memory"))).isPositive();
        assertThat(metricRegistry.getGauge("jvm.jmx.heap.used", Labels.Mutable.of("type", "Memory"))).isPositive();
        assertThat(metricRegistry.getGauge("jvm.jmx.heap.max", Labels.Mutable.of("type", "Memory"))).isPositive();
        printMetricSets();
    }

    @Test
    void testGC() throws Exception {
        addJmxMetric(new JmxMetric("java.lang:type=GarbageCollector,name=*", "CollectionCount", "collection_count"));
        addJmxMetric(new JmxMetric("java.lang:type=GarbageCollector,name=*", "CollectionTime", null));
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            String memoryManagerName = gcBean.getName();
            assertThat(metricRegistry.getGauge("jvm.jmx.collection_count", Labels.Mutable.of("name", memoryManagerName).add("type", "GarbageCollector"))).isNotNegative();
            assertThat(metricRegistry.getGauge("jvm.jmx.CollectionTime", Labels.Mutable.of("name", memoryManagerName).add("type", "GarbageCollector"))).isNotNegative();
        }
        printMetricSets();
    }

    private void printMetricSets() {
        DslJsonSerializer metricsReporter = new DslJsonSerializer(mock(StacktraceConfiguration.class), mock(ApmServerClient.class));
        metricRegistry.report(metricsReporter);
        System.out.println(metricsReporter.toString());
    }

    private void addJmxMetric(JmxMetric jmxMetric) throws java.io.IOException {
        config.getCaptureJmxMetrics().update(List.of(jmxMetric), SpyConfiguration.CONFIG_SOURCE_NAME);
    }
}
