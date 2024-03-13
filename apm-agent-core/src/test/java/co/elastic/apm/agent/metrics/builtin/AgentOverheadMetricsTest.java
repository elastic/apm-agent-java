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
package co.elastic.apm.agent.metrics.builtin;

import co.elastic.apm.agent.configuration.MetricsConfigurationImpl;
import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.tracer.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.metrics.MetricSet;
import co.elastic.apm.agent.report.ReporterConfigurationImpl;
import co.elastic.apm.agent.util.ExecutorUtils;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class AgentOverheadMetricsTest {

    private MetricRegistry metricRegistry;

    private ReporterConfigurationImpl spyReporterConfig;
    private MetricsConfigurationImpl spyMetricsConfig;

    private AgentOverheadMetrics overheadMetrics;

    @BeforeEach
    public void setUp() {
        spyReporterConfig = spy(ReporterConfigurationImpl.class);
        spyMetricsConfig = spy(MetricsConfigurationImpl.class);

        overheadMetrics = new AgentOverheadMetrics();
        metricRegistry = new MetricRegistry(spyReporterConfig, spyMetricsConfig);
    }

    @Test
    @Disabled("due to flakyness")
    public void checkCpuMetrics() throws InterruptedException {
        //make sure that the OS provides a value for cpuLoad
        awaitNonZeroProcessCpuLoad();

        CountDownLatch finish1 = new CountDownLatch(3);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch consumeAgainLatch = new CountDownLatch(1);
        Runnable threadTask = () -> {
            try {
                startLatch.await();
                consumeCpu();
                finish1.countDown();
                consumeAgainLatch.await();
                consumeCpu();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        ThreadFactory singleNamedThreadFactory = new ExecutorUtils.SingleNamedThreadFactory("cpu-start-before");
        Thread t1 = singleNamedThreadFactory.newThread(threadTask);
        t1.start();
        await().atMost(Duration.ofSeconds(10)).until(() -> t1.getState() == Thread.State.WAITING);

        doReturn(true).when(spyMetricsConfig).isOverheadMetricsEnabled();
        overheadMetrics.bindTo(metricRegistry, spyMetricsConfig);

        ThreadFactory namedThreadFactory = new ExecutorUtils.NamedThreadFactory("cpu-start-after");
        Thread t2 = namedThreadFactory.newThread(threadTask);
        t2.start();
        Thread t3 = namedThreadFactory.newThread(threadTask);
        t3.start();

        startLatch.countDown();
        finish1.await();

        reportAndCheckMetrics(metrics -> {
            assertThat(metrics).containsKeys(
                Labels.Mutable.of("task", "cpu-start-before"),
                Labels.Mutable.of("task", "cpu-start-after")
            );

            assertThat(metrics.get(Labels.Mutable.of("task", "cpu-start-before")).getRawMetrics())
                .hasEntrySatisfying("agent.background.cpu.overhead.pct", val -> assertThat(val).isStrictlyBetween(0.0, 1.0))
                .hasEntrySatisfying("agent.background.cpu.total.pct", val -> assertThat(val).isStrictlyBetween(0.0, 1.0))
                .satisfies(rawValues ->
                    assertThat(rawValues.get("agent.background.cpu.overhead.pct"))
                        .isGreaterThanOrEqualTo(rawValues.get("agent.background.cpu.total.pct"))
                );
            assertThat(metrics.get(Labels.Mutable.of("task", "cpu-start-after")).getRawMetrics())
                .hasEntrySatisfying("agent.background.cpu.overhead.pct", val -> assertThat(val).isStrictlyBetween(0.0, 1.0))
                .hasEntrySatisfying("agent.background.cpu.total.pct", val -> assertThat(val).isStrictlyBetween(0.0, 1.0))
                .satisfies(rawValues ->
                    assertThat(rawValues.get("agent.background.cpu.overhead.pct"))
                        .isGreaterThanOrEqualTo(rawValues.get("agent.background.cpu.total.pct"))
                );
        });

        consumeAgainLatch.countDown();
        t1.join();
        t2.join();
        t3.join();

        //ensure that died threads are also counted
        reportAndCheckMetrics(metrics -> {
            assertThat(metrics.get(Labels.Mutable.of("task", "cpu-start-before")).getRawMetrics())
                .hasEntrySatisfying("agent.background.cpu.overhead.pct", val -> assertThat(val).isStrictlyBetween(0.0, 1.0))
                .hasEntrySatisfying("agent.background.cpu.total.pct", val -> assertThat(val).isStrictlyBetween(0.0, 1.0))
                .satisfies(rawValues ->
                    assertThat(rawValues.get("agent.background.cpu.overhead.pct"))
                        .isGreaterThanOrEqualTo(rawValues.get("agent.background.cpu.total.pct"))
                );
            assertThat(metrics.get(Labels.Mutable.of("task", "cpu-start-after")).getRawMetrics())
                .hasEntrySatisfying("agent.background.cpu.overhead.pct", val -> assertThat(val).isStrictlyBetween(0.0, 1.0))
                .hasEntrySatisfying("agent.background.cpu.total.pct", val -> assertThat(val).isStrictlyBetween(0.0, 1.0))
                .satisfies(rawValues ->
                    assertThat(rawValues.get("agent.background.cpu.overhead.pct"))
                        .isGreaterThanOrEqualTo(rawValues.get("agent.background.cpu.total.pct"))
                );
        });


        //and that died threads are finally cleaned up
        reportAndCheckMetrics(metrics -> {
            assertThat(metrics.get(Labels.Mutable.of("task", "cpu-start-before")).getRawMetrics()).isEmpty();
            assertThat(metrics.get(Labels.Mutable.of("task", "cpu-start-after")).getRawMetrics()).isEmpty();
        });

    }

    @Test
    public void checkAllocationMetric() throws InterruptedException {
        final Collection<Object> blackHole = new ConcurrentLinkedQueue<>();

        CountDownLatch finish1 = new CountDownLatch(3);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch allocateAgainLatch = new CountDownLatch(1);
        Runnable threadTask = () -> {
            try {
                startLatch.await();
                allocate(blackHole, 2);
                finish1.countDown();
                allocateAgainLatch.await();
                allocate(blackHole, 2);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        ThreadFactory singleNamedThreadFactory = new ExecutorUtils.SingleNamedThreadFactory("alloc-start-before");
        Thread t1 = singleNamedThreadFactory.newThread(threadTask);
        t1.start();
        await().atMost(Duration.ofSeconds(10)).until(() -> t1.getState() == Thread.State.WAITING);

        doReturn(true).when(spyMetricsConfig).isOverheadMetricsEnabled();
        overheadMetrics.bindTo(metricRegistry, spyMetricsConfig);

        ThreadFactory namedThreadFactory = new ExecutorUtils.NamedThreadFactory("alloc-start-after");
        Thread t2 = namedThreadFactory.newThread(threadTask);
        t2.start();
        Thread t3 = namedThreadFactory.newThread(threadTask);
        t3.start();

        startLatch.countDown();
        finish1.await();

        //wait until all threads are fully parked for the check below for threads without allocations
        //this is required because allocateAgainLatch.await() seems to occasionally cause an allocation
        await().atMost(Duration.ofSeconds(10)).until(() ->
            t1.getState() == Thread.State.WAITING && t2.getState() == Thread.State.WAITING && t3.getState() == Thread.State.WAITING
        );

        reportAndCheckMetrics(metrics -> {
            assertThat(metrics).containsKeys(
                Labels.Mutable.of("task", "alloc-start-before"),
                Labels.Mutable.of("task", "alloc-start-after")
            );

            assertThat(metrics.get(Labels.Mutable.of("task", "alloc-start-before")).getRawMetrics())
                .hasEntrySatisfying("agent.background.memory.allocation.bytes", val -> assertThat(val).isGreaterThan(1_000_000));
            assertThat(metrics.get(Labels.Mutable.of("task", "alloc-start-after")).getRawMetrics())
                .hasEntrySatisfying("agent.background.memory.allocation.bytes", val -> assertThat(val).isGreaterThan(1_000_000));
        });

        //make sure that threads without allocations are not reported
        Thread.sleep(100);
        reportAndCheckMetrics(metrics -> {
            assertThat(metrics.get(Labels.Mutable.of("task", "alloc-start-before")).getRawMetrics())
                .doesNotContainKey("agent.background.memory.allocation.bytes");
            assertThat(metrics.get(Labels.Mutable.of("task", "alloc-start-after")).getRawMetrics())
                .doesNotContainKey("agent.background.memory.allocation.bytes");
        });

        allocateAgainLatch.countDown();
        t1.join();
        t2.join();
        t3.join();

        //ensure that died threads are also counted
        reportAndCheckMetrics(metrics -> {
            assertThat(metrics.get(Labels.Mutable.of("task", "alloc-start-before")).getRawMetrics())
                .hasEntrySatisfying("agent.background.memory.allocation.bytes", val -> assertThat(val).isGreaterThan(1_000_000));
            assertThat(metrics.get(Labels.Mutable.of("task", "alloc-start-after")).getRawMetrics())
                .hasEntrySatisfying("agent.background.memory.allocation.bytes", val -> assertThat(val).isGreaterThan(1_000_000));
        });

        //and that died threads are finally cleaned up
        reportAndCheckMetrics(metrics -> {
            assertThat(metrics.get(Labels.Mutable.of("task", "alloc-start-before")).getRawMetrics()).isEmpty();
            assertThat(metrics.get(Labels.Mutable.of("task", "alloc-start-after")).getRawMetrics()).isEmpty();
        });
    }


    @Test
    public void checkThreadCountMetric() throws InterruptedException {
        AtomicInteger startedCount = new AtomicInteger();

        CountDownLatch endLatch = new CountDownLatch(1);
        Runnable threadTask = () -> {
            try {
                startedCount.incrementAndGet();
                endLatch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        ThreadFactory singleNamedThreadFactory = new ExecutorUtils.SingleNamedThreadFactory("count-start-before");
        Thread t1 = singleNamedThreadFactory.newThread(threadTask);
        t1.start();
        await().atMost(Duration.ofSeconds(10)).untilAtomic(startedCount, equalTo(1));

        doReturn(true).when(spyMetricsConfig).isOverheadMetricsEnabled();
        overheadMetrics.bindTo(metricRegistry, spyMetricsConfig);

        ThreadFactory namedThreadFactory = new ExecutorUtils.NamedThreadFactory("count-start-after");
        Thread t2 = namedThreadFactory.newThread(threadTask);
        t2.start();
        await().atMost(Duration.ofSeconds(10)).untilAtomic(startedCount, equalTo(2));

        reportAndCheckMetrics(metrics -> {
            assertThat(metrics).containsKeys(
                Labels.Mutable.of("task", "count-start-before"),
                Labels.Mutable.of("task", "count-start-after")
            );

            assertThat(metrics.get(Labels.Mutable.of("task", "count-start-before")).getRawMetrics())
                .containsEntry("agent.background.threads.count", 1.0);
            assertThat(metrics.get(Labels.Mutable.of("task", "count-start-after")).getRawMetrics())
                .containsEntry("agent.background.threads.count", 1.0);
        });

        Thread t3 = namedThreadFactory.newThread(threadTask);
        t3.start();
        await().atMost(Duration.ofSeconds(10)).untilAtomic(startedCount, equalTo(3));

        reportAndCheckMetrics(metrics -> {
            assertThat(metrics.get(Labels.Mutable.of("task", "count-start-before")).getRawMetrics())
                .containsEntry("agent.background.threads.count", 1.0);
            assertThat(metrics.get(Labels.Mutable.of("task", "count-start-after")).getRawMetrics())
                .containsEntry("agent.background.threads.count", 2.0);
        });

        endLatch.countDown();
        t1.join();
        t2.join();
        t3.join();


        ThreadFactory shortLivedFactory = new ExecutorUtils.SingleNamedThreadFactory("count-short-lived");
        Thread t4 = shortLivedFactory.newThread(() -> {
        });
        t4.start();
        t4.join();

        //ensure that died threads are also counted for the time interval in which they died
        reportAndCheckMetrics(metrics -> {
            assertThat(metrics.get(Labels.Mutable.of("task", "count-start-before")).getRawMetrics())
                .containsEntry("agent.background.threads.count", 1.0);
            assertThat(metrics.get(Labels.Mutable.of("task", "count-start-after")).getRawMetrics())
                .containsEntry("agent.background.threads.count", 2.0);
            assertThat(metrics.get(Labels.Mutable.of("task", "count-short-lived")).getRawMetrics())
                .containsEntry("agent.background.threads.count", 1.0);
        });

        //and that died threads are finally cleaned up after the last report
        reportAndCheckMetrics(metrics -> {
            assertThat(metrics.get(Labels.Mutable.of("task", "count-start-before")).getRawMetrics()).isEmpty();
            assertThat(metrics.get(Labels.Mutable.of("task", "count-start-after")).getRawMetrics()).isEmpty();
            assertThat(metrics.get(Labels.Mutable.of("task", "count-short-lived")).getRawMetrics()).isEmpty();
        });
    }

    @Test
    public void disableAllViaFlag() throws InterruptedException {
        ThreadMXBean threadMXBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        threadMXBean.setThreadCpuTimeEnabled(false);
        threadMXBean.setThreadAllocatedMemoryEnabled(false);

        doReturn(false).when(spyMetricsConfig).isOverheadMetricsEnabled();
        overheadMetrics.bindTo(metricRegistry, spyMetricsConfig);

        assertThat(threadMXBean.isThreadCpuTimeEnabled()).isFalse();
        assertThat(threadMXBean.isThreadAllocatedMemoryEnabled()).isFalse();

        List<Object> blackHole = new ArrayList<>();
        ThreadFactory singleNamedThreadFactory = new ExecutorUtils.SingleNamedThreadFactory("test-task");
        Thread t1 = singleNamedThreadFactory.newThread(() -> {
            consumeCpu();
            allocate(blackHole, 4);
        });
        t1.start();
        t1.join();

        reportAndCheckMetrics(metrics -> {
            assertThat(metrics).doesNotContainKey(Labels.Mutable.of("task", "test-task"));
        });
    }


    @ParameterizedTest
    @ValueSource(strings = {
        "agent.background.threads.count",
        "agent.background.memory.allocation.bytes",
        "agent.background.cpu.overhead.pct",
        "agent.background.cpu.total.pct"
    })
    public void testDisableMetric(String metric) throws InterruptedException {
        doReturn(List.of(WildcardMatcher.valueOf(metric))).when(spyReporterConfig).getDisableMetrics();
        doReturn(true).when(spyMetricsConfig).isOverheadMetricsEnabled();
        overheadMetrics.bindTo(metricRegistry, spyMetricsConfig);

        List<Object> blackHole = new ArrayList<>();
        ThreadFactory singleNamedThreadFactory = new ExecutorUtils.SingleNamedThreadFactory("test-task");
        Thread t1 = singleNamedThreadFactory.newThread(() -> {
            consumeCpu();
            allocate(blackHole, 4);
        });
        t1.start();
        t1.join();

        reportAndCheckMetrics(metrics -> {
            MetricSet metricSet = metrics.get(Labels.Mutable.of("task", "test-task"));
            if (metricSet != null) {
                assertThat(metricSet.getRawMetrics()).doesNotContainKey(metric);
            }
        });
    }

    private static void awaitNonZeroProcessCpuLoad() {
        long start = System.nanoTime();
        Double load = null;
        while ((System.nanoTime() - start) < 5_000_000_000L) {
            load = new AgentOverheadMetrics().getProcessCpuLoad();
            if (load != null && load > 0.0) break;
        }
        assertThat(load).isGreaterThan(0.0);
    }

    private void reportAndCheckMetrics(Consumer<Map<Labels, MetricSet>> assertions) {
        metricRegistry.flipPhaseAndReport((metrics) -> {
            assertions.accept(new HashMap<>(metrics));
        });
    }

    private void consumeCpu() {
        int result = 0;
        for (int i = 0; i < 10000; i++) {
            result += Math.random() * i;
        }
        assertThat(result).isGreaterThan(0); //just to consume the value
    }

    private void allocate(Collection<Object> blackHole, int atLeastMegabytes) {
        //we only allocate 1kb per object to have "normal" object sizes (no humongous allocations)
        for (int i = 0; i < 1024 * atLeastMegabytes; i++) {
            blackHole.add(new byte[1024]);
        }
    }

}
