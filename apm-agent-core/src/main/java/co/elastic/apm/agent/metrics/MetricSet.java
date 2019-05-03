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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
public class MetricSet {
    private final Labels labels;
    private final ConcurrentMap<String, DoubleSupplier> gauges = new ConcurrentHashMap<>();
    // low load factor as hash collisions are quite costly when tracking breakdown metrics
    private final ConcurrentMap<String, Timer> timers = new ConcurrentHashMap<>(32, 0.5f, Runtime.getRuntime().availableProcessors());
    private volatile boolean hasNonEmptyTimer;

    public MetricSet(Labels labels) {
        this.labels = labels;
    }

    public void add(String name, DoubleSupplier metric) {
        gauges.putIfAbsent(name, metric);
    }

    DoubleSupplier get(String name) {
        return gauges.get(name);
    }

    public Labels getLabels() {
        return labels;
    }

    public Map<String, DoubleSupplier> getGauges() {
        return gauges;
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

    public Map<String, Timer> getTimers() {
        return timers;
    }

    public boolean hasContent() {
        return !gauges.isEmpty() || hasNonEmptyTimer;
    }

    public void onAfterReport() {
        hasNonEmptyTimer = false;
    }
}
