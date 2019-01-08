/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018-2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.report.serialize;

import co.elastic.apm.agent.metrics.DoubleSupplier;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.metrics.MetricSet;
import com.dslplatform.json.JsonWriter;
import com.dslplatform.json.NumberConverter;

import java.util.Iterator;
import java.util.Map;

public class MetricRegistrySerializer {

    private static final byte NEW_LINE = '\n';

    public static void serialize(MetricRegistry metricRegistry, StringBuilder replaceBuilder, JsonWriter jw) {
        final long timestamp = System.currentTimeMillis() * 1000;
        for (MetricSet metricSet : metricRegistry.getMetricSets().values()) {
            serializeMetricSet(metricSet, timestamp, replaceBuilder, jw);
            jw.writeByte(NEW_LINE);
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

                if (!metricSet.getTags().isEmpty()) {
                    DslJsonSerializer.writeFieldName("tags", jw);
                    DslJsonSerializer.serializeTags(metricSet.getTags(), replaceBuilder, jw);
                    jw.writeByte(JsonWriter.COMMA);
                }

                DslJsonSerializer.writeFieldName("samples", jw);
                serializeSamples(metricSet.getSamples(), jw);
            }
            jw.writeByte(JsonWriter.OBJECT_END);
        }
        jw.writeByte(JsonWriter.OBJECT_END);
    }

    private static void serializeSamples(Map<String, DoubleSupplier> samples, JsonWriter jw) {
        jw.writeByte(JsonWriter.OBJECT_START);
        final int size = samples.size();
        if (size > 0) {
            final Iterator<Map.Entry<String, DoubleSupplier>> iterator = samples.entrySet().iterator();
            Map.Entry<String, DoubleSupplier> kv = iterator.next();
            serializeSample(kv.getKey(), kv.getValue().get(), jw);
            for (int i = 1; i < size; i++) {
                jw.writeByte(JsonWriter.COMMA);
                kv = iterator.next();
                serializeSample(kv.getKey(), kv.getValue().get(), jw);
            }
        }
        jw.writeByte(JsonWriter.OBJECT_END);
    }

    private static void serializeSample(String key, double value, JsonWriter jw) {
        jw.writeString(key);
        jw.writeByte(JsonWriter.SEMI);
        jw.writeByte(JsonWriter.OBJECT_START);
        {
            DslJsonSerializer.writeFieldName("value", jw);
            NumberConverter.serialize(value, jw);
        }
        jw.writeByte(JsonWriter.OBJECT_END);
    }
}
