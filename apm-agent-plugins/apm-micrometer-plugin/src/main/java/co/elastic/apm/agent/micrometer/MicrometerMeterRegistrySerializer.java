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
import co.elastic.apm.agent.tracer.reporting.DataWriter;
import co.elastic.apm.agent.tracer.reporting.ReportingTracer;
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

import static co.elastic.apm.agent.tracer.reporting.DataWriter.StructureType.ARRAY_END;
import static co.elastic.apm.agent.tracer.reporting.DataWriter.StructureType.ARRAY_START;
import static co.elastic.apm.agent.tracer.reporting.DataWriter.StructureType.COMMA;
import static co.elastic.apm.agent.tracer.reporting.DataWriter.StructureType.NEW_LINE;
import static co.elastic.apm.agent.tracer.reporting.DataWriter.StructureType.OBJECT_END;
import static co.elastic.apm.agent.tracer.reporting.DataWriter.StructureType.OBJECT_START;
import static co.elastic.apm.agent.tracer.reporting.DataWriter.StructureType.QUOTE;
import static co.elastic.apm.agent.tracer.reporting.DataWriter.StructureType.SEMI;

public class MicrometerMeterRegistrySerializer {

    private static final int BUFFER_SIZE_LIMIT = 2048;

    private static final Logger logger = LoggerFactory.getLogger(MicrometerMeterRegistrySerializer.class);
    private final StringBuilder replaceBuilder = new StringBuilder();
    private final MetricsConfiguration config;
    private final ReportingTracer tracer;
    private final WeakSet<Meter> internallyDisabledMeters = WeakConcurrent.buildSet();

    private int maxSerializedSize = 512;

    public MicrometerMeterRegistrySerializer(MetricsConfiguration config, ReportingTracer tracer) {
        this.config = config;
        this.tracer = tracer;
    }

    Iterable<Meter> getFailedMeters() {
        return internallyDisabledMeters;
    }

