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
package co.elastic.apm.agent.report.serialize;

import co.elastic.apm.agent.context.AbstractLifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.metrics.DoubleSupplier;
import co.elastic.apm.agent.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.metrics.MetricSet;
import co.elastic.apm.agent.metrics.Timer;
import co.elastic.apm.agent.report.Reporter;
import co.elastic.apm.agent.report.ReporterConfiguration;
import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonWriter;
import com.dslplatform.json.Nullable;
import com.dslplatform.json.NumberConverter;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class MetricRegistryReporter extends AbstractLifecycleListener implements MetricRegistry.MetricsReporter, Runnable {

    private static final byte NEW_LINE = '\n';

    private final JsonWriter jsonWriter = new DslJson<>(new DslJson.Settings<>()).newWriter();
    private final StringBuilder replaceBuilder = new StringBuilder();
    private final Reporter reporter;
    private final ElasticApmTracer tracer;
    private final MetricRegistry metricRegistry;

    public MetricRegistryReporter(ElasticApmTracer tracer) {
        this.tracer = tracer;
        this.reporter = tracer.getReporter();
        this.metricRegistry = tracer.getMetricRegistry();
    }

    @Override
    public void start(ElasticApmTracer tracer) {
        long intervalMs = tracer.getConfig(ReporterConfiguration.class).getMetricsIntervalMs();
        if (intervalMs > 0) {
            tracer.getSharedSingleThreadedPool().scheduleAtFixedRate(this, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void run() {
        if (tracer.isRunning()) {
            metricRegistry.flipPhaseAndReport(this);
        } else {
            metricRegistry.flipPhaseAndReport(null);
        }
    }

    @Override
    public void report(Map<? extends Labels, MetricSet> metricSets) {
        byte[] serialized = serialize(metricSets);
        if (serialized != null) {
            reporter.report(serialized);
        }
    }

    @Nullable
    public byte[] serialize(Map<? extends Labels, MetricSet> metricSets) {
        serialize(metricSets, replaceBuilder, jsonWriter);
        if (jsonWriter.size() == 0) {
            return null;
        }
        return jsonWriter.toByteArray();
    }

    public static void serialize(Map<? extends Labels, MetricSet> metricSets, StringBuilder replaceBuilder, JsonWriter jw) {
        final long timestamp = System.currentTimeMillis() * 1000;
        for (MetricSet metricSet : metricSets.values()) {
            if (metricSet.hasContent()) {
                serializeMetricSet(metricSet, timestamp, replaceBuilder, jw);
                jw.writeByte(NEW_LINE);
            }
        }
    }

    static void serializeMetricSet(MetricSet metricSet, long epochMicros, StringBuilder replaceBuilder, JsonWriter jw) {
        jw.writeByte(JsonWriter.OBJECT_START);
        {
            DslJsonSerializer.writeFieldName("metricset", jw);
            jw.writeByte(JsonWriter.OBJECT_START);
            {
                DslJsonSerializer.writeFieldName("timestamp", jw);
                NumberConverter.serialize(epochMicros, jw);
                jw.writeByte(JsonWriter.COMMA);
                DslJsonSerializer.serializeLabels(metricSet.getLabels(), replaceBuilder, jw);
                DslJsonSerializer.writeFieldName("samples", jw);
                jw.writeByte(JsonWriter.OBJECT_START);
                boolean hasSamples = serializeGauges(metricSet.getGauges(), jw);
                hasSamples |= serializeTimers(metricSet.getTimers(), hasSamples, jw);
                serializeCounters(metricSet.getCounters(), hasSamples, jw);
                jw.writeByte(JsonWriter.OBJECT_END);
            }
            jw.writeByte(JsonWriter.OBJECT_END);
        }
        jw.writeByte(JsonWriter.OBJECT_END);
    }

    private static boolean serializeGauges(Map<String, DoubleSupplier> gauges, JsonWriter jw) {
        final int size = gauges.size();
        if (size > 0) {
            final Iterator<Map.Entry<String, DoubleSupplier>> iterator = gauges.entrySet().iterator();

            // serialize first valid value
            double value = Double.NaN;
            while (iterator.hasNext() && !isValid(value)) {
                Map.Entry<String, DoubleSupplier> kv = iterator.next();
                value = kv.getValue().get();
                if (isValid(value)) {
                    serializeValue(kv.getKey(), value, jw);
                }
            }

            // serialize rest
            while (iterator.hasNext()) {
                Map.Entry<String, DoubleSupplier> kv = iterator.next();
                value = kv.getValue().get();
                if (isValid(value)) {
                    jw.writeByte(JsonWriter.COMMA);
                    serializeValue(kv.getKey(), value, jw);
                }
            }
            return true;
        }
        return false;
    }

    private static boolean serializeTimers(Map<String, Timer> timers, boolean hasSamples, JsonWriter jw) {
        final int size = timers.size();
        if (size > 0) {
            final Iterator<Map.Entry<String, Timer>> iterator = timers.entrySet().iterator();

            // serialize first valid value
            Timer value = null;
            while (iterator.hasNext() && value == null) {
                Map.Entry<String, Timer> kv = iterator.next();
                if (kv.getValue().hasContent()) {
                    value = kv.getValue();
                    if (hasSamples) {
                        jw.writeByte(JsonWriter.COMMA);
                    }
                    hasSamples = true;
                    serializeTimer(kv.getKey(), value, jw);
                }
            }

            // serialize rest
            while (iterator.hasNext()) {
                Map.Entry<String, Timer> kv = iterator.next();
                value = kv.getValue();
                if (value.hasContent()) {
                    jw.writeByte(JsonWriter.COMMA);
                    serializeTimer(kv.getKey(), value, jw);
                }
            }
        }
        return hasSamples;
    }

    private static void serializeCounters(Map<String, AtomicLong> counters, boolean hasSamples, JsonWriter jw) {
        final int size = counters.size();
        if (size > 0) {
            final Iterator<Map.Entry<String, AtomicLong>> iterator = counters.entrySet().iterator();

            // serialize first valid value
            AtomicLong value = null;
            while (iterator.hasNext() && value == null) {
                Map.Entry<String, AtomicLong> kv = iterator.next();
                if (kv.getValue().get() > 0) {
                    value = kv.getValue();
                    if (hasSamples) {
                        jw.writeByte(JsonWriter.COMMA);
                    }
                    serializeCounter(kv.getKey(), value, jw);
                }
            }

            // serialize rest
            while (iterator.hasNext()) {
                Map.Entry<String, AtomicLong> kv = iterator.next();
                value = kv.getValue();
                if (kv.getValue().get() > 0) {
                    jw.writeByte(JsonWriter.COMMA);
                    serializeCounter(kv.getKey(), value, jw);
                }
            }
        }
    }

    private static void serializeCounter(String key, AtomicLong value, JsonWriter jw) {
        serializeValueStart(key, "", jw);
        NumberConverter.serialize(value.get(), jw);
        jw.writeByte(JsonWriter.OBJECT_END);
    }

    private static boolean isValid(double value) {
        return !Double.isInfinite(value) && !Double.isNaN(value);
    }

    private static void serializeTimer(String key, Timer timer, JsonWriter jw) {
        serializeValue(key, ".count", timer.getCount(), jw);
        jw.writeByte(JsonWriter.COMMA);
        serializeValue(key, ".sum.us", timer.getTotalTimeUs(), jw);
    }

    private static void serializeValue(String key, double value, JsonWriter jw) {
        serializeValue(key, "", value, jw);
    }

    private static void serializeValue(String key, String suffix, double value, JsonWriter jw) {
        serializeValueStart(key, suffix, jw);
        NumberConverter.serialize(value, jw);
        jw.writeByte(JsonWriter.OBJECT_END);
    }

    private static void serializeValue(String key, String suffix, long value, JsonWriter jw) {
        serializeValueStart(key, suffix, jw);
        NumberConverter.serialize(value, jw);
        jw.writeByte(JsonWriter.OBJECT_END);
    }

    private static void serializeValueStart(String key, String suffix, JsonWriter jw) {
        jw.writeByte(JsonWriter.QUOTE);
        jw.writeAscii(key);
        jw.writeAscii(suffix);
        jw.writeByte(JsonWriter.QUOTE);
        jw.writeByte(JsonWriter.SEMI);
        jw.writeByte(JsonWriter.OBJECT_START);
        jw.writeByte(JsonWriter.QUOTE);
        jw.writeAscii("value");
        jw.writeByte(JsonWriter.QUOTE);
        jw.writeByte(JsonWriter.SEMI);
    }
}
