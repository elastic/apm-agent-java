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
package co.elastic.apm.agent.report.serialize;

import co.elastic.apm.agent.metrics.DoubleSupplier;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.metrics.MetricSet;
import co.elastic.apm.agent.metrics.Timer;
import com.dslplatform.json.JsonWriter;
import com.dslplatform.json.NumberConverter;

import java.util.Iterator;
import java.util.Map;

public class MetricRegistrySerializer {

    private static final byte NEW_LINE = '\n';

    public static void serialize(MetricRegistry metricRegistry, StringBuilder replaceBuilder, JsonWriter jw) {
        final long timestamp = System.currentTimeMillis() * 1000;
        for (MetricSet metricSet : metricRegistry.getMetricSets().values()) {
            if (metricSet.hasContent()) {
                serializeMetricSet(metricSet, timestamp, replaceBuilder, jw);
                metricSet.onAfterReport();
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
                final boolean hasSamples = serializeGauges(metricSet.getGauges(), jw);
                serializeTimers(metricSet.getTimers(), hasSamples, jw);
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

    private static void serializeTimers(Map<String, Timer> timers, boolean hasSamples, JsonWriter jw) {

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
    }

    private static boolean isValid(double value) {
        return !Double.isInfinite(value) && !Double.isNaN(value);
    }

    private static void serializeTimer(String key, Timer timer, JsonWriter jw) {
        serializeValue(key, ".count", timer.getCount(), jw);
        jw.writeByte(JsonWriter.COMMA);
        serializeValue(key, ".sum", timer.getTotalTimeMs(), jw);
        timer.resetState();
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
