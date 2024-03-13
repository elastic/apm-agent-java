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
package co.elastic.apm.agent.tracer.metrics;

import com.dslplatform.json.JsonWriter;

public class DslJsonUtil {

    public static final int MAX_VALUE_LENGTH = 1024;

    private static final String[] DISALLOWED_IN_PROPERTY_NAME = new String[]{".", "*", "\""};

    public static void writeFieldName(final String fieldName, final JsonWriter jw) {
        jw.writeByte(JsonWriter.QUOTE);
        jw.writeAscii(fieldName);
        jw.writeByte(JsonWriter.QUOTE);
        jw.writeByte(JsonWriter.SEMI);
    }


    public static CharSequence sanitizePropertyName(String key, StringBuilder replaceBuilder) {
        for (int i = 0; i < DISALLOWED_IN_PROPERTY_NAME.length; i++) {
            if (key.contains(DISALLOWED_IN_PROPERTY_NAME[i])) {
                return replaceAll(key, DISALLOWED_IN_PROPERTY_NAME, "_", replaceBuilder);
            }
        }
        return key;
    }

    private static CharSequence replaceAll(String s, String[] stringsToReplace, String replacement, StringBuilder replaceBuilder) {
        // uses a instance variable StringBuilder to avoid allocations
        replaceBuilder.setLength(0);
        replaceBuilder.append(s);
        for (String toReplace : stringsToReplace) {
            replace(replaceBuilder, toReplace, replacement, 0);
        }
        return replaceBuilder;
    }

    static void replace(StringBuilder replaceBuilder, String toReplace, String replacement, int fromIndex) {
        for (int i = replaceBuilder.indexOf(toReplace, fromIndex); i != -1; i = replaceBuilder.indexOf(toReplace, fromIndex)) {
            replaceBuilder.replace(i, i + toReplace.length(), replacement);
            fromIndex = i;
        }
    }

    public static void writeStringValue(CharSequence value, final StringBuilder replaceBuilder, final JsonWriter jw) {
        if (value.length() > MAX_VALUE_LENGTH) {
            replaceBuilder.setLength(0);
            replaceBuilder.append(value, 0, Math.min(value.length(), MAX_VALUE_LENGTH + 1));
            writeStringBuilderValue(replaceBuilder, jw);
        } else {
            jw.writeString(value);
        }
    }

    private static void writeStringBuilderValue(StringBuilder value, JsonWriter jw) {
        if (value.length() > MAX_VALUE_LENGTH) {
            value.setLength(MAX_VALUE_LENGTH - 1);
            value.append('…');
        }
        jw.writeString(value);
    }
}
