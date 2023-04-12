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

import co.elastic.apm.agent.tracer.pooling.Recyclable;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A metric set is a collection of metrics which have the same labels.
 * <p>
 * A metric set corresponds to one document per
 * {@link co.elastic.apm.agent.report.ReporterConfiguration#metricsInterval metrics_interval} in Elasticsearch.
 * An alternative would be to have one document per metric but having one document for all metrics with the same labels saves disk space.
 * </p>
 * Example of some serialized metric sets:
 * <pre>
 * {"metricset":{"timestamp":1545047730692000,"samples":{"jvm.gc.alloc":{"value":24089200.0}}}}
 * {"metricset":{"timestamp":1545047730692000,"tags":{"name":"G1 Young Generation"},"samples":{"jvm.gc.time":{"value":0.0},"jvm.gc.count":{"value":0.0}}}}
 * {"metricset":{"timestamp":1545047730692000,"tags":{"name":"G1 Old Generation"},  "samples":{"jvm.gc.time":{"value":0.0},"jvm.gc.count":{"value":0.0}}}}
 * </pre>
 */
public class MetricSet implements Recyclable {
    private final Labels.Immutable labels;
    private final ConcurrentMap<String, DoubleSupplier> gauges;
    // low load factor as hash collisions are quite costly when tracking breakdown metrics
    private final ConcurrentMap<String, Timer> timers = new ConcurrentHashMap<>(32, 0.5f, Runtime.getRuntime().availableProcessors());
    private final ConcurrentMap<String, AtomicLong> counters = new ConcurrentHashMap<>(32, 0.5f, Runtime.getRuntime().availableProcessors());
    private final ConcurrentMap<String, Double> rawValues = new ConcurrentHashMap<>();
    private volatile boolean hasNonEmptyTimer;
    private volatile boolean hasNonEmptyCounter;

    MetricSet(Labels.Immutable labels) {
        this(labels, new ConcurrentHashMap<String, DoubleSupplier>());
    }

    MetricSet(Labels.Immutable labels, ConcurrentMap<String, DoubleSupplier> gauges) {
        this.labels = labels;
        this.gauges = gauges;
    }

    void addGauge(String name, DoubleSupplier metric) {
        gauges.putIfAbsent(name, metric);
    }

    @Nullable
    DoubleSupplier getGauge(String name) {
        return gauges.get(name);
    }

    public Labels getLabels() {
        return labels;
    }

    public ConcurrentMap<String, DoubleSupplier> getGauges() {
        return gauges;
    }

    public void addRawMetric(String metric, double value) {
        rawValues.put(metric, value);
    }

    public Timer timer(String timerName) {
        hasNonEmptyTimer = true;
        Timer timer = timers.get(timerName);
        if (timer == null) {
            timers.putIfAbsent(timerName, new Timer());
            timer = timers.get(timerName);
        }
        return timer;
    }

    public void addToCounter(String name, long count) {
        hasNonEmptyCounter = true;
        AtomicLong counter = counters.get(name);
        if (counter == null) {
            counters.putIfAbsent(name, new AtomicLong());
            counter = counters.get(name);
        }
        counter.addAndGet(count);
    }

    public Map<String, Timer> getTimers() {
        return timers;
    }

    public boolean hasContent() {
        return !gauges.isEmpty() || hasNonEmptyTimer || hasNonEmptyCounter || !rawValues.isEmpty();
    }

    /**
     * Should be called only when the MetricSet is inactive
     */
    public void resetState() {
        for (Timer timer : timers.values()) {
            timer.resetState();
        }
        for (AtomicLong counter : counters.values()) {
            counter.set(0);
        }
        rawValues.clear();
        hasNonEmptyTimer = false;
        hasNonEmptyCounter = false;
    }

    public Map<String, AtomicLong> getCounters() {
        return counters;
    }

    public Map<String, Double> getRawMetrics() {
        return rawValues;
    }

}
