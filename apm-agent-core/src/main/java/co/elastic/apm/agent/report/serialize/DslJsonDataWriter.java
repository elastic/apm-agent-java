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

import co.elastic.apm.agent.report.Reporter;
import co.elastic.apm.agent.tracer.reporting.DataWriter;
import com.dslplatform.json.BoolConverter;
import com.dslplatform.json.JsonWriter;
import com.dslplatform.json.NumberConverter;

import javax.annotation.Nullable;

public class DslJsonDataWriter implements DataWriter {

    private final JsonWriter jw;

    private final Reporter reporter;

    private final StringBuilder replaceBuilder = new StringBuilder();

    public DslJsonDataWriter(JsonWriter jw, Reporter reporter) {
        this.jw = jw;
        this.reporter = reporter;
    }

    @Override
    public void writeStructure(StructureType type) {
        switch (type) {
            case OBJECT_START:
                jw.writeByte(JsonWriter.OBJECT_START);
                break;
            case OBJECT_END:
                jw.writeByte(JsonWriter.OBJECT_END);
                break;
            case ARRAY_START:
                jw.writeByte(JsonWriter.ARRAY_START);
                break;
            case ARRAY_END:
                jw.writeByte(JsonWriter.ARRAY_END);
                break;
            case NEXT:
                jw.writeByte(JsonWriter.COMMA);
                break;
            case NEW:
                jw.writeByte((byte) '\n');
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    @Override
    public void writeKey(CharSequence name) {
        jw.writeString(name);
        jw.writeByte(JsonWriter.SEMI);
    }

    @Override
    public void writeKey(CharSequence name, boolean sanitized) {
        if (sanitized) {
            DslJsonSerializer.writeStringValue(DslJsonSerializer.sanitizePropertyName(name.toString(), replaceBuilder), replaceBuilder, jw);
            jw.writeByte(JsonWriter.SEMI);
        } else {
            writeKey(name);
        }
    }

    @Override
    public void writeKey(CharSequence name, @Nullable String suffix, boolean sanitized) {
        replaceBuilder.setLength(0);
        if (sanitized) {
            DslJsonSerializer.sanitizePropertyName(name.toString(), replaceBuilder);
        } else {
            replaceBuilder.append(name);
        }
        if (suffix != null) {
            if (replaceBuilder.length() == 0) {
                replaceBuilder.append(name);
            }
            replaceBuilder.append(suffix);
        }
        jw.writeString(replaceBuilder);
        jw.writeByte(JsonWriter.SEMI);
    }

    @Override
    public void writeValue(boolean value) {
        BoolConverter.serialize(value, jw);
    }

    @Override
    public void writeValue(long value) {
        NumberConverter.serialize(value, jw);
    }

    @Override
    public void writeValue(double value) {
        NumberConverter.serialize(value, jw);
    }

    @Override
    public void writeValue(CharSequence value) {
        jw.writeString(value);
    }

    @Override
    public void writeValue(CharSequence value, boolean trimmed) {
        if (trimmed) {
            DslJsonSerializer.writeStringValue(value, replaceBuilder, jw);
        } else {
            writeValue(value);
        }
    }

    @Override
    public int size() {
        return jw.size();
    }

    @Override
    public void report() {
        reporter.reportMetrics(jw);
    }

    @Override
    public String toString() {
        return jw.toString();
    }
}
