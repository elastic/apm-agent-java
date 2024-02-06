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
package co.elastic.apm.agent.micrometer;

import co.elastic.apm.agent.sdk.internal.util.PrivilegedActionUtils;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakSet;
import co.elastic.apm.agent.tracer.configuration.MetricsConfiguration;
import co.elastic.apm.agent.tracer.metrics.DslJsonUtil;
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
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;

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

    private static final int BUFFER_SIZE_LIMIT = 2048;

    private static final Logger logger = LoggerFactory.getLogger(MicrometerMeterRegistrySerializer.class);

    private final DslJson<Object> dslJson = new DslJson<>(new DslJson.Settings<>());
    private final StringBuilder replaceBuilder = new StringBuilder();
    private final MetricsConfiguration config;
    private final WeakSet<Meter> internallyDisabledMeters = WeakConcurrent.buildSet();

    private int maxSerializedSize = 512;

    public MicrometerMeterRegistrySerializer(MetricsConfiguration config) {
        this.config = config;
    }

    Iterable<Meter> getFailedMeters() {
        return internallyDisabledMeters;
    }

    public List<JsonWriter> serialize(final Map<Meter.Id, Meter> metersById, final long epochMicros) {
        List<JsonWriter> serializedMeters = new ArrayList<>();
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
            JsonWriter jw = dslJson.newWriter(maxSerializedSize);
            if (serializeMetricSet(entry.getKey(), entry.getValue(), epochMicros, replaceBuilder, jw)) {
                serializedMeters.add(jw);
                maxSerializedSize = Math.max(Math.min(jw.size(), BUFFER_SIZE_LIMIT), maxSerializedSize);
            }
        }
        return serializedMeters;
    }

    boolean serializeMetricSet(List<Tag> tags, List<Meter> meters, long epochMicros, StringBuilder replaceBuilder, JsonWriter jw) {
        boolean hasSamples = false;
        boolean dedotMetricName = config.isDedotCustomMetrics();
        jw.writeByte(JsonWriter.OBJECT_START);
        {
            DslJsonUtil.writeFieldName("metricset", jw);
            jw.writeByte(JsonWriter.OBJECT_START);
            {
                DslJsonUtil.writeFieldName("timestamp", jw);
                NumberConverter.serialize(epochMicros, jw);
                jw.writeByte(JsonWriter.COMMA);
                serializeTags(tags, replaceBuilder, jw);
                DslJsonUtil.writeFieldName("samples", jw);
                jw.writeByte(JsonWriter.OBJECT_START);

                ClassLoader originalContextCL = PrivilegedActionUtils.getContextClassLoader(Thread.currentThread());
                try {
                    for (int i = 0, size = meters.size(); i < size; i++) {
                        Meter meter = meters.get(i);
                        if (internallyDisabledMeters.contains(meter)) {
                            continue;
                        }
                        try {
                            // Setting the Meter CL as the context class loader during the Meter query operations
                            PrivilegedActionUtils.setContextClassLoader(Thread.currentThread(), PrivilegedActionUtils.getClassLoader(meter.getClass()));
                            if (meter instanceof Timer) {
                                Timer timer = (Timer) meter;
                                hasSamples = serializeTimer(jw, timer.takeSnapshot(), timer.getId(), timer.count(), timer.totalTime(TimeUnit.MICROSECONDS), hasSamples, replaceBuilder, dedotMetricName);
                            } else if (meter instanceof FunctionTimer) {
                                FunctionTimer timer = (FunctionTimer) meter;
                                hasSamples = serializeTimer(jw, null, timer.getId(), (long) timer.count(), timer.totalTime(TimeUnit.MICROSECONDS), hasSamples, replaceBuilder, dedotMetricName);
                            } else if (meter instanceof LongTaskTimer) {
                                LongTaskTimer timer = (LongTaskTimer) meter;
                                hasSamples = serializeTimer(jw, timer.takeSnapshot(), timer.getId(), timer.activeTasks(), timer.duration(TimeUnit.MICROSECONDS), hasSamples, replaceBuilder, dedotMetricName);
                            } else if (meter instanceof DistributionSummary) {
                                DistributionSummary summary = (DistributionSummary) meter;
                                hasSamples = serializeDistributionSummary(jw, summary.takeSnapshot(), summary.getId(), summary.count(), summary.totalAmount(), hasSamples, replaceBuilder, dedotMetricName);
                            } else if (meter instanceof Gauge) {
                                Gauge gauge = (Gauge) meter;
                                hasSamples = serializeValue(gauge.getId(), gauge.value(), hasSamples, jw, replaceBuilder, dedotMetricName);
                            } else if (meter instanceof Counter) {
                                Counter counter = (Counter) meter;
                                hasSamples = serializeValue(counter.getId(), counter.count(), hasSamples, jw, replaceBuilder, dedotMetricName);
                            } else if (meter instanceof FunctionCounter) {
                                FunctionCounter counter = (FunctionCounter) meter;
                                hasSamples = serializeValue(counter.getId(), counter.count(), hasSamples, jw, replaceBuilder, dedotMetricName);
                            }
                        } catch (Throwable throwable) {
                            String meterName = meter.getId().getName();
                            logger.warn("Failed to serialize Micrometer meter \"{}\" with tags {}. This meter will be " +
                                "excluded from serialization going forward.", meterName, tags);
                            logger.debug("Detailed info about failure to register Micrometer meter \"" + meterName +
                                "\": ", throwable);
                            internallyDisabledMeters.add(meter);
                        }
                    }
                } finally {
                    PrivilegedActionUtils.setContextClassLoader(Thread.currentThread(), originalContextCL);
                }
                jw.writeByte(JsonWriter.OBJECT_END);
            }
            jw.writeByte(JsonWriter.OBJECT_END);
        }
        jw.writeByte(JsonWriter.OBJECT_END);
        jw.writeByte(NEW_LINE);
        return hasSamples;
    }

    private static void serializeTags(List<Tag> tags, StringBuilder replaceBuilder, JsonWriter jw) {
        if (tags.isEmpty()) {
            return;
        }
        DslJsonUtil.writeFieldName("tags", jw);
        jw.writeByte(OBJECT_START);
        for (int i = 0, tagsSize = tags.size(); i < tagsSize; i++) {
            Tag tag = tags.get(i);
            if (i > 0) {
                jw.writeByte(COMMA);
            }
            DslJsonUtil.writeStringValue(DslJsonUtil.sanitizePropertyName(tag.getKey(), replaceBuilder), replaceBuilder, jw);
            jw.writeByte(JsonWriter.SEMI);
            DslJsonUtil.writeStringValue(tag.getValue(), replaceBuilder, jw);
        }
        jw.writeByte(OBJECT_END);
        jw.writeByte(COMMA);
    }

    /**
     * Conditionally serializes a {@link Timer} if the total time is valid, i.e. neither Double.NaN nor +/-Infinite
     *
     * @param jw        writer
     * @param histogramSnapshot
     * @param id        meter ID
     * @param count     count
     * @param totalTime total time
     * @param hasValue  whether a value has already been written
     * @param replaceBuilder
     * @param dedotMetricName
     * @return true if a value has been written before, including this one; false otherwise
     */
    private static boolean serializeTimer(JsonWriter jw, HistogramSnapshot histogramSnapshot, Meter.Id id, long count, double totalTime, boolean hasValue, StringBuilder replaceBuilder, boolean dedotMetricName) {
        if (isValidValue(totalTime)) {
            if (hasValue) jw.writeByte(JsonWriter.COMMA);
            serializeValue(id, ".count", count, jw, replaceBuilder, dedotMetricName);
            jw.writeByte(JsonWriter.COMMA);
            serializeValue(id, ".sum.us", totalTime, jw, replaceBuilder, dedotMetricName);
            if (histogramSnapshot != null && histogramSnapshot.histogramCounts().length > 0) {
                jw.writeByte(JsonWriter.COMMA);
                serializeHistogram(id, histogramSnapshot, jw, replaceBuilder, dedotMetricName);
            }
            return true;
        }
        return hasValue;
    }

    /**
     * Conditionally serializes a {@link DistributionSummary} if the total amount is valid, i.e. neither Double.NaN nor +/-Infinite
     *
     * @param jw          writer
     * @param histogramSnapshot
     * @param id          meter ID
     * @param count       count
     * @param totalAmount total amount of recorded events
     * @param hasValue    whether a value has already been written
     * @param replaceBuilder
     * @param dedotMetricName
     * @return true if a value has been written before, including this one; false otherwise
     */
    private static boolean serializeDistributionSummary(JsonWriter jw, HistogramSnapshot histogramSnapshot, Meter.Id id, long count, double totalAmount, boolean hasValue, StringBuilder replaceBuilder, boolean dedotMetricName) {
        if (isValidValue(totalAmount)) {
            if (hasValue) jw.writeByte(JsonWriter.COMMA);
            serializeValue(id, ".count", count, jw, replaceBuilder, dedotMetricName);
            jw.writeByte(JsonWriter.COMMA);
            serializeValue(id, ".sum", totalAmount, jw, replaceBuilder, dedotMetricName);
            if (histogramSnapshot != null && histogramSnapshot.histogramCounts().length > 0) {
                jw.writeByte(JsonWriter.COMMA);
                serializeHistogram(id, histogramSnapshot, jw, replaceBuilder, dedotMetricName);
            }
            return true;
        }
        return hasValue;
    }

    private static void serializeHistogram(Meter.Id id, HistogramSnapshot histogramSnapshot, JsonWriter jw, StringBuilder replaceBuilder, boolean dedotMetricName) {
        if (histogramSnapshot == null) {
            return;
        }
        String suffix = ".histogram";
        CountAtBucket[] bucket = histogramSnapshot.histogramCounts();
        serializeObjectStart(id.getName(), "values", suffix, jw, replaceBuilder, dedotMetricName);
        jw.writeByte(JsonWriter.ARRAY_START);
        if (bucket.length > 0) {
            NumberConverter.serialize(bucket[0].bucket(), jw);
            for (int i = 1; i < bucket.length; i++) {
                jw.writeByte(JsonWriter.COMMA);
                NumberConverter.serialize(bucket[i].bucket(), jw);
            }
        }
        jw.writeByte(JsonWriter.ARRAY_END);
        jw.writeByte(JsonWriter.COMMA);
        jw.writeByte(JsonWriter.QUOTE);
        jw.writeAscii("counts");
        jw.writeByte(JsonWriter.QUOTE);
        jw.writeByte(JsonWriter.SEMI);
        jw.writeByte(JsonWriter.ARRAY_START);
        // Micrometer bucket counts are cumulative: E.g. the count at bucket with upper
        // boundary X is the total number of observations smaller than X
        // including values which have already been counted for smaller buckets.
        // Elastic however expects non-cumulative bucket counts
        if (bucket.length > 0) {
            NumberConverter.serialize((long) bucket[0].count(), jw);
            double prevBucketCount = bucket[0].count();
            for (int i = 1; i < bucket.length; i++) {
                jw.writeByte(JsonWriter.COMMA);
                NumberConverter.serialize((long) (bucket[i].count() - prevBucketCount), jw);
                prevBucketCount = bucket[i].count();
            }
        }
        jw.writeByte(JsonWriter.ARRAY_END);

        jw.writeByte(JsonWriter.COMMA);
        jw.writeAscii("\"type\":\"histogram\"");

        jw.writeByte(JsonWriter.OBJECT_END);
    }

    private static void serializeValue(Meter.Id id, String suffix, long value, JsonWriter jw, StringBuilder replaceBuilder, boolean dedotMetricName) {
        serializeValueStart(id.getName(), suffix, jw, replaceBuilder, dedotMetricName);
        NumberConverter.serialize(value, jw);
        jw.writeByte(JsonWriter.OBJECT_END);
    }

    /**
     * Conditionally serializes a {@code double} value if the value is valid, i.e. neither Double.NaN nor +/-Infinite
     *
     * @param id       meter ID
     * @param value    meter value
     * @param hasValue whether a value has already been written
     * @param jw       writer
     * @param replaceBuilder
     * @param dedotMetricName
     * @return true if a value has been written before, including this one; false otherwise
     */
    private static boolean serializeValue(Meter.Id id, double value, boolean hasValue, JsonWriter jw, StringBuilder replaceBuilder, boolean dedotMetricName) {
        if (isValidValue(value)) {
            if (hasValue) jw.writeByte(JsonWriter.COMMA);
            serializeValue(id, "", value, jw, replaceBuilder, dedotMetricName);
            return true;
        }
        return hasValue;
    }

    private static void serializeValue(Meter.Id id, String suffix, double value, JsonWriter jw, StringBuilder replaceBuilder, boolean dedotMetricName) {
        serializeValueStart(id.getName(), suffix, jw, replaceBuilder, dedotMetricName);
        NumberConverter.serialize(value, jw);
        jw.writeByte(JsonWriter.OBJECT_END);
    }

    private static void serializeValueStart(String key, String suffix, JsonWriter jw, StringBuilder replaceBuilder, boolean dedotMetricName) {
        serializeObjectStart(key, "value",  suffix, jw, replaceBuilder, dedotMetricName);
    }

    private static void serializeObjectStart(String key, String objectName, String suffix, JsonWriter jw, StringBuilder replaceBuilder, boolean dedotMetricName) {
        replaceBuilder.setLength(0);
        if (dedotMetricName) {
            DslJsonUtil.sanitizePropertyName(key, replaceBuilder);
        } else {
            replaceBuilder.append(key);
        }
        if (suffix != null) {
            if (replaceBuilder.length() == 0) {
                replaceBuilder.append(key);
            }
            replaceBuilder.append(suffix);
        }
        jw.writeString(replaceBuilder);

        jw.writeByte(JsonWriter.SEMI);
        jw.writeByte(JsonWriter.OBJECT_START);
        jw.writeByte(JsonWriter.QUOTE);
        jw.writeAscii(objectName);
        jw.writeByte(JsonWriter.QUOTE);
        jw.writeByte(JsonWriter.SEMI);
    }

    private static boolean isValidValue(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
