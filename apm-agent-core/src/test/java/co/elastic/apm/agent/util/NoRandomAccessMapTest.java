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
package co.elastic.apm.agent.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoRandomAccessMapTest {
    private NoRandomAccessMap<String, String> map = new NoRandomAccessMap<>();

    @AfterEach
    void reset() {
        map.resetState();
        assertThat(map.isEmpty()).isTrue();
    }

    @Test
    void testOneEntry() {
        map.add("foo", "bar");
        int numElements = 0;
        for (NoRandomAccessMap.Entry entry : map) {
            numElements++;
            assertThat(entry.getKey()).isEqualTo("foo");
            assertThat(entry.getValue()).isEqualTo("bar");
        }
        assertThat(numElements).isEqualTo(1);
    }

    @Test
    void testNullValue() {
        map.add("foo", null);
        int numElements = 0;
        for (NoRandomAccessMap.Entry entry : map) {
            numElements++;
            assertThat(entry.getKey()).isEqualTo("foo");
            assertThat(entry.getValue()).isNull();
        }
        assertThat(numElements).isEqualTo(1);
    }

    @Test
    void testTwoEntries() {
        map.add(String.valueOf(0), "value");
        map.add(String.valueOf(1), "value");
        int index = 0;
        int numElements = 0;
        for (NoRandomAccessMap.Entry entry : map) {
            numElements++;
            assertThat(entry.getKey()).isEqualTo(String.valueOf(index++));
            assertThat(entry.getValue()).isEqualTo("value");
        }
        assertThat(numElements).isEqualTo(2);
    }

    @Test
    void testCopyFrom() {
        map.add(String.valueOf(0), "value");
        map.add(String.valueOf(1), "value");
        NoRandomAccessMap<String, String> copy = new NoRandomAccessMap<>();
        copy.add("foo", "bar");
        copy.copyFrom(map);
        int index = 0;
        int numElements = 0;
        for (NoRandomAccessMap.Entry entry : copy) {
            numElements++;
            assertThat(entry.getKey()).isEqualTo(String.valueOf(index++));
            assertThat(entry.getValue()).isEqualTo("value");
        }
        assertThat(numElements).isEqualTo(2);
    }

    @Test
    void testTwoIterations() {
        map.add(String.valueOf(0), "value");
        map.add(String.valueOf(1), "value");
        int numElements = 0;
        for (NoRandomAccessMap.Entry entry : map) {
            numElements++;
            break;
        }
        for (NoRandomAccessMap.Entry entry : map) {
            numElements++;
        }
        assertThat(numElements).isEqualTo(3);
    }
}
