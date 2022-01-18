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
package co.elastic.apm.agent.metrics;

import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.report.ReporterConfiguration;
import org.HdrHistogram.WriterReaderPhaser;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
    private static final int METRIC_SET_LIMIT = 1000;
    private final WriterReaderPhaser phaser = new WriterReaderPhaser();
    private final ReporterConfiguration config;
    /**
     * Groups {@link MetricSet}s by their unique labels.
     */
    private volatile ConcurrentMap<Labels.Immutable, MetricSet> activeMetricSets = new ConcurrentHashMap<>();
    private ConcurrentMap<Labels.Immutable, MetricSet> inactiveMetricSets = new ConcurrentHashMap<>();
    /**
     * Final and thus stable references to the two different metric sets.
     * See {@link #getOrCreateMetricSet(Labels)}
     */
    private final ConcurrentMap<Labels.Immutable, MetricSet> metricSets1 = activeMetricSets, metricSets2 = inactiveMetricSets;

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
            final MetricSet metricSet = getOrCreateMetricSet(labels);
            if (metricSet != null) {
                metricSet.addGauge(name, metric);
            }
        } finally {
            phaser.writerCriticalSectionExit(criticalValueAtEnter);
        }
    }

    private boolean isDisabled(String name) {
        return WildcardMatcher.anyMatch(config.getDisableMetrics(), name) != null;
    }

    public double getGaugeValue(String name, Labels labels) {
        final MetricSet metricSet = activeMetricSets.get(labels);
        if (metricSet != null) {
            DoubleSupplier gauge = metricSet.getGauge(name);
            if (gauge != null) {
                return gauge.get();
            }
        }
        return Double.NaN;
    }

    @Nullable
    public DoubleSupplier getGauge(String name, Labels labels) {
        final MetricSet metricSet = activeMetricSets.get(labels);
        if (metricSet != null) {
            DoubleSupplier gauge = metricSet.getGauge(name);
            if (gauge != null) {
                return gauge;
            }
        }
        return null;
    }

    /**
     * Executes the following steps within a single read-operation critical section:
     * <ul>
     *     <li>Switch between active and inactive MetricSet containers</li>
     *     <li>Report the inactivated MetricSets (optional)</li>
     *     <li>Reset the inactivated MetricSets</li>
     * </ul>
     *
     * @param metricsReporter a reporter to be used for reporting the inactivated MetricSets. May be {@code null}
     *                        if reporting is not required.
     */
    public void flipPhaseAndReport(@Nullable MetricsReporter metricsReporter) {
        try {
            phaser.readerLock();
            ConcurrentMap<Labels.Immutable, MetricSet> temp = inactiveMetricSets;
            inactiveMetricSets = activeMetricSets;
            activeMetricSets = temp;
            phaser.flipPhase();
            if (metricsReporter != null) {
                metricsReporter.report(inactiveMetricSets);
            }
            for (MetricSet metricSet : inactiveMetricSets.values()) {
                metricSet.resetState();
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
            final MetricSet metricSet = getOrCreateMetricSet(labels);
            if (metricSet != null) {
                metricSet.timer(timerName).update(durationUs, count);
            }
        } finally {
            phaser.writerCriticalSectionExit(criticalValueAtEnter);
        }
    }

    /*
     * Must always be executed in context of a critical section so that the
     * activeMetricSets and inactiveMetricSets reference can't swap while this method runs
     */
    @Nullable
    private MetricSet getOrCreateMetricSet(Labels labels) {
        MetricSet metricSet = activeMetricSets.get(labels);
        if (metricSet != null) {
            return metricSet;
        }
        if (activeMetricSets.size() < METRIC_SET_LIMIT) {
            return createMetricSet(labels.immutableCopy());
        }
        return null;
    }

    @Nonnull
    private MetricSet createMetricSet(Labels.Immutable labelsCopy) {
        // Gauges are the only metric types which are not reset after each report (as opposed to counters and timers)
        // that's why both metric sets have to contain the exact same gauges.
        // we can't access inactiveMetricSets as it might be swapped as this method is executed
        // inactiveMetricSets is only stable after flipping the phase (phaser.flipPhase)
        MetricSet metricSet = new MetricSet(labelsCopy);
        final MetricSet racyMetricSet = metricSets1.putIfAbsent(labelsCopy, metricSet);
        if (racyMetricSet != null) {
            metricSet = racyMetricSet;
        }
        // even if the map already contains this metric set, the gauges reference will be the same
        metricSets2.putIfAbsent(labelsCopy, new MetricSet(labelsCopy, metricSet.getGauges()));
        if (metricSets1.size() >= METRIC_SET_LIMIT) {
            logger.warn("The limit of 1000 timers has been reached, no new timers will be created. " +
                "Try to name your transactions so that there are less distinct transaction names.");
        }
        return activeMetricSets.get(labelsCopy);
    }

    public void incrementCounter(String name, Labels labels) {
        long criticalValueAtEnter = phaser.writerCriticalSectionEnter();
        try {
            final MetricSet metricSet = getOrCreateMetricSet(labels);
            if (metricSet != null) {
                metricSet.incrementCounter(name);
            }
        } finally {
            phaser.writerCriticalSectionExit(criticalValueAtEnter);
        }
    }

    /**
     * @see WriterReaderPhaser#writerCriticalSectionEnter()
     */
    public long writerCriticalSectionEnter() {
        return phaser.writerCriticalSectionEnter();
    }

    /**
     * @see WriterReaderPhaser#writerCriticalSectionExit(long)
     */
    public void writerCriticalSectionExit(long criticalValueAtEnter) {
        phaser.writerCriticalSectionExit(criticalValueAtEnter);
    }

    public void removeGauge(String metricName, Labels labels) {
        MetricSet metricSet = activeMetricSets.get(labels);
        if (metricSet != null) {
            metricSet.getGauges().remove(metricName);
        }
    }

    public interface MetricsReporter {
        /**
         * Don't hold a reference to metricSets after this method ends as it will be reused.
         *
         * @param metricSets the metrics to report
         */
        void report(Map<? extends Labels, MetricSet> metricSets);
    }

}
