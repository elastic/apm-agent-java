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

import co.elastic.apm.agent.report.serialize.SerializationConstants;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;

class BinaryHeaderMapTest {
    private BinaryHeaderMap headerMap = new BinaryHeaderMap();

    @AfterEach
    void reset() {
        headerMap.resetState();
        assertThat(headerMap.isEmpty()).isTrue();
    }

    @Test
    void testOneEntry() {
        headerMap.add("foo", "bar".getBytes());
        assertThat(headerMap).hasSize(1);
        headerMap.forEach(e -> {
            assertThat(e.getKey()).isEqualTo("foo");
            assertThat(e.getValue().toString()).isEqualTo("bar");
        });
    }

    @Test
    void testNullValue() {
        headerMap.add("key", null);
        assertThat(headerMap.size()).isEqualTo(1);
        headerMap.iterator().forEachRemaining(e -> {
            assertThat(e.getKey()).isEqualTo("key");
            assertThat(e.getValue()).isNull();
        });
    }

    @Test
    void testCapacityIncrease() {
        // we don't have access to actual capacity
        // thus we just add more than the initial capacity and ensure it's consistent
        int targetSize = BinaryHeaderMap.INITIAL_CAPACITY * 10;
        for (int i = 0; i < targetSize; i++) {
            byte[] value = (i % 5 == 0) ? null : RandomStringUtils.randomAlphanumeric(32).getBytes();
            headerMap.add(String.format("key_%d", i), value);
            assertThat(headerMap.size()).isEqualTo(i + 1);
        }

        int index = 0;
        for (BinaryHeaderMap.Entry entry : headerMap) {
            assertThat(entry.getKey()).isEqualTo("key_%d", index);
            if (index % 5 == 0) {
                assertThat(entry.getValue()).isNull();
            } else {
                assertThat(entry.getValue()).isNotEmpty();
            }
            index++;
        }
        assertThat(index).isEqualTo(targetSize);

    }

    @Test
    void testLongHeader() {
        final String longRandomString = RandomStringUtils.randomAlphanumeric(SerializationConstants.MAX_VALUE_LENGTH + 1);
        headerMap.add("long", longRandomString.getBytes());
        headerMap.add("foo", "bar".getBytes());
        assertThat(headerMap.size()).isEqualTo(2);
        Iterator<BinaryHeaderMap.Entry> iterator = headerMap.iterator();
        BinaryHeaderMap.Entry longHeader = iterator.next();
        assertThat(longHeader.getKey()).isEqualTo("long");
        assertThat(longHeader.getValue().toString().length()).isEqualTo(SerializationConstants.MAX_VALUE_LENGTH);
        BinaryHeaderMap.Entry shortHeader = iterator.next();
        assertThat(shortHeader.getKey()).isEqualTo("foo");
        assertThat(shortHeader.getValue().toString()).isEqualTo("bar");
    }

    @Test
    void testMapOverflow() {
        final String longRandomString = RandomStringUtils.randomAlphanumeric(SerializationConstants.MAX_VALUE_LENGTH + 3);
        for (int i = 0; i < 9; i++) {
            headerMap.add("long" + i, longRandomString.getBytes());
            headerMap.add("foo", "bar".getBytes());
        }
        assertThat(headerMap.size()).isEqualTo(18);
        assertThat(headerMap.add("long10", longRandomString.getBytes())).isFalse();
        assertThat(headerMap.size()).isEqualTo(18);
        Iterator<BinaryHeaderMap.Entry> iterator = headerMap.iterator();
        for (int i = 0; i < 9; i++) {
            BinaryHeaderMap.Entry longHeader = iterator.next();
            assertThat(longHeader.getKey()).isEqualTo("long" + i);
            assertThat(longHeader.getValue().toString().length()).isEqualTo(SerializationConstants.MAX_VALUE_LENGTH);
            BinaryHeaderMap.Entry shortHeader = iterator.next();
            assertThat(shortHeader.getKey()).isEqualTo("foo");
            assertThat(shortHeader.getValue().toString()).isEqualTo("bar");
        }
    }

    @Test
    void testNonUtf8EncodedValue() {
        assertThat(headerMap.add("foo", "bar".getBytes())).isTrue();
        assertThat(headerMap.add("ignore", Base64.getDecoder().decode("ignore"))).isFalse();
        assertThat(headerMap.add("baz", "qux".getBytes())).isTrue();
        assertThat(headerMap.size()).isEqualTo(2);
        Iterator<BinaryHeaderMap.Entry> iterator = headerMap.iterator();
        BinaryHeaderMap.Entry header = iterator.next();
        assertThat(header.getKey()).isEqualTo("foo");
        assertThat(header.getValue().toString()).isEqualTo("bar");
        header = iterator.next();
        assertThat(header.getKey()).isEqualTo("baz");
        assertThat(header.getValue().toString()).isEqualTo("qux");
    }

    @Test
    void testTwoEntries() {
        headerMap.add(String.valueOf(0), "value0".getBytes());
        headerMap.add(String.valueOf(1), "value1".getBytes());
        int index = 0;
        int numElements = 0;
        for (BinaryHeaderMap.Entry entry : headerMap) {
            numElements++;
            String indexS = String.valueOf(index++);
            assertThat(entry.getKey()).isEqualTo(indexS);
            assertThat(entry.getValue().toString()).isEqualTo("value" + indexS);
        }
        assertThat(numElements).isEqualTo(2);
    }

    @Test
    void testCopyFrom() {
        headerMap.add(String.valueOf(0), "value0".getBytes());
        headerMap.add(String.valueOf(1), "value1".getBytes());
        BinaryHeaderMap copy = new BinaryHeaderMap();
        copy.add("foo", "bar".getBytes());
        copy.copyFrom(headerMap);
        int index = 0;
        int numElements = 0;
        for (BinaryHeaderMap.Entry entry : copy) {
            numElements++;
            String indexS = String.valueOf(index++);
            assertThat(entry.getKey()).isEqualTo(indexS);
            assertThat(entry.getValue().toString()).isEqualTo("value" + indexS);
        }
        assertThat(numElements).isEqualTo(2);
    }

    @Test
    void testTwoIterations() {
        headerMap.add(String.valueOf(0), "value".getBytes());
        headerMap.add(String.valueOf(1), "value".getBytes());
        int numElements = 0;
        for (BinaryHeaderMap.Entry entry : headerMap) {
            numElements++;
            break;
        }
        for (BinaryHeaderMap.Entry entry : headerMap) {
            numElements++;
        }
        assertThat(numElements).isEqualTo(3);
    }
}
