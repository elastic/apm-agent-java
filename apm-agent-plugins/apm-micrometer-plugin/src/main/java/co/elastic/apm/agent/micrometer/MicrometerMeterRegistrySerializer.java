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
package co.elastic.apm.agent.micrometer;

import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonWriter;
import com.dslplatform.json.NumberConverter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.dslplatform.json.JsonWriter.COMMA;
import static com.dslplatform.json.JsonWriter.OBJECT_END;
import static com.dslplatform.json.JsonWriter.OBJECT_START;

public class MicrometerMeterRegistrySerializer {

    private static final byte NEW_LINE = (byte) '\n';
    private final DslJson<Object> dslJson = new DslJson<>(new DslJson.Settings<>());
    private final StringBuilder replaceBuilder = new StringBuilder();
    private int previousSize = 512;

    public JsonWriter serialize(final Map<Meter.Id, Meter> metersById, final long epochMicros) {
        JsonWriter jw = dslJson.newWriter((int) (previousSize * 1.25));
        serialize(metersById, epochMicros, replaceBuilder, jw);
        previousSize = jw.size();
        return jw;
    }

    static void serialize(final Map<Meter.Id, Meter> metersById, final long epochMicros, final StringBuilder replaceBuilder, final JsonWriter jw) {
        final Map<List<Tag>, List<Meter>> metersGroupedByTags = new HashMap<>();
        for (Map.Entry<Meter.Id, Meter> entry : metersById.entrySet()) {
            List<Tag> tags = entry.getKey().getTags();
            List<Meter> meters = metersGroupedByTags.get(tags);
            if (meters == null) {
                meters = new ArrayList<>(1);
                metersGroupedByTags.put(tags, meters);
            }
            meters.add(entry.getValue());
        }
        for (Map.Entry<List<Tag>, List<Meter>> entry : metersGroupedByTags.entrySet()) {
            serializeMetricSet(entry.getKey(), entry.getValue(), epochMicros, replaceBuilder, jw);
            jw.writeByte(NEW_LINE);
        }
    }

    static void serializeMetricSet(List<Tag> tags, List<Meter> meters, long epochMicros, StringBuilder replaceBuilder, JsonWriter jw) {
        jw.writeByte(JsonWriter.OBJECT_START);
        {
            DslJsonSerializer.writeFieldName("metricset", jw);
            jw.writeByte(JsonWriter.OBJECT_START);
            {
                DslJsonSerializer.writeFieldName("timestamp", jw);
                NumberConverter.serialize(epochMicros, jw);
                jw.writeByte(JsonWriter.COMMA);
                serializeTags(tags, replaceBuilder, jw);
                DslJsonSerializer.writeFieldName("samples", jw);
                jw.writeByte(JsonWriter.OBJECT_START);
                boolean hasValue = false;
                for (int i = 0, size = meters.size(); i < size; i++) {
                    Meter meter = meters.get(i);
                    if (meter instanceof Timer) {
                        if (hasValue) jw.writeByte(JsonWriter.COMMA);
                        Timer timer = (Timer) meter;
                        serializeTimer(jw, timer.getId(), timer.count(), timer.totalTime(TimeUnit.MICROSECONDS));
                        hasValue = true;
                    } else if (meter instanceof FunctionTimer) {
                        if (hasValue) jw.writeByte(JsonWriter.COMMA);
                        FunctionTimer timer = (FunctionTimer) meter;
                        serializeTimer(jw, timer.getId(), (long) timer.count(), timer.totalTime(TimeUnit.MICROSECONDS));
                        hasValue = true;
                    } else if (meter instanceof LongTaskTimer) {
                        if (hasValue) jw.writeByte(JsonWriter.COMMA);
                        LongTaskTimer timer = (LongTaskTimer) meter;
                        serializeTimer(jw, timer.getId(), timer.activeTasks(), timer.duration(TimeUnit.MICROSECONDS));
                        hasValue = true;
                    } else if (meter instanceof DistributionSummary) {
                        if (hasValue) jw.writeByte(JsonWriter.COMMA);
                        DistributionSummary timer = (DistributionSummary) meter;
                        serializeDistributionSummary(jw, timer.getId(), timer.count(), timer.totalAmount());
                        hasValue = true;
                    } else if (meter instanceof Gauge) {
                        Gauge gauge = (Gauge) meter;

                        // gauge values can be Double.NaN or +/-Infinite
                        if (isValidValue(gauge.value())) {
                            if (hasValue) jw.writeByte(JsonWriter.COMMA);
                            serializeValue(gauge.getId(), gauge.value(), jw);
                            hasValue = true;
                        }

                    } else if (meter instanceof Counter) {
                        if (hasValue) jw.writeByte(JsonWriter.COMMA);
                        Counter counter = (Counter) meter;
                        serializeValue(counter.getId(), counter.count(), jw);
                        hasValue = true;
                    } else if (meter instanceof FunctionCounter) {
                        if (hasValue) jw.writeByte(JsonWriter.COMMA);
                        FunctionCounter counter = (FunctionCounter) meter;
                        serializeValue(counter.getId(), counter.count(), jw);
                        hasValue = true;
                    }
                }
                jw.writeByte(JsonWriter.OBJECT_END);
            }
            jw.writeByte(JsonWriter.OBJECT_END);
        }
        jw.writeByte(JsonWriter.OBJECT_END);
    }

    private static void serializeTags(List<Tag> tags, StringBuilder replaceBuilder, JsonWriter jw) {
        if (tags.isEmpty()) {
            return;
        }
        DslJsonSerializer.writeFieldName("tags", jw);
        jw.writeByte(OBJECT_START);
        for (int i = 0, tagsSize = tags.size(); i < tagsSize; i++) {
            Tag tag = tags.get(i);
            if (i > 0) {
                jw.writeByte(COMMA);
            }
            DslJsonSerializer.writeLastField(tag.getKey(), tag.getValue(), replaceBuilder, jw);
        }
        jw.writeByte(OBJECT_END);
        jw.writeByte(COMMA);
    }

    private static void serializeTimer(JsonWriter jw, Meter.Id id, long count, double totalTime) {
        serializeValue(id, ".count", count, jw);
        jw.writeByte(JsonWriter.COMMA);
        serializeValue(id, ".sum.us", totalTime, jw);
    }

    private static void serializeDistributionSummary(JsonWriter jw, Meter.Id id, long count, double totalTime) {
        serializeValue(id, ".count", count, jw);
        jw.writeByte(JsonWriter.COMMA);
        serializeValue(id, ".sum", totalTime, jw);
    }

    private static void serializeValue(Meter.Id id, String suffix, long value, JsonWriter jw) {
        serializeValueStart(id.getName(), suffix, jw);
        NumberConverter.serialize(value, jw);
        jw.writeByte(JsonWriter.OBJECT_END);
    }

    private static void serializeValue(Meter.Id id, double value, JsonWriter jw) {
        serializeValue(id, "", value, jw);
    }

    private static void serializeValue(Meter.Id id, String suffix, double value, JsonWriter jw) {
        serializeValueStart(id.getName(), suffix, jw);
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

    private static boolean isValidValue(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
