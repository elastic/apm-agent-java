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

    public static String toTokenString(List<Map<String, List<String>>> maps) {
        if (maps.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map<String, List<String>> map : maps) {
            for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                for (String value : entry.getValue()) {
                    sb.append(entry.getKey()).append('[').append(value).append(']').append(" ");
                }
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

    public List<Map<String, List<String>>> scanMultiValueMaps() {
        skipWhiteSpace();
        List<Map<String, List<String>>> maps = new ArrayList<>();
        while (hasNext()) {
            maps.add(scanMultiValueMap());
        }
        return maps;
    }

    Map<String, List<String>> scanMultiValueMap() {
        Map<String, List<String>> map = new HashMap<>();
        skipWhiteSpace();
        while (hasNext()) {
            if (peek() == ',') {
                next();
                break;
            }
            String key = scanKey();
            if (!map.containsKey(key)) {
                map.put(key, new ArrayList<String>());
            }
            map.get(key).add(scanValue());
            skipWhiteSpace();
        }
        return map;
    }

    Map<String, String> scanMap() {
        Map<String, String> map = new HashMap<>();
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
        while (!isNext('[')) {
            if (!hasNext()) {
                throw new IllegalArgumentException("Expected value start token '[' at pos " + pos + " in '" + input + "'");
            }
            next();
        }
        return input.substring(start, pos);
    }

    String scanValue() {
        if (!isNext('[')) {
            throw new IllegalArgumentException("Expected value start token '[' at pos " + pos + " in '" + input + "'");
        } else {
            next();
        }
        int start = pos;
        while (!isNext(']')) {
            if (isNext('[')) {
                throw new IllegalArgumentException("Invalid char '[' within a value at pos " + pos + " in '" + input + "'");
            }
            if (!hasNext()) {
                throw new IllegalArgumentException("Expected end value token ']' at pos " + pos + " in '" + input + "'");
            }
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

    boolean isNext(char c) {
        return hasNext() && peek() == c;
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
