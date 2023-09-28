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
package co.elastic.apm.agent.otelmetricsdk;

import co.elastic.apm.agent.tracer.reporting.DataWriter;
import co.elastic.apm.agent.tracer.reporting.ReportingTracer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributeType;
import io.opentelemetry.api.common.Attributes;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

import static co.elastic.apm.agent.tracer.reporting.DataWriter.StructureType.ARRAY_END;
import static co.elastic.apm.agent.tracer.reporting.DataWriter.StructureType.ARRAY_START;
import static co.elastic.apm.agent.tracer.reporting.DataWriter.StructureType.NEXT;
import static co.elastic.apm.agent.tracer.reporting.DataWriter.StructureType.NEW;
import static co.elastic.apm.agent.tracer.reporting.DataWriter.StructureType.OBJECT_END;
import static co.elastic.apm.agent.tracer.reporting.DataWriter.StructureType.OBJECT_START;

class MetricSetSerializer {

    private static final int INITIAL_BUFFER_SIZE = 2048;

    private final DataWriter writer;
    private boolean anySamplesWritten;

    public MetricSetSerializer(ReportingTracer tracer, Attributes attributes, CharSequence instrumentationScopeName, long epochMicros) {
        anySamplesWritten = false;
        writer = tracer.newWriter(INITIAL_BUFFER_SIZE);
        writer.writeStructure(OBJECT_START);
        {
            writer.writeKey("metricset");
            writer.writeStructure(OBJECT_START);
            {
                writer.writeKey("timestamp");
                writer.writeValue(epochMicros);
                writer.writeStructure(NEXT);
                serializeAttributes(instrumentationScopeName, attributes);
                writer.writeKey("samples");
                writer.writeStructure(OBJECT_START);
            }
        }
    }

    public void addValue(CharSequence metricName, double value) {
        addValue(metricName, true, 0, value);
    }

    public void addValue(CharSequence metricName, long value) {
        addValue(metricName, false, value, 0.0);
    }

    private void addValue(CharSequence metricName, boolean isDouble, long longVal, double doubleVal) {
        if (anySamplesWritten) {
            writer.writeStructure(NEXT);
        }
        writer.writeKey(metricName);
        writer.writeStructure(OBJECT_START);
        {
            writer.writeKey("value");
            if (isDouble) {
                writer.writeValue(doubleVal);
            } else {
                writer.writeValue(longVal);
            }
        }
        writer.writeStructure(OBJECT_END);
        anySamplesWritten = true;
    }


    public void addExplicitBucketHistogram(CharSequence metricName, List<Double> boundaries, List<Long> counts) {
        if (isEmptyHistogram(boundaries, counts)) {
            return;
        }
        if (anySamplesWritten) {
            writer.writeStructure(NEXT);
        }
        writer.writeKey(metricName);
        writer.writeStructure(OBJECT_START);
        {
            writer.writeKey("values");
            convertAndSerializeHistogramBucketBoundaries(boundaries, counts);
            writer.writeStructure(NEXT);
            writer.writeKey("counts");
            convertAndSerializeHistogramBucketCounts(counts);
            writer.writeStructure(NEXT);
            writer.writeKey("type");
            writer.writeValue("histogram");
        }
        writer.writeStructure(OBJECT_END);
        anySamplesWritten = true;
    }

    private boolean isEmptyHistogram(List<Double> boundaries, List<Long> counts) {
        for (long count : counts) {
            if (count != 0) {
                return false;
            }
        }
        return true;
    }

    private void convertAndSerializeHistogramBucketCounts(List<Long> counts) {
        writer.writeStructure(ARRAY_START);
        boolean firstElement = true;
        for (long count : counts) {
            if (count != 0) {
                if (!firstElement) {
                    writer.writeStructure(NEXT);
                }
                firstElement = false;
                writer.writeValue(count);
            }
        }
        writer.writeStructure(ARRAY_END);
    }

