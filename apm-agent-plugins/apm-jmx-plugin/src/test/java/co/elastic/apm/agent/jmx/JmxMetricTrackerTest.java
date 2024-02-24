/*
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
 */
package co.elastic.apm.agent.jmx;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.tracer.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.report.serialize.MetricRegistrySerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JmxMetricTrackerTest {

    private MetricRegistry metricRegistry;
    private JmxConfiguration config;
    private ElasticApmTracer tracer;
    private List<ObjectName> toUnregister;
    private JmxMetricTracker jmxTracker;
    private MBeanServer mbeanServer;

    @BeforeEach
    void setUp() {
        tracer = MockTracer.createRealTracer();
        metricRegistry = tracer.getMetricRegistry();
        config = tracer.getConfig(JmxConfiguration.class);
        jmxTracker = tracer.getLifecycleListener(JmxMetricTracker.class);
        mbeanServer = ManagementFactory.getPlatformMBeanServer();
        jmxTracker.init(mbeanServer);
        toUnregister = new ArrayList<>();
    }

    @AfterEach
    void cleanup() {
        tracer.stop();
        for (ObjectName name : toUnregister) {
            try {
                mbeanServer.unregisterMBean(name);
            } catch (Exception e) {
                // silently ignored
            }
        }
        toUnregister.clear();
    }

    private void registerMBean(Object object, ObjectName objectName) throws Exception {
        toUnregister.add(objectName);
        mbeanServer.registerMBean(object, objectName);
    }

    @Test
    void testAvailableProcessors() throws Exception {
        setConfig(JmxMetric.valueOf("object_name[java.lang:type=OperatingSystem] attribute[AvailableProcessors:metric_name=available_processors]"));
        assertThat(metricRegistry.getGaugeValue("jvm.jmx.available_processors", Labels.Mutable.of("type", "OperatingSystem"))).isEqualTo(ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors());
    }

    @Test
    void testHeap() throws Exception {
        setConfig(JmxMetric.valueOf("object_name[java.lang:type=Memory] attribute[HeapMemoryUsage:metric_name=heap]"));
        Labels.Mutable labels = Labels.Mutable.of("type", "Memory");
        assertThat(metricRegistry.getGaugeValue("jvm.jmx.heap.committed", labels)).isPositive();
        assertThat(metricRegistry.getGaugeValue("jvm.jmx.heap.init", labels)).isPositive();
        assertThat(metricRegistry.getGaugeValue("jvm.jmx.heap.used", labels)).isPositive();
        assertThat(metricRegistry.getGaugeValue("jvm.jmx.heap.max", labels)).isPositive();
        printMetricSets();
    }

    @Test
    void testGC() throws Exception {
        setConfig(JmxMetric.valueOf("object_name[java.lang:type=GarbageCollector,name=*] attribute[CollectionCount:metric_name=collection_count]"));
        setConfig(JmxMetric.valueOf("object_name[java.lang:type=GarbageCollector,name=*] attribute[CollectionCount:metric_name=collection_count] attribute[CollectionTime]"));
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            String memoryManagerName = gcBean.getName();
            assertThat(metricRegistry.getGaugeValue("jvm.jmx.collection_count", getGcLabels(memoryManagerName))).isNotNegative();
            assertThat(metricRegistry.getGaugeValue("jvm.jmx.CollectionTime", getGcLabels(memoryManagerName))).isNotNegative();
        }
        printMetricSets();
    }

    @Test
    void testAttributeWildcard() throws Exception {
        setConfig(JmxMetric.valueOf("object_name[java.lang:type=GarbageCollector,name=*] attribute[*]"));
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            String memoryManagerName = gcBean.getName();
            assertThat(metricRegistry.getGaugeValue("jvm.jmx.collection_count", getGcLabels(memoryManagerName))).isNotNegative();
            assertThat(metricRegistry.getGaugeValue("jvm.jmx.CollectionTime", getGcLabels(memoryManagerName))).isNotNegative();
        }
        printMetricSets();
    }

    @Test
    void testRemoveMetric() throws Exception {
        setConfig(JmxMetric.valueOf("object_name[java.lang:type=GarbageCollector,name=*] attribute[CollectionCount:metric_name=collection_count] attribute[CollectionTime]"));
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            String memoryManagerName = gcBean.getName();
            assertThat(metricRegistry.getGaugeValue("jvm.jmx.collection_count", getGcLabels(memoryManagerName))).isNotNegative();
            assertThat(metricRegistry.getGaugeValue("jvm.jmx.CollectionTime", getGcLabels(memoryManagerName))).isNotNegative();
        }
        setConfig(JmxMetric.valueOf("object_name[java.lang:type=GarbageCollector,name=*] attribute[CollectionCount]"));
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            String memoryManagerName = gcBean.getName();
            assertThat(metricRegistry.getGaugeValue("jvm.jmx.CollectionCount", getGcLabels(memoryManagerName))).isNotNegative();
            assertThat(metricRegistry.getGaugeValue("jvm.jmx.collection_count", getGcLabels(memoryManagerName))).isNaN();
            assertThat(metricRegistry.getGaugeValue("jvm.jmx.CollectionTime", getGcLabels(memoryManagerName))).isNaN();
        }
    }

    private static Labels getGcLabels(String memoryManagerName) {
        return Labels.Mutable.of("name", memoryManagerName).add("type", "GarbageCollector");
    }

    @Test
    void testMBeanAddedLater() throws Exception {
        setConfig(JmxMetric.valueOf("object_name[foo:type=Bar] attribute[Baz]"));
        ObjectName objectName = new ObjectName("foo:type=Bar");
        registerMBean(new TestMetric(), objectName);
        assertThat(metricRegistry.getGaugeValue("jvm.jmx.Baz", Labels.Mutable.of("type", "Bar"))).isEqualTo(42);
    }

    @Test
    void testMBeanMatchingWildcardAddedLater() throws Exception {
        ObjectName objectName = new ObjectName("foo:type=Foo,name=mbean1");
        ObjectName objectName2 = new ObjectName("foo:type=Foo,name=mbean2");
        Labels labels1 = Labels.Mutable.of("name", "mbean1").add("type", "Foo");
        Labels labels2 = Labels.Mutable.of("name", "mbean2").add("type", "Foo");

        registerMBean(new TestMetric(), objectName);
        setConfig(JmxMetric.valueOf("object_name[foo:type=Foo,name=*] attribute[Baz]"));

        assertThat(metricRegistry.getGaugeValue("jvm.jmx.Baz", labels1)).isEqualTo(42);
        assertThat(metricRegistry.getGaugeValue("jvm.jmx.Baz", labels2)).isNaN();

        registerMBean(new TestMetric(), objectName2);
        assertThat(metricRegistry.getGaugeValue("jvm.jmx.Baz", labels1)).isEqualTo(42);
        assertThat(metricRegistry.getGaugeValue("jvm.jmx.Baz", labels2)).isEqualTo(42);
    }

    @Test
    void testMBeanUnregister() throws Exception {
        ObjectName objectName = new ObjectName("foo:type=Foo,name=testMBeanUnregister");
        registerMBean(new TestMetric(), objectName);
        setConfig(JmxMetric.valueOf("object_name[foo:type=Foo,name=*] attribute[Baz]"));
        Labels labels = Labels.Mutable.of("name", "testMBeanUnregister").add("type", "Foo");

        assertThat(metricRegistry.getGaugeValue("jvm.jmx.Baz", labels)).isEqualTo(42);

        mbeanServer.unregisterMBean(objectName);

        // trying to get a non-existing MBean metric value will unregister it
        assertThat(metricRegistry.getGaugeValue("jvm.jmx.Baz", labels)).isNaN();
        assertThat(metricRegistry.getGauge("jvm.jmx.Baz", labels)).isNull();
    }

    @Test
    void testMBeanExceptionWhenRegisteredThenOk() throws Exception {
        ObjectName objectName = new ObjectName("foo:type=Foo,name=testMBeanExceptionWhenRegisteredThenOk");
        TestMetric testMetric = new TestMetric();
        testMetric.setValue(-1);
        assertThatThrownBy(testMetric::getBaz).isInstanceOf(RuntimeException.class);

        setConfig(JmxMetric.valueOf("object_name[foo:type=Foo,name=*] attribute[Baz]"));

        registerMBean(testMetric, objectName);

        Labels labels = Labels.Mutable.of("name", "testMBeanExceptionWhenRegisteredThenOk").add("type", "Foo");
        assertThat(metricRegistry.getGaugeValue("jvm.jmx.Baz", labels))
            .isNaN();
        assertThat(metricRegistry.getGauge("jvm.jmx.Baz", labels))
            .isNull();

        testMetric.setValue(37);
        // calling directly the JMX tracker to avoid testing async execution
        jmxTracker.retryFailedJmx(mbeanServer);

        assertThat(metricRegistry.getGaugeValue("jvm.jmx.Baz", labels))
            .isEqualTo(37);
    }

    public interface TestMetricMBean {
        int getBaz();
    }

    public static class TestMetric implements TestMetricMBean {

        private int value;

        public TestMetric() {
            this.value = 42;
        }

        void setValue(int value) {
            this.value = value;
        }

        @Override
        public int getBaz() {
            if (value < 0) {
                throw new RuntimeException("value less than zero");
            }
            return value;
        }
    }

    private void printMetricSets() {
        metricRegistry.flipPhaseAndReport(
            metricSets -> {
                metricSets.values().forEach(
                    metricSet -> System.out.println(new MetricRegistrySerializer().serialize(metricSet, emptyList()).toString())
                );
            }
        );
    }

    private void setConfig(JmxMetric... jmxMetric) throws java.io.IOException {
        config.getCaptureJmxMetrics().update(Arrays.asList(jmxMetric), SpyConfiguration.CONFIG_SOURCE_NAME);
    }
}
