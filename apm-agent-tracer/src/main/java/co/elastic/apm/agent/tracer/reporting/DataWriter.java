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
package co.elastic.apm.agent.tracer.reporting;

public interface DataWriter {

    void writeFieldName(String name);

    void write(StructureType type);

    void serialize(boolean value);

    void serialize(long value);

    void serialize(double value);

    void writeAscii(String value);

    void writeString(CharSequence replaceBuilder);

    void writeStringValue(CharSequence value, StringBuilder replaceBuilder);

    int size();

    CharSequence sanitizePropertyName(String key, StringBuilder replaceBuilder);

    void report();

    enum StructureType {
        OBJECT_START,
        OBJECT_END,
        ARRAY_START,
        ARRAY_END,
        COMMA,
        SEMI,
        QUOTE,
        NEW_LINE
    }
}
