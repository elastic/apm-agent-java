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
package co.elastic.apm.agent.jmx;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JmxMetricTrackerTest {

    private MetricRegistry metricRegistry;
    private JmxConfiguration config;
    private ElasticApmTracer tracer;

    @BeforeEach
    void setUp() {
        tracer = MockTracer.createRealTracer();
        metricRegistry = tracer.getMetricRegistry();
        config = tracer.getConfig(JmxConfiguration.class);
        tracer.getLifecycleListener(JmxMetricTracker.class).init(ManagementFactory.getPlatformMBeanServer());
    }

    @AfterEach
    void cleanup(){
        tracer.stop();
    }

    @Test
    void testAvailableProcessors() throws Exception {
        setConfig(JmxMetric.valueOf("object_name[java.lang:type=OperatingSystem] attribute[AvailableProcessors:metric_name=available_processors]"));
        assertThat(metricRegistry.getGaugeValue("jvm.jmx.available_processors", Labels.Mutable.of("type", "OperatingSystem"))).isEqualTo(ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors());
    }

    @Test
    void testHeap() throws Exception {
        setConfig(JmxMetric.valueOf("object_name[java.lang:type=Memory] attribute[HeapMemoryUsage:metric_name=heap]"));
        assertThat(metricRegistry.getGaugeValue("jvm.jmx.heap.committed", Labels.Mutable.of("type", "Memory"))).isPositive();
        assertThat(metricRegistry.getGaugeValue("jvm.jmx.heap.init", Labels.Mutable.of("type", "Memory"))).isPositive();
        assertThat(metricRegistry.getGaugeValue("jvm.jmx.heap.used", Labels.Mutable.of("type", "Memory"))).isPositive();
        assertThat(metricRegistry.getGaugeValue("jvm.jmx.heap.max", Labels.Mutable.of("type", "Memory"))).isPositive();
        printMetricSets();
    }

    @Test
    void testGC() throws Exception {
        setConfig(JmxMetric.valueOf("object_name[java.lang:type=GarbageCollector,name=*] attribute[CollectionCount:metric_name=collection_count]"));
        setConfig(JmxMetric.valueOf("object_name[java.lang:type=GarbageCollector,name=*] attribute[CollectionCount:metric_name=collection_count] attribute[CollectionTime]"));
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            String memoryManagerName = gcBean.getName();
            assertThat(metricRegistry.getGaugeValue("jvm.jmx.collection_count", Labels.Mutable.of("name", memoryManagerName).add("type", "GarbageCollector"))).isNotNegative();
            assertThat(metricRegistry.getGaugeValue("jvm.jmx.CollectionTime", Labels.Mutable.of("name", memoryManagerName).add("type", "GarbageCollector"))).isNotNegative();
        }
        printMetricSets();
    }

    @Test
    void testRemoveMetric() throws Exception {
        setConfig(JmxMetric.valueOf("object_name[java.lang:type=GarbageCollector,name=*] attribute[CollectionCount:metric_name=collection_count] attribute[CollectionTime]"));
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            String memoryManagerName = gcBean.getName();
            assertThat(metricRegistry.getGaugeValue("jvm.jmx.collection_count", Labels.Mutable.of("name", memoryManagerName).add("type", "GarbageCollector"))).isNotNegative();
            assertThat(metricRegistry.getGaugeValue("jvm.jmx.CollectionTime", Labels.Mutable.of("name", memoryManagerName).add("type", "GarbageCollector"))).isNotNegative();
        }
        setConfig(JmxMetric.valueOf("object_name[java.lang:type=GarbageCollector,name=*] attribute[CollectionCount]"));
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            String memoryManagerName = gcBean.getName();
            assertThat(metricRegistry.getGaugeValue("jvm.jmx.CollectionCount", Labels.Mutable.of("name", memoryManagerName).add("type", "GarbageCollector"))).isNotNegative();
            assertThat(metricRegistry.getGaugeValue("jvm.jmx.collection_count", Labels.Mutable.of("name", memoryManagerName).add("type", "GarbageCollector"))).isNaN();
            assertThat(metricRegistry.getGaugeValue("jvm.jmx.CollectionTime", Labels.Mutable.of("name", memoryManagerName).add("type", "GarbageCollector"))).isNaN();
        }
    }

    @Test
    void testMBeanAddedLater() throws Exception {
        setConfig(JmxMetric.valueOf("object_name[foo:type=Bar] attribute[Baz]"));
        ObjectName objectName = new ObjectName("foo:type=Bar");
        ManagementFactory.getPlatformMBeanServer().registerMBean(new TestMetric(), objectName);
        try {
            assertThat(metricRegistry.getGaugeValue("jvm.jmx.Baz", Labels.Mutable.of("type", "Bar"))).isEqualTo(42);
        } finally {
            ManagementFactory.getPlatformMBeanServer().unregisterMBean(objectName);
        }
    }

    @Test
    void testMBeanMatchingWildcardAddedLater() throws Exception {
        ObjectName objectName = new ObjectName("foo:type=Foo,name=mbean1");
        ObjectName objectName2 = new ObjectName("foo:type=Foo,name=mbean2");
        try {
            ManagementFactory.getPlatformMBeanServer().registerMBean(new TestMetric(), objectName);
            setConfig(JmxMetric.valueOf("object_name[foo:type=Foo,name=*] attribute[Baz]"));
            assertThat(metricRegistry.getGaugeValue("jvm.jmx.Baz", Labels.Mutable.of("name", "mbean1").add("type", "Foo"))).isEqualTo(42);
            assertThat(metricRegistry.getGaugeValue("jvm.jmx.Baz", Labels.Mutable.of("name", "mbean2").add("type", "Foo"))).isNaN();

            ManagementFactory.getPlatformMBeanServer().registerMBean(new TestMetric(), objectName2);
            assertThat(metricRegistry.getGaugeValue("jvm.jmx.Baz", Labels.Mutable.of("name", "mbean1").add("type", "Foo"))).isEqualTo(42);
            assertThat(metricRegistry.getGaugeValue("jvm.jmx.Baz", Labels.Mutable.of("name", "mbean2").add("type", "Foo"))).isEqualTo(42);
        } finally {
            ManagementFactory.getPlatformMBeanServer().unregisterMBean(objectName);
            ManagementFactory.getPlatformMBeanServer().unregisterMBean(objectName2);
        }
    }

    @Test
    void testMBeanUnregister() throws Exception {
        ObjectName objectName = new ObjectName("foo:type=Foo,name=testMBeanUnregister");
        ManagementFactory.getPlatformMBeanServer().registerMBean(new TestMetric(), objectName);
        try {
            setConfig(JmxMetric.valueOf("object_name[foo:type=Foo,name=*] attribute[Baz]"));
            assertThat(metricRegistry.getGaugeValue("jvm.jmx.Baz", Labels.Mutable.of("name", "testMBeanUnregister").add("type", "Foo"))).isEqualTo(42);
        } finally {
            ManagementFactory.getPlatformMBeanServer().unregisterMBean(objectName);
        }

        // trying to get a non-existing MBean metric value will unregister it
        assertThat(metricRegistry.getGaugeValue("jvm.jmx.Baz", Labels.Mutable.of("name", "testMBeanUnregister").add("type", "Foo"))).isNaN();
        assertThat(metricRegistry.getGauge("jvm.jmx.Baz", Labels.Mutable.of("name", "testMBeanUnregister").add("type", "Foo"))).isNull();
    }

    public interface TestMetricMBean {
        int getBaz();
    }

    public static class TestMetric implements TestMetricMBean {

        @Override
        public int getBaz() {
            return 42;
        }
    }

    private void printMetricSets() {
        DslJsonSerializer metricsReporter = new DslJsonSerializer(mock(StacktraceConfiguration.class), mock(ApmServerClient.class));
        metricRegistry.report(metricsReporter);
        System.out.println(metricsReporter.toString());
    }

    private void setConfig(JmxMetric... jmxMetric) throws java.io.IOException {
        config.getCaptureJmxMetrics().update(Arrays.asList(jmxMetric), SpyConfiguration.CONFIG_SOURCE_NAME);
    }
}