    public List<DataWriter> serialize(final Map<Meter.Id, Meter> metersById, final long epochMicros) {
        List<DataWriter> serializedMeters = new ArrayList<>();
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
            DataWriter writer = tracer.newWriter(maxSerializedSize);
            if (serializeMetricSet(entry.getKey(), entry.getValue(), epochMicros, replaceBuilder, writer)) {
                serializedMeters.add(writer);
                maxSerializedSize = Math.max(Math.min(writer.size(), BUFFER_SIZE_LIMIT), maxSerializedSize);
            }
        }
        return serializedMeters;
    }

    boolean serializeMetricSet(List<Tag> tags, List<Meter> meters, long epochMicros, StringBuilder replaceBuilder, DataWriter writer) {
        boolean hasSamples = false;
        boolean dedotMetricName = config.isDedotCustomMetrics();
        writer.write(OBJECT_START);
        {
            writer.writeFieldName("metricset");
            writer.write(OBJECT_START);
            {
                writer.writeFieldName("timestamp");
                writer.serialize(epochMicros);
                writer.write(COMMA);
                serializeTags(tags, replaceBuilder, writer);
                writer.writeFieldName("samples");
                writer.write(OBJECT_START);

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
                                hasSamples = serializeTimer(writer, timer.takeSnapshot(), timer.getId(), timer.count(), timer.totalTime(TimeUnit.MICROSECONDS), hasSamples, replaceBuilder, dedotMetricName);
                            } else if (meter instanceof FunctionTimer) {
                                FunctionTimer timer = (FunctionTimer) meter;
                                hasSamples = serializeTimer(writer, null, timer.getId(), (long) timer.count(), timer.totalTime(TimeUnit.MICROSECONDS), hasSamples, replaceBuilder, dedotMetricName);
                            } else if (meter instanceof LongTaskTimer) {
                                LongTaskTimer timer = (LongTaskTimer) meter;
                                hasSamples = serializeTimer(writer, timer.takeSnapshot(), timer.getId(), timer.activeTasks(), timer.duration(TimeUnit.MICROSECONDS), hasSamples, replaceBuilder, dedotMetricName);
                            } else if (meter instanceof DistributionSummary) {
                                DistributionSummary summary = (DistributionSummary) meter;
                                hasSamples = serializeDistributionSummary(writer, summary.takeSnapshot(), summary.getId(), summary.count(), summary.totalAmount(), hasSamples, replaceBuilder, dedotMetricName);
                            } else if (meter instanceof Gauge) {
                                Gauge gauge = (Gauge) meter;
                                hasSamples = serializeValue(gauge.getId(), gauge.value(), hasSamples, writer, replaceBuilder, dedotMetricName);
                            } else if (meter instanceof Counter) {
                                Counter counter = (Counter) meter;
                                hasSamples = serializeValue(counter.getId(), counter.count(), hasSamples, writer, replaceBuilder, dedotMetricName);
                            } else if (meter instanceof FunctionCounter) {
                                FunctionCounter counter = (FunctionCounter) meter;
                                hasSamples = serializeValue(counter.getId(), counter.count(), hasSamples, writer, replaceBuilder, dedotMetricName);
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
                writer.write(OBJECT_END);
            }
            writer.write(OBJECT_END);
        }
        writer.write(OBJECT_END);
        writer.write(NEW_LINE);
        return hasSamples;
    }

    private static void serializeTags(List<Tag> tags, StringBuilder replaceBuilder, DataWriter writer) {
        if (tags.isEmpty()) {
            return;
        }
        writer.writeFieldName("tags");
        writer.write(OBJECT_START);
        for (int i = 0, tagsSize = tags.size(); i < tagsSize; i++) {
            Tag tag = tags.get(i);
            if (i > 0) {
                writer.write(COMMA);
            }
            writer.writeStringValue(writer.sanitizePropertyName(tag.getKey(), replaceBuilder), replaceBuilder);
            writer.write(SEMI);
            writer.writeStringValue(tag.getValue(), replaceBuilder);
        }
        writer.write(OBJECT_END);
        writer.write(COMMA);
    }

    /**
     * Conditionally serializes a {@link Timer} if the total time is valid, i.e. neither Double.NaN nor +/-Infinite
     *
     * @param writer        writer
     * @param histogramSnapshot
     * @param id        meter ID
     * @param count     count
     * @param totalTime total time
     * @param hasValue  whether a value has already been written
     * @param replaceBuilder
     * @param dedotMetricName
     * @return true if a value has been written before, including this one; false otherwise
     */
    private static boolean serializeTimer(DataWriter writer, HistogramSnapshot histogramSnapshot, Meter.Id id, long count, double totalTime, boolean hasValue, StringBuilder replaceBuilder, boolean dedotMetricName) {
        if (isValidValue(totalTime)) {
            if (hasValue) writer.write(COMMA);
            serializeValue(id, ".count", count, writer, replaceBuilder, dedotMetricName);
            writer.write(COMMA);
            serializeValue(id, ".sum.us", totalTime, writer, replaceBuilder, dedotMetricName);
            if (histogramSnapshot != null && histogramSnapshot.histogramCounts().length > 0) {
                writer.write(COMMA);
                serializeHistogram(id, histogramSnapshot, writer, replaceBuilder, dedotMetricName);
            }
            return true;
        }
        return hasValue;
    }

    /**
     * Conditionally serializes a {@link DistributionSummary} if the total amount is valid, i.e. neither Double.NaN nor +/-Infinite
     *
     * @param writer          writer
     * @param histogramSnapshot
     * @param id          meter ID
     * @param count       count
     * @param totalAmount total amount of recorded events
     * @param hasValue    whether a value has already been written
     * @param replaceBuilder
     * @param dedotMetricName
     * @return true if a value has been written before, including this one; false otherwise
     */
    private static boolean serializeDistributionSummary(DataWriter writer, HistogramSnapshot histogramSnapshot, Meter.Id id, long count, double totalAmount, boolean hasValue, StringBuilder replaceBuilder, boolean dedotMetricName) {
        if (isValidValue(totalAmount)) {
            if (hasValue) writer.write(COMMA);
            serializeValue(id, ".count", count, writer, replaceBuilder, dedotMetricName);
            writer.write(COMMA);
            serializeValue(id, ".sum", totalAmount, writer, replaceBuilder, dedotMetricName);
            if (histogramSnapshot != null && histogramSnapshot.histogramCounts().length > 0) {
                writer.write(COMMA);
                serializeHistogram(id, histogramSnapshot, writer, replaceBuilder, dedotMetricName);
            }
            return true;
        }
        return hasValue;
    }

    private static void serializeHistogram(Meter.Id id, HistogramSnapshot histogramSnapshot, DataWriter writer, StringBuilder replaceBuilder, boolean dedotMetricName) {
        if (histogramSnapshot == null) {
            return;
        }
        String suffix = ".histogram";
        CountAtBucket[] bucket = histogramSnapshot.histogramCounts();
        serializeObjectStart(id.getName(), "values", suffix, writer, replaceBuilder, dedotMetricName);
        writer.write(ARRAY_START);
        if (bucket.length > 0) {
            writer.serialize(bucket[0].bucket());
            for (int i = 1; i < bucket.length; i++) {
                writer.write(COMMA);
                writer.serialize(bucket[i].bucket());
            }
        }
        writer.write(ARRAY_END);
        writer.write(COMMA);
        writer.write(QUOTE);
        writer.writeAscii("counts");
        writer.write(QUOTE);
        writer.write(SEMI);
        writer.write(ARRAY_START);
        // Micrometer bucket counts are cumulative: E.g. the count at bucket with upper
        // boundary X is the total number of observations smaller than X
        // including values which have already been counted for smaller buckets.
        // Elastic however expects non-cumulative bucket counts
        if (bucket.length > 0) {
            writer.serialize((long) bucket[0].count());
            double prevBucketCount = bucket[0].count();
            for (int i = 1; i < bucket.length; i++) {
                writer.write(COMMA);
                writer.serialize((long) (bucket[i].count() - prevBucketCount));
                prevBucketCount = bucket[i].count();
            }
        }
        writer.write(ARRAY_END);

        writer.write(COMMA);
        writer.writeAscii("\"type\":\"histogram\"");

        writer.write(OBJECT_END);
    }

    private static void serializeValue(Meter.Id id, String suffix, long value, DataWriter writer, StringBuilder replaceBuilder, boolean dedotMetricName) {
        serializeValueStart(id.getName(), suffix, writer, replaceBuilder, dedotMetricName);
        writer.serialize(value);
        writer.write(OBJECT_END);
    }

    /**
     * Conditionally serializes a {@code double} value if the value is valid, i.e. neither Double.NaN nor +/-Infinite
     *
     * @param id       meter ID
     * @param value    meter value
     * @param hasValue whether a value has already been written
     * @param writer   writer
     * @param replaceBuilder
     * @param dedotMetricName
     * @return true if a value has been written before, including this one; false otherwise
     */
    private static boolean serializeValue(Meter.Id id, double value, boolean hasValue, DataWriter writer, StringBuilder replaceBuilder, boolean dedotMetricName) {
        if (isValidValue(value)) {
            if (hasValue) writer.write(COMMA);
            serializeValue(id, "", value, writer, replaceBuilder, dedotMetricName);
            return true;
        }
        return hasValue;
    }

    private static void serializeValue(Meter.Id id, String suffix, double value, DataWriter writer, StringBuilder replaceBuilder, boolean dedotMetricName) {
        serializeValueStart(id.getName(), suffix, writer, replaceBuilder, dedotMetricName);
        writer.serialize(value);
        writer.write(OBJECT_END);
    }

    private static void serializeValueStart(String key, String suffix, DataWriter writer, StringBuilder replaceBuilder, boolean dedotMetricName) {
        serializeObjectStart(key, "value",  suffix, writer, replaceBuilder, dedotMetricName);
    }

    private static void serializeObjectStart(String key, String objectName, String suffix, DataWriter writer, StringBuilder replaceBuilder, boolean dedotMetricName) {
        replaceBuilder.setLength(0);
        if (dedotMetricName) {
            writer.sanitizePropertyName(key, replaceBuilder);
        } else {
            replaceBuilder.append(key);
        }
        if (suffix != null) {
            if (replaceBuilder.length() == 0) {
                replaceBuilder.append(key);
            }
            replaceBuilder.append(suffix);
        }
        writer.writeString(replaceBuilder);

        writer.write(SEMI);
        writer.write(OBJECT_START);
        writer.write(QUOTE);
        writer.writeAscii(objectName);
        writer.write(QUOTE);
        writer.write(SEMI);
    }

    private static boolean isValidValue(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