    private void convertAndSerializeHistogramBucketBoundaries(List<Double> boundaries, List<Long> counts) {
        writer.writeStructure(ARRAY_START);
        boolean firstElement = true;
        //Bucket boundary conversion algorithm is copied from APM server
        int bucketCount = counts.size();
        for (int i = 0; i < bucketCount; i++) {
            if (counts.get(i) != 0) {
                if (!firstElement) {
                    writer.writeStructure(NEXT);
                }
                firstElement = false;
                if (i == 0) {
                    double bounds = boundaries.get(0);
                    if (bounds > 0) {
                        bounds /= 2;
                    }
                    writer.writeValue(bounds);
                } else if (i == bucketCount - 1) {
                    writer.writeValue(boundaries.get(bucketCount - 2));
                } else {
                    double lower = boundaries.get(i - 1);
                    double upper = boundaries.get(i);
                    writer.writeValue(lower + (upper - lower) / 2);
                }
            }
        }
        writer.writeStructure(ARRAY_END);
    }

    private void serializeAttributes(CharSequence instrumentationScopeName, Attributes attributes) {
        Map<AttributeKey<?>, Object> attributeMap = attributes.asMap();
        if (attributeMap.isEmpty() && instrumentationScopeName.length() == 0) {
            return;
        }
        writer.writeKey("tags");
        writer.writeStructure(OBJECT_START);
        boolean anyWritten = false;
        if (instrumentationScopeName.length() > 0) {
            writer.writeKey("otel_instrumentation_scope_name");
            writer.writeValue(instrumentationScopeName);
            anyWritten = true;
        }
        for (Map.Entry<AttributeKey<?>, Object> entry : attributeMap.entrySet()) {
            AttributeKey<?> key = entry.getKey();
            Object value = entry.getValue();
            anyWritten |= serializeAttribute(key, value, anyWritten);
        }
        writer.writeStructure(OBJECT_END);
        writer.writeStructure(NEXT);
    }

    private boolean serializeAttribute(AttributeKey<?> key, @Nullable Object value, boolean prependComma) {
        if (isValidAttributeValue(key, value)) {
            if (prependComma) {
                writer.writeStructure(NEXT);
            }
            writer.writeKey(key.getKey(), true);

            AttributeType type = key.getType();
            switch (type) {
                case STRING:
                    writer.writeValue((CharSequence) value);
                    return true;
                case BOOLEAN:
                    writer.writeValue((Boolean) value);
                    return true;
                case LONG:
                    writer.writeValue(((Number) value).longValue());
                    return true;
                case DOUBLE:
                    writer.writeValue(((Number) value).doubleValue());
                    return true;
                case STRING_ARRAY:
                case BOOLEAN_ARRAY:
                case LONG_ARRAY:
                case DOUBLE_ARRAY:
                    return false; //Array types are not supported yet
                default:
                    throw new IllegalStateException("Unhandled enum value: " + type);
            }
        }
        return false;
    }

    private boolean isValidAttributeValue(AttributeKey<?> key, @Nullable Object value) {
        if (value == null) {
            return false;
        }
        switch (key.getType()) {
            case STRING:
                return value instanceof CharSequence;
            case BOOLEAN:
                return value instanceof Boolean;
            case LONG:
            case DOUBLE:
                return value instanceof Number;
            case STRING_ARRAY:
            case BOOLEAN_ARRAY:
            case LONG_ARRAY:
            case DOUBLE_ARRAY:
                return false; //Array types are not supported at the moment
        }
        return false;
    }

    private void serializeMetricSetEnd() {
        {
            /*"metricset":*/
            {
                /*"samples":*/
                {
                    writer.writeStructure(OBJECT_END);
                }
            }
            writer.writeStructure(OBJECT_END);
        }
        writer.writeStructure(OBJECT_END);
        writer.writeStructure(NEW);
    }

    public void finishAndReport() {
        if (anySamplesWritten) {
            serializeMetricSetEnd();
            writer.report();
        }
    }
}
