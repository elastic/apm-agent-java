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
package co.elastic.apm.agent.metrics;

import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.report.ReporterConfiguration;
import org.HdrHistogram.WriterReaderPhaser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A registry for metrics.
 * <p>
 * Currently only holds gauges.
 * There are plans to add support for histogram-based timers.
 * </p>
 */
public class MetricRegistry {

    private static final Logger logger = LoggerFactory.getLogger(MetricRegistry.class);
    private final WriterReaderPhaser phaser = new WriterReaderPhaser();
    private final ReporterConfiguration config;
    /**
     * Groups {@link MetricSet}s by their unique labels.
     */
    private volatile ConcurrentMap<Labels.Immutable, MetricSet> activeMetricSets = new ConcurrentHashMap<>();
    private ConcurrentMap<Labels.Immutable, MetricSet> inactiveMetricSets = new ConcurrentHashMap<>();

    public MetricRegistry(ReporterConfiguration config) {
        this.config = config;
    }

    /**
     * Same as {@link #add(String, Labels, DoubleSupplier)} but only adds the metric
     * if the {@link DoubleSupplier} does not return {@link Double#NaN}
     *
     * @param name   the name of the metric
     * @param labels labels for the metric.
     *               Tags can be used to create different graphs based for each value of a specific tag name, using a terms aggregation.
     *               Note that there will be a {@link MetricSet} created for each distinct set of labels.
     * @param metric this supplier will be called for every reporting cycle
     *               ({@link co.elastic.apm.agent.report.ReporterConfiguration#metricsInterval metrics_interval)})
     * @see #add(String, Labels, DoubleSupplier)
     */
    public void addUnlessNan(String name, Labels labels, DoubleSupplier metric) {
        if (isDisabled(name)) {
            return;
        }
        if (!Double.isNaN(metric.get())) {
            add(name, labels, metric);
        }
    }

    /**
     * Same as {@link #add(String, Labels, DoubleSupplier)} but only adds the metric
     * if the {@link DoubleSupplier} returns a positive number or zero.
     *
     * @param name   the name of the metric
     * @param labels labels for the metric.
     *               Tags can be used to create different graphs based for each value of a specific tag name, using a terms aggregation.
     *               Note that there will be a {@link MetricSet} created for each distinct set of labels.
     * @param metric this supplier will be called for every reporting cycle
     *               ({@link co.elastic.apm.agent.report.ReporterConfiguration#metricsInterval metrics_interval)})
     * @see #add(String, Labels, DoubleSupplier)
     */
    public void addUnlessNegative(String name, Labels labels, DoubleSupplier metric) {
        if (isDisabled(name)) {
            return;
        }
        if (metric.get() >= 0) {
            add(name, labels, metric);
        }
    }

    /**
     * Adds a gauge to the metric registry.
     *
     * @param name   the name of the metric
     * @param labels labels for the metric.
     *               Tags can be used to create different graphs based for each value of a specific tag name, using a terms aggregation.
     *               Note that there will be a {@link MetricSet} created for each distinct set of labels.
     * @param metric this supplier will be called for every reporting cycle
     *               ({@link co.elastic.apm.agent.report.ReporterConfiguration#metricsInterval metrics_interval)})
     */
    public void add(String name, Labels labels, DoubleSupplier metric) {
        if (isDisabled(name)) {
            return;
        }

        long criticalValueAtEnter = phaser.writerCriticalSectionEnter();
        try {
            getOrCreateMetricSet(labels).addGauge(name, metric);
        } finally {
            phaser.writerCriticalSectionExit(criticalValueAtEnter);
        }
    }

    private boolean isDisabled(String name) {
        return WildcardMatcher.anyMatch(config.getDisableMetrics(), name) != null;
    }

    public double getGauge(String name, Labels labels) {
        final MetricSet metricSet = activeMetricSets.get(labels);
        if (metricSet != null) {
            return metricSet.getGauge(name).get();
        }
        return Double.NaN;
    }

    public void report(MetricsReporter metricsReporter) {
        try {
            phaser.readerLock();
            ConcurrentMap<Labels.Immutable, MetricSet> temp = inactiveMetricSets;
            inactiveMetricSets = activeMetricSets;
            activeMetricSets = temp;
            phaser.flipPhase();
            try {
                metricsReporter.report(inactiveMetricSets);
            } catch (IOException e) {
                logger.error("Error while reporting metrics", e);
            }
        } finally {
            phaser.readerUnlock();
        }
    }

    public void updateTimer(String timerName, Labels labels, long durationUs) {
        updateTimer(timerName, labels, durationUs, 1);
    }

    public void updateTimer(String timerName, Labels labels, long durationUs, long count) {
        long criticalValueAtEnter = phaser.writerCriticalSectionEnter();
        try {
            getOrCreateMetricSet(labels).timer(timerName).update(durationUs, count);
        } finally {
            phaser.writerCriticalSectionExit(criticalValueAtEnter);
        }
    }

    /*
     * Must always be executed in context of a critical section so that the
     * activeMetricSets and inactiveMetricSets reference can't swap while this method runs
     */
    private MetricSet getOrCreateMetricSet(Labels labels) {
        MetricSet metricSet = activeMetricSets.get(labels);
        if (metricSet == null) {
            final Labels.Immutable labelsCopy = labels.immutableCopy();
            // Gauges are the only metric types which are not reset after each report (as opposed to counters and timers)
            // that's why both the activeMetricSet and inactiveMetricSet have to contain the exact same gauges.
            activeMetricSets.putIfAbsent(labelsCopy, new MetricSet(labelsCopy));
            metricSet = activeMetricSets.get(labelsCopy);
            // even if the map already contains this metric set, the gauges reference will be the same
            inactiveMetricSets.putIfAbsent(labelsCopy, new MetricSet(labelsCopy, metricSet.getGauges()));
        }
        return metricSet;
    }

    public void incrementCounter(String name, Labels labels) {
        long criticalValueAtEnter = phaser.writerCriticalSectionEnter();
        try {
            getOrCreateMetricSet(labels).incrementCounter(name);
        } finally {
            phaser.writerCriticalSectionExit(criticalValueAtEnter);
        }
    }

    public void doAtomically(Runnable r) {
        long criticalValueAtEnter = phaser.writerCriticalSectionEnter();
        try {
            r.run();
        } finally {
            phaser.writerCriticalSectionExit(criticalValueAtEnter);
        }
    }

    public interface MetricsReporter {
        void report(Map<Labels.Immutable, MetricSet> metricSets) throws IOException;
    }

}
