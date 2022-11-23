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

import co.elastic.apm.agent.configuration.MetricsConfiguration;
import co.elastic.apm.agent.context.AbstractLifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricCollector;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.metrics.MetricsProvider;
import co.elastic.apm.agent.util.ElasticThreadStateListener;
import co.elastic.apm.agent.util.ExecutorUtils;
import co.elastic.apm.agent.util.JmxUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class AgentOverheadMetrics extends AbstractLifecycleListener implements ElasticThreadStateListener, MetricsProvider {

    private static final Logger logger = LoggerFactory.getLogger(AgentOverheadMetrics.class);
    private static final String CPU_OVERHEAD_METRIC = "agent.background.cpu.overhead.pct";
    private static final String CPU_USAGE_METRIC = "agent.background.cpu.total.pct";
    private static final String ALLOCATION_METRIC = "agent.background.memory.allocation.bytes";
    private static final String THREAD_COUNT_METRIC = "agent.background.threads.count";

    private boolean cpuOverheadMetricEnabled;
    private boolean cpuUsageMetricEnabled;
    private boolean allocationMetricEnabled;
    private boolean threadCountMetricEnabled;

    @Nullable
    private Long lastReportedProcessCpuTime;

    /**
     * Contains all started, running and stopped threads since the last metrics reporting.
     */
    private final ConcurrentHashMap<Thread, ThreadInfo> lastThreadInfo = new ConcurrentHashMap<>();

    private static class ThreadInfo {
        final String threadPurpose;

        @Nullable
        volatile Long cpuTime;
        @Nullable
        volatile Long allocationBytes;

        @Nullable
        volatile Long deathCpuTime;
        @Nullable
        volatile Long deathAllocationBytes;

        private ThreadInfo(String threadPurpose) {
            this.threadPurpose = threadPurpose;
        }
    }

    private final ThreadMXBean threadBean;
    private final OperatingSystemMXBean osBean;

    @Nullable
    private final Method isThreadAllocatedMemorySupported;
    @Nullable
    private final Method setThreadAllocatedMemoryEnabled;
    @Nullable
    private final Method getThreadAllocatedBytes;
    @Nullable
    private final Method getProcessCpuLoad;
    @Nullable
    private final Method getProcessCpuTime;

    private final long processCpuTimeScalingFactor;

    public AgentOverheadMetrics() {
        osBean = ManagementFactory.getOperatingSystemMXBean();
        getProcessCpuLoad = JmxUtils.getOperatingSystemMBeanMethod(osBean, "getProcessCpuLoad");
        getProcessCpuTime = JmxUtils.getOperatingSystemMBeanMethod(osBean, "getProcessCpuTime");

        threadBean = ManagementFactory.getThreadMXBean();
        isThreadAllocatedMemorySupported = JmxUtils.getThreadMBeanMethod(threadBean, "isThreadAllocatedMemorySupported");
        setThreadAllocatedMemoryEnabled = JmxUtils.getThreadMBeanMethod(threadBean, "setThreadAllocatedMemoryEnabled", boolean.class);
        getThreadAllocatedBytes = JmxUtils.getThreadMBeanMethod(threadBean, "getThreadAllocatedBytes", long.class);

        // See the documentation of J9 getProcessCpuTime for this special case:
        // https://eclipse-openj9.github.io/openj9-docs/api/jdk8/jre/management/extension/com/ibm/lang/management/OperatingSystemMXBean.html#getProcessCpuTime--
        if (JmxUtils.isIbmOperatingSystemMBean() && "true".equals(System.getProperty("com.ibm.lang.management.OperatingSystemMXBean.isCpuTime100ns"))) {
            processCpuTimeScalingFactor = 100;
        } else {
            processCpuTimeScalingFactor = 1; //all other implementations return the time in nanoseconds
        }
    }

    @Override
    public void start(ElasticApmTracer tracer) throws Exception {
        MetricRegistry metricRegistry = tracer.getMetricRegistry();
        MetricsConfiguration config = tracer.getConfig(MetricsConfiguration.class);
        bindTo(metricRegistry, config);
    }

    void bindTo(MetricRegistry metricRegistry, MetricsConfiguration config) {
        boolean overheadMetricsEnabled = config.isOverheadMetricsEnabled();

        cpuOverheadMetricEnabled = !metricRegistry.isDisabled(CPU_OVERHEAD_METRIC) && overheadMetricsEnabled;
        cpuUsageMetricEnabled = !metricRegistry.isDisabled(CPU_USAGE_METRIC) && overheadMetricsEnabled;
        allocationMetricEnabled = !metricRegistry.isDisabled(ALLOCATION_METRIC) && overheadMetricsEnabled;
        threadCountMetricEnabled = !metricRegistry.isDisabled(THREAD_COUNT_METRIC) && overheadMetricsEnabled;

        if (allocationMetricEnabled) {
            boolean allocationMeasurementEnabled = enableThreadAllocationMeasurement();
            if (!allocationMeasurementEnabled) {
                allocationMetricEnabled = false;
            }
        }

        if (cpuOverheadMetricEnabled || cpuUsageMetricEnabled) {
            //ensure that process-cpu-time is supported, as it is required
            boolean cpuTimeMeasurementEnabled = getProcessCpuTime() != null;
            if (!cpuTimeMeasurementEnabled) {
                logger.warn("Agent cpu overhead metrics can not be enabled: OperatingSystemMXBean.getProcessCpuTime() is not supported");
            }
            cpuTimeMeasurementEnabled = cpuTimeMeasurementEnabled && enableThreadCpuTimeMeasurement();
            if (!cpuTimeMeasurementEnabled) {
                cpuOverheadMetricEnabled = false;
                cpuUsageMetricEnabled = false;
            }
        }

        if (anyMetricEnabled()) {
            //initialize values for first report
            lastReportedProcessCpuTime = getProcessCpuTime();
            ExecutorUtils.setThreadStartListener(this);
            for (Map.Entry<Thread, String> threadWithPurpose : ExecutorUtils.getStartedThreads()) {
                Thread thread = threadWithPurpose.getKey();
                lastThreadInfo.putIfAbsent(thread, new ThreadInfo(threadWithPurpose.getValue()));
                ThreadInfo threadInfo = lastThreadInfo.get(thread);
                updateCpuTimeStat(thread, threadInfo);
                updateAllocationStat(thread, threadInfo);
            }

            metricRegistry.addMetricsProvider(this);
        }
    }

    private boolean enableThreadAllocationMeasurement() {
        if (isThreadAllocatedMemorySupported == null || setThreadAllocatedMemoryEnabled == null || getThreadAllocatedBytes == null) {
            logger.warn("Agent allocation metrics can not be enabled: The JVM ThreadBean does not expose this capability.");
            return false;
        }
        try {
            boolean isSupported = (Boolean) isThreadAllocatedMemorySupported.invoke(threadBean);
            if (!isSupported) {
                logger.warn("Agent allocation metrics can not be enabled: ThreadMxBean.isThreadAllocatedMemorySupported() returned false");
                return false;
            }
        } catch (Exception e) {
            logger.warn("Agent allocation metrics can not be enabled", e);
            return false;
        }
        try {
            setThreadAllocatedMemoryEnabled.invoke(threadBean, true);
            logger.debug("Enabled agent allocation measurement");
        } catch (Exception e) {
            logger.warn("Agent allocation metrics can not be enabled", e);
            return false;
        }
        return true;
    }

    private boolean enableThreadCpuTimeMeasurement() {
        boolean isSupported = threadBean.isThreadCpuTimeSupported();
        if (!isSupported) {
            logger.warn("Agent cpu overhead metrics can not be enabled: ThreadMxBean.isThreadCpuTimeSupported() returned false");
            return false;
        }
        threadBean.setThreadCpuTimeEnabled(true);
        return true;
    }

    private boolean anyMetricEnabled() {
        return cpuOverheadMetricEnabled || cpuUsageMetricEnabled || allocationMetricEnabled || threadCountMetricEnabled;
    }

    @Override
    public void elasticThreadStarted(Thread thread, String purpose) {
        lastThreadInfo.putIfAbsent(thread, new ThreadInfo(purpose));
        ThreadInfo threadInfo = lastThreadInfo.get(thread);
        updateAllocationStat(thread, threadInfo);
        updateCpuTimeStat(thread, threadInfo);
    }

    @Override
    public void elasticThreadFinished(Thread thread) {
        ThreadInfo threadInfo = lastThreadInfo.get(thread);
        if (threadInfo != null) {
            if (cpuOverheadMetricEnabled || cpuUsageMetricEnabled) {
                threadInfo.deathCpuTime = getThreadCpuTime(thread);
            }
            if (allocationMetricEnabled) {
                threadInfo.deathAllocationBytes = getThreadAllocatedBytes(thread);
            }
        }
    }

    @Override
    public void collectAndReset(MetricCollector collector) {
        collectCpuUsageMetrics(collector);
        collectAllocationMetrics(collector);
        collectActiveThreadsMetric(collector);

        //cleanup dead threads
        for (Map.Entry<Thread, ThreadInfo> threadInfo : lastThreadInfo.entrySet()) {
            Thread thread = threadInfo.getKey();
            if (!thread.isAlive()) {
                lastThreadInfo.remove(thread);
            }
        }
    }

    private void collectActiveThreadsMetric(MetricCollector collector) {
        if (!threadCountMetricEnabled) {
            return;
        }
        Map<String, AtomicLong> threadCountByPurpose = new HashMap<>();

        for (ThreadInfo threadInfo : lastThreadInfo.values()) {
            // We don't check here whether the thread is still alive because we also want to count
            // short-lived threads which have been started and died since the last metrics report
            addToCounter(threadCountByPurpose, threadInfo.threadPurpose, 1);
        }

        for (Map.Entry<String, AtomicLong> entry : threadCountByPurpose.entrySet()) {
            String purpose = entry.getKey();
            Labels labels = Labels.Mutable.of("task", purpose).immutableCopy();

            collector.addMetricValue(THREAD_COUNT_METRIC, labels, entry.getValue().get());
        }
    }

    private void collectAllocationMetrics(MetricCollector collector) {
        if (!allocationMetricEnabled) {
            return;
        }

        Map<String, AtomicLong> allocatedBytesByPurpose = new HashMap<>();

        for (Map.Entry<Thread, ThreadInfo> threadInfo : lastThreadInfo.entrySet()) {
            Thread thread = threadInfo.getKey();
            ThreadInfo info = threadInfo.getValue();
            String threadPurpose = info.threadPurpose;

            Long allocationDelta = updateAllocationStat(thread, info);
            if (allocationDelta != null && allocationDelta > 0) {
                addToCounter(allocatedBytesByPurpose, threadPurpose, allocationDelta);
            }
        }

        for (Map.Entry<String, AtomicLong> entry : allocatedBytesByPurpose.entrySet()) {
            String purpose = entry.getKey();
            Labels labels = Labels.Mutable.of("task", purpose).immutableCopy();

            long allocationBytes = entry.getValue().get();
            collector.addMetricValue(ALLOCATION_METRIC, labels, allocationBytes);
        }
    }

    private void collectCpuUsageMetrics(MetricCollector collector) {
        if (!cpuUsageMetricEnabled && !cpuOverheadMetricEnabled) {
            return;
        }

        Map<String, AtomicLong> cpuTimeIncreaseByPurpose = new HashMap<>();

        for (Map.Entry<Thread, ThreadInfo> threadInfo : lastThreadInfo.entrySet()) {
            Thread thread = threadInfo.getKey();
            ThreadInfo info = threadInfo.getValue();
            String threadPurpose = info.threadPurpose;

            Long cpuTimeDelta = updateCpuTimeStat(thread, info);
            if (cpuTimeDelta != null && cpuTimeDelta > 0) {
                addToCounter(cpuTimeIncreaseByPurpose, threadPurpose, cpuTimeDelta);
            }
        }

        Long processCpuTimeDelta = getProcessCpuTimeDelta();
        if (processCpuTimeDelta == null) {
            return; //nothing we can do without the process-cpu-time
        }

        Double processCpuUsage = getProcessCpuLoad();

        for (Map.Entry<String, AtomicLong> entry : cpuTimeIncreaseByPurpose.entrySet()) {
            String purpose = entry.getKey();
            Labels.Immutable labels = Labels.Mutable.of("task", purpose).immutableCopy();

            double cpuOverhead = ((double) entry.getValue().get()) / processCpuTimeDelta;
            if (cpuOverheadMetricEnabled) {
                collector.addMetricValue(CPU_OVERHEAD_METRIC, labels, cpuOverhead);
            }
            if (cpuUsageMetricEnabled && processCpuUsage != null) {
                collector.addMetricValue(CPU_USAGE_METRIC, labels, cpuOverhead * processCpuUsage);
            }
        }
    }

    private <K> void addToCounter(Map<K, AtomicLong> counterMap, K key, long increase) {
        AtomicLong counter = counterMap.get(key);
        if (counter != null) {
            counter.addAndGet(increase);
        } else {
            counterMap.put(key, new AtomicLong(increase));
        }
    }

    @Nullable
    private Long updateCpuTimeStat(Thread thread, ThreadInfo info) {
        if (!cpuUsageMetricEnabled && !cpuOverheadMetricEnabled) {
            return null;
        }

        Long currentCpuTime;
        if (info.deathCpuTime != null) {
            currentCpuTime = info.deathCpuTime;
        } else {
            currentCpuTime = getThreadCpuTime(thread);
        }

        Long delta = null;
        Long lastCpuTime = info.cpuTime;
        if (currentCpuTime != null && lastCpuTime != null) {
            delta = currentCpuTime - lastCpuTime;
        }
        info.cpuTime = currentCpuTime;

        return delta;
    }


    @Nullable
    private Long updateAllocationStat(Thread thread, ThreadInfo info) {
        if (!allocationMetricEnabled) {
            return null;
        }
        Long currentAllocation;
        if (info.deathAllocationBytes != null) {
            currentAllocation = info.deathAllocationBytes;
        } else {
            currentAllocation = getThreadAllocatedBytes(thread);
        }

        Long delta = null;

        Long lastAllocation = info.allocationBytes;
        if (currentAllocation != null && lastAllocation != null) {
            delta = currentAllocation - lastAllocation;
        }
        info.allocationBytes = currentAllocation;

        return delta;
    }

    @Nullable
    private Long getThreadAllocatedBytes(Thread thread) {
        if (getThreadAllocatedBytes == null) {
            return null;
        }
        try {
            long allocated = (Long) getThreadAllocatedBytes.invoke(threadBean, thread.getId());
            if (allocated >= 0) {
                return allocated; //If the thread has died JVMs are allowed to return -1
            }
        } catch (Exception e) {
            logger.error("Error on attempt to fetch thread allocated bytes", e);
        }
        return null;
    }

    @Nullable
    private Long getThreadCpuTime(Thread thread) {
        long time = threadBean.getThreadCpuTime(thread.getId());
        if (time < 0) {
            return null; //If the thread has died JVMs are allowed to return -1
        }
        return time;
    }

    @Nullable
    private Long getProcessCpuTime() {
        if (getProcessCpuTime == null) {
            return null;
        }
        try {
            long cpuTime = (Long) getProcessCpuTime.invoke(osBean);
            if (cpuTime >= 0) { //values lower than zero indicate an error
                return cpuTime * processCpuTimeScalingFactor;
            }
        } catch (Exception e) {
            logger.error("Error on attempt to fetch process cpu time", e);
        }
        return null;
    }

    @Nullable
    private Long getProcessCpuTimeDelta() {
        Long processCpuTimeDelta = null;
        Long currentProcessCpuTime = getProcessCpuTime();
        if (currentProcessCpuTime != null && lastReportedProcessCpuTime != null) {
            processCpuTimeDelta = currentProcessCpuTime - lastReportedProcessCpuTime;
        }
        lastReportedProcessCpuTime = currentProcessCpuTime;
        return processCpuTimeDelta;
    }

    @Nullable
    Double getProcessCpuLoad() {
        if (getProcessCpuLoad == null) {
            return null;
        }
        try {
            double cpuLoad = (Double) getProcessCpuLoad.invoke(osBean);
            if (cpuLoad >= 0) { //values lower than zero indicate an error
                return cpuLoad;
            }
        } catch (Exception e) {
            logger.error("Error on attempt to fetch process cpu load", e);
        }
        return null;
    }
}
