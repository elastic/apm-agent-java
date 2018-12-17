/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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

import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonWriter;

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

    private static final byte NEW_LINE = '\n';

    /**
     * Groups {@link MetricSet}s by their unique tags.
     */
    private final ConcurrentMap<Map<String, String>, MetricSet> metricSets = new ConcurrentHashMap<>();

    /**
     * Same as {@link #add(String, Map, DoubleSupplier)} but only adds the metric
     * if the {@link DoubleSupplier} does not return {@link Double#NaN}
     *
     * @param name   the name of the metric
     * @param tags   tags for the metric.
     *               Tags can be used to create different graphs based for each value of a specific tag name, using a terms aggregation.
     *               Note that there will be a {@link MetricSet} created for each distinct set of tags.
     * @param metric this supplier will be called for every reporting cycle
     *               ({@link co.elastic.apm.agent.report.ReporterConfiguration#metricsInterval metrics_interval)})
     * @see #add(String, Map, DoubleSupplier)
     */
    public void addUnlessNan(String name, Map<String, String> tags, DoubleSupplier metric) {
        if (!Double.isNaN(metric.get())) {
            add(name, tags, metric);
        }
    }

    /**
     * Same as {@link #add(String, Map, DoubleSupplier)} but only adds the metric
     * if the {@link DoubleSupplier} returns a positive number or zero.
     *
     * @param name   the name of the metric
     * @param tags   tags for the metric.
     *               Tags can be used to create different graphs based for each value of a specific tag name, using a terms aggregation.
     *               Note that there will be a {@link MetricSet} created for each distinct set of tags.
     * @param metric this supplier will be called for every reporting cycle
     *               ({@link co.elastic.apm.agent.report.ReporterConfiguration#metricsInterval metrics_interval)})
     * @see #add(String, Map, DoubleSupplier)
     */
    public void addUnlessNegative(String name, Map<String, String> tags, DoubleSupplier metric) {
        if (metric.get() >= 0) {
            add(name, tags, metric);
        }
    }

    /**
     * Adds a gauge to the metric registry.
     *
     * @param name   the name of the metric
     * @param tags   tags for the metric.
     *               Tags can be used to create different graphs based for each value of a specific tag name, using a terms aggregation.
     *               Note that there will be a {@link MetricSet} created for each distinct set of tags.
     * @param metric this supplier will be called for every reporting cycle
     *               ({@link co.elastic.apm.agent.report.ReporterConfiguration#metricsInterval metrics_interval)})
     */
    public void add(String name, Map<String, String> tags, DoubleSupplier metric) {
        MetricSet metricSet = metricSets.get(tags);
        if (metricSet == null) {
            metricSets.putIfAbsent(tags, new MetricSet(tags));
            metricSet = metricSets.get(tags);
        }
        metricSet.add(name, metric);
    }

    public void serialize(JsonWriter jw, StringBuilder replaceBuilder) {
        final long timestamp = System.currentTimeMillis() * 1000;
        for (MetricSet metricSet : metricSets.values()) {
            metricSet.serialize(timestamp, replaceBuilder, jw);
            jw.writeByte(NEW_LINE);
        }
    }

    public double get(String name, Map<String, String> tags) {
        final MetricSet metricSet = metricSets.get(tags);
        if (metricSet != null) {
            return metricSet.get(name).get();
        }
        return Double.NaN;
    }

    @Override
    public String toString() {
        final JsonWriter jw = new DslJson<>().newWriter();
        serialize(jw, new StringBuilder());
        return jw.toString();
    }
}
