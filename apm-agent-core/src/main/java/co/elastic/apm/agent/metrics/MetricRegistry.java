/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.metrics;

import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.report.ReporterConfiguration;

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

    /**
     * Groups {@link MetricSet}s by their unique labels.
     */
    private final ConcurrentMap<Labels, MetricSet> metricSets = new ConcurrentHashMap<>();
    private final ReporterConfiguration config;

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
        MetricSet metricSet = getOrCreateMetricSet(labels);
        metricSet.add(name, metric);
    }

    private boolean isDisabled(String name) {
        return WildcardMatcher.anyMatch(config.getDisableMetrics(), name) != null;
    }

    public double get(String name, Labels labels) {
        final MetricSet metricSet = metricSets.get(labels);
        if (metricSet != null) {
            return metricSet.get(name).get();
        }
        return Double.NaN;
    }

    public Map<Labels, MetricSet> getMetricSets() {
        return metricSets;
    }

    private MetricSet getOrCreateMetricSet(Labels labels) {
        MetricSet metricSet = metricSets.get(labels);
        if (metricSet == null) {
            final Labels copy = labels.immutableCopy();
            metricSets.putIfAbsent(copy, new MetricSet(copy));
            metricSet = metricSets.get(copy);
        }
        return metricSet;
    }
}
