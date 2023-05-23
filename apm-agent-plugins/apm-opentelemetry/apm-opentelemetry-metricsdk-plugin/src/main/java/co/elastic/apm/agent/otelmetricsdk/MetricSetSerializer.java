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

import co.elastic.apm.agent.report.Reporter;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import com.dslplatform.json.BoolConverter;
import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonWriter;
import com.dslplatform.json.Nullable;
import com.dslplatform.json.NumberConverter;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributeType;
import io.opentelemetry.api.common.Attributes;

import java.util.List;
import java.util.Map;

import static com.dslplatform.json.JsonWriter.ARRAY_END;
import static com.dslplatform.json.JsonWriter.ARRAY_START;
import static com.dslplatform.json.JsonWriter.COMMA;
import static com.dslplatform.json.JsonWriter.OBJECT_END;
import static com.dslplatform.json.JsonWriter.OBJECT_START;

class MetricSetSerializer {

    private static final byte NEW_LINE = '\n';

    private static final int INITIAL_BUFFER_SIZE = 2048;

    private static final DslJson<Object> DSL_JSON = new DslJson<>(new DslJson.Settings<>());

    private final StringBuilder replaceBuilder;
    private final JsonWriter jw;
    private boolean anySamplesWritten;

    public MetricSetSerializer(Attributes attributes, CharSequence instrumentationScopeName, long epochMicros, StringBuilder replaceBuilder) {
        this.replaceBuilder = replaceBuilder;
        anySamplesWritten = false;
        jw = DSL_JSON.newWriter(INITIAL_BUFFER_SIZE);
        jw.writeByte(JsonWriter.OBJECT_START);
        {
            DslJsonSerializer.writeFieldName("metricset", jw);
            jw.writeByte(JsonWriter.OBJECT_START);
            {
                DslJsonSerializer.writeFieldName("timestamp", jw);
                NumberConverter.serialize(epochMicros, jw);
                jw.writeByte(JsonWriter.COMMA);
                serializeAttributes(instrumentationScopeName, attributes);
                DslJsonSerializer.writeFieldName("samples", jw);
                jw.writeByte(JsonWriter.OBJECT_START);
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
            jw.writeByte(COMMA);
        }
        serializeFieldKey(metricName);
        jw.writeByte(JsonWriter.OBJECT_START);
        {
            serializeFieldKeyAscii("value");
            if (isDouble) {
                NumberConverter.serialize(doubleVal, jw);
            } else {
                NumberConverter.serialize(longVal, jw);
            }
        }
        jw.writeByte(JsonWriter.OBJECT_END);
        anySamplesWritten = true;
    }


    public void addExplicitBucketHistogram(CharSequence metricName, List<Double> boundaries, List<Long> counts) {
        if (isEmptyHistogram(boundaries, counts)) {
            return;
        }
        if (anySamplesWritten) {
            jw.writeByte(COMMA);
        }
        serializeFieldKey(metricName);
        jw.writeByte(JsonWriter.OBJECT_START);
        {
            serializeFieldKeyAscii("values");
            convertAndSerializeHistogramBucketBoundaries(boundaries, counts);
            jw.writeByte(COMMA);
            serializeFieldKeyAscii("counts");
            convertAndSerializeHistogramBucketCounts(counts);
            jw.writeByte(COMMA);
            jw.writeAscii("\"type\":\"histogram\"");
        }
        jw.writeByte(JsonWriter.OBJECT_END);
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
        jw.writeByte(ARRAY_START);
        boolean firstElement = true;
        for (long count : counts) {
            if (count != 0) {
                if (!firstElement) {
                    jw.writeByte(COMMA);
                }
                firstElement = false;
                NumberConverter.serialize(count, jw);
            }
        }
        jw.writeByte(ARRAY_END);
    }

    private void convertAndSerializeHistogramBucketBoundaries(List<Double> boundaries, List<Long> counts) {
        jw.writeByte(ARRAY_START);
        boolean firstElement = true;
        //Bucket boundary conversion algorithm is copied from APM server
        int bucketCount = counts.size();
        for (int i = 0; i < bucketCount; i++) {
            if (counts.get(i) != 0) {
                if (!firstElement) {
                    jw.writeByte(COMMA);
                }
                firstElement = false;
                if (i == 0) {
                    double bounds = boundaries.get(0);
                    if (bounds > 0) {
                        bounds /= 2;
                    }
                    NumberConverter.serialize(bounds, jw);
                } else if (i == bucketCount - 1) {
                    NumberConverter.serialize(boundaries.get(bucketCount - 2), jw);
                } else {
                    double lower = boundaries.get(i - 1);
                    double upper = boundaries.get(i);
                    NumberConverter.serialize(lower + (upper - lower) / 2, jw);
                }
            }
        }
        jw.writeByte(ARRAY_END);
    }

    private void serializeFieldKey(CharSequence fieldName) {
        jw.writeString(fieldName);
        jw.writeByte(JsonWriter.SEMI);
    }

    private void serializeFieldKeyAscii(String fieldName) {
        jw.writeByte(JsonWriter.QUOTE);
        jw.writeAscii(fieldName);
        jw.writeByte(JsonWriter.QUOTE);
        jw.writeByte(JsonWriter.SEMI);
    }

    private void serializeAttributes(CharSequence instrumentationScopeName, Attributes attributes) {
        Map<AttributeKey<?>, Object> attributeMap = attributes.asMap();
        if (attributeMap.isEmpty() && instrumentationScopeName.length() == 0) {
            return;
        }
        DslJsonSerializer.writeFieldName("tags", jw);
        jw.writeByte(OBJECT_START);
        boolean anyWritten = false;
        if (instrumentationScopeName.length() > 0) {
            jw.writeAscii("\"otel_instrumentation_scope_name\":");
            jw.writeString(instrumentationScopeName);
            anyWritten = true;
        }
        for (Map.Entry<AttributeKey<?>, Object> entry : attributeMap.entrySet()) {
            AttributeKey<?> key = entry.getKey();
            Object value = entry.getValue();
            anyWritten |= serializeAttribute(key, value, anyWritten);
        }
        jw.writeByte(OBJECT_END);
        jw.writeByte(COMMA);
    }

    private boolean serializeAttribute(AttributeKey<?> key, @Nullable Object value, boolean prependComma) {
        if (isValidAttributeValue(key, value)) {
            if (prependComma) {
                jw.writeByte(COMMA);
            }
            DslJsonSerializer.writeStringValue(DslJsonSerializer.sanitizePropertyName(key.getKey(), replaceBuilder), replaceBuilder, jw);
            jw.writeByte(JsonWriter.SEMI);

            AttributeType type = key.getType();
            switch (type) {
                case STRING:
                    jw.writeString((CharSequence) value);
                    return true;
                case BOOLEAN:
                    BoolConverter.serialize((Boolean) value, jw);
                    return true;
                case LONG:
                    NumberConverter.serialize(((Number) value).longValue(), jw);
                    return true;
                case DOUBLE:
                    NumberConverter.serialize(((Number) value).doubleValue(), jw);
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
                    jw.writeByte(JsonWriter.OBJECT_END);
                }
            }
            jw.writeByte(JsonWriter.OBJECT_END);
        }
        jw.writeByte(JsonWriter.OBJECT_END);
        jw.writeByte(NEW_LINE);
    }

    public void finishAndReport(Reporter reporter) {
        if (anySamplesWritten) {
            serializeMetricSetEnd();
            reporter.reportMetrics(jw);
        }
    }
}
