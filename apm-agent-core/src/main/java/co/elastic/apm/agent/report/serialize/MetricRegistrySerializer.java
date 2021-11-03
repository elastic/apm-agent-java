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
package co.elastic.apm.agent.report.serialize;

import co.elastic.apm.agent.metrics.DoubleSupplier;
import co.elastic.apm.agent.metrics.MetricSet;
import co.elastic.apm.agent.metrics.Timer;
import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonWriter;
import com.dslplatform.json.NumberConverter;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class MetricRegistrySerializer {

    private static final byte NEW_LINE = '\n';

    private static final int BUFFER_SIZE_LIMIT = 2048;

    private final DslJson<Object> dslJson = new DslJson<>(new DslJson.Settings<>());
    private final StringBuilder replaceBuilder = new StringBuilder();
    private int maxSerializedSize = 512;

    /**
     * Creates a JSON writer, serializes the given metric set into it and returns it. If the serialized metric-set
     * does not contain samples, the method returns null.
     * @param metricSet a metric-set to serialize
     * @return the serialized metric-set or {@code null} if no samples were serialized
     */
    @Nullable
    public JsonWriter serialize(MetricSet metricSet, List<String> serviceNames) {
        JsonWriter jw = dslJson.newWriter(maxSerializedSize);
        boolean hasSamples = serialize(metricSet, !serviceNames.isEmpty() ? serviceNames.get(0) : null, jw);
        for (int i = 1; i < serviceNames.size(); ++i) {
            serialize(metricSet, serviceNames.get(i), jw);
        }
        if (hasSamples) {
            maxSerializedSize = Math.max(Math.min(jw.size(), BUFFER_SIZE_LIMIT), maxSerializedSize);
            return jw;
        }
        return null;
    }

    private boolean serialize(MetricSet metricSet, String serviceName, JsonWriter jw) {
        final long timestamp = System.currentTimeMillis() * 1000;
        return serialize(metricSet, timestamp, serviceName, replaceBuilder, jw);
    }

    private static boolean serialize(MetricSet metricSet, long epochMicros, String serviceName, StringBuilder replaceBuilder, JsonWriter jw) {
        boolean hasSamples;
        jw.writeByte(JsonWriter.OBJECT_START);
        {
            DslJsonSerializer.writeFieldName("metricset", jw);
            jw.writeByte(JsonWriter.OBJECT_START);
            {
                DslJsonSerializer.writeFieldName("timestamp", jw);
                NumberConverter.serialize(epochMicros, jw);
                jw.writeByte(JsonWriter.COMMA);
                DslJsonSerializer.serializeLabels(metricSet.getLabels(), serviceName, replaceBuilder, jw);
                DslJsonSerializer.writeFieldName("samples", jw);
                jw.writeByte(JsonWriter.OBJECT_START);
                hasSamples = serializeGauges(metricSet.getGauges(), jw);
                hasSamples |= serializeTimers(metricSet.getTimers(), hasSamples, jw);
                hasSamples |= serializeCounters(metricSet.getCounters(), hasSamples, jw);
                jw.writeByte(JsonWriter.OBJECT_END);
            }
            jw.writeByte(JsonWriter.OBJECT_END);
        }
        jw.writeByte(JsonWriter.OBJECT_END);
        jw.writeByte(NEW_LINE);
        return hasSamples;
    }

    private static boolean serializeGauges(Map<String, DoubleSupplier> gauges, JsonWriter jw) {
        boolean hasSamples = false;
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
                    hasSamples = true;
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
            return hasSamples;
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
                    serializeTimer(kv.getKey(), value, jw);
                    hasSamples = true;
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

    private static boolean serializeCounters(Map<String, AtomicLong> counters, boolean hasSamples, JsonWriter jw) {
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
                    hasSamples = true;
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
        return hasSamples;
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
