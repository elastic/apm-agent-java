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
package co.elastic.apm.agent.jmx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scans input like {@code map1_key1[value] map1_key2[value], map2_key1[value] map2_key2[value]} and converts it to a {@code List<Map<String, String>>}
 *
 * <p>
 * For example, the following assertion is valid:
 * </p>
 * {@code assert new MapsTokenScanner("foo[bar] baz[qux], quux[corge]").equals(List.of(Map.of("foo", "bar", "baz", "qux"), Map.of("quux", "corge"))}
 */
public class MapsTokenScanner {
    private final String input;
    private int pos; // read position char offset

    public MapsTokenScanner(String input) {
        this.input = input;
    }

    public static String toTokenString(List<Map<String, String>> maps) {
        if (maps.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map<String, String> map : maps) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                sb.append(entry.getKey()).append('[').append(entry.getValue()).append(']').append(" ");
            }
            if (!map.isEmpty()) {
                // remove last ' '
                sb.setLength(sb.length() - 1);
            }
            sb.append(", ");
        }
        // remove last ', '
        sb.setLength(sb.length() - 2);

        return sb.toString();
    }

    public List<Map<String, String>> scanMaps() {
        skipWhiteSpace();
        List<Map<String, String>> maps = new ArrayList<>();
        while (hasNext()) {
            maps.add(scanMap());
        }
        return maps;
    }

    Map<String, String> scanMap() {
        HashMap<String, String> map = new HashMap<>();
        skipWhiteSpace();
        while (hasNext()) {
            if (peek() == ',') {
                next();
                break;
            }
            map.put(scanKey(), scanValue());
            skipWhiteSpace();
        }
        return map;
    }

    String scanKey() {
        if (next() == '[') {
            throw new IllegalArgumentException("Empty key at pos " + pos + " in '" + input + "'");
        }
        int start = pos - 1;
        while (peek() != '[') {
            next();
        }
        return input.substring(start, pos);
    }

    String scanValue() {
        if (next() != '[') {
            throw new IllegalArgumentException("Expected key start '[' at pos " + pos + " in '" + input + "'");
        }
        int start = pos;
        while (peek() != ']') {
            next();
        }
        String value = input.substring(start, pos);
        next();
        return value;
    }

    void skipWhiteSpace() {
        while (hasNext() && Character.isWhitespace(peek())) {
            next();
        }
    }

    char next() {
        return input.charAt(pos++);
    }

    char peek() {
        return input.charAt(pos);
    }

    boolean hasNext() {
        return pos < input.length();
    }
}
