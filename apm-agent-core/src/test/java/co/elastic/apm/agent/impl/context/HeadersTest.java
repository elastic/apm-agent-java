/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent.impl.context;

import co.elastic.apm.agent.util.BinaryHeaderMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;

class HeadersTest {
    private Headers headers = new Headers();

    @BeforeEach
    void fill() throws BinaryHeaderMap.InsufficientCapacityException {
        headers.add("bin0", "bin-val0".getBytes());
        headers.add("text0", "text-val0");
        headers.add("text1", "text-val1");
        headers.add("bin1", "bin-val1".getBytes());
    }

    @AfterEach
    void reset() {
        headers.resetState();
        assertThat(headers.isEmpty()).isTrue();
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testOneEntry() {
        assertThat(headers.size()).isEqualTo(4);
        Iterator<Headers.Header> iterator = headers.iterator();
        Headers.Header header = iterator.next();
        assertThat(header.getKey()).isEqualTo("text0");
        assertThat(header.getValue().toString()).isEqualTo("text-val0");
        header = iterator.next();
        assertThat(header.getKey()).isEqualTo("text1");
        assertThat(header.getValue().toString()).isEqualTo("text-val1");
        header = iterator.next();
        assertThat(header.getKey()).isEqualTo("bin0");
        assertThat(header.getValue().toString()).isEqualTo("bin-val0");
        header = iterator.next();
        assertThat(header.getKey()).isEqualTo("bin1");
        assertThat(header.getValue().toString()).isEqualTo("bin-val1");
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testCopyFrom() throws BinaryHeaderMap.InsufficientCapacityException {
        Headers copy = new Headers();
        copy.add("bin2", "bin-val2".getBytes());
        copy.add("text2", "text-val2");
        copy.copyFrom(headers);

        assertThat(copy.size()).isEqualTo(4);
        Iterator<Headers.Header> iterator = copy.iterator();
        Headers.Header header = iterator.next();
        assertThat(header.getKey()).isEqualTo("text0");
        assertThat(header.getValue().toString()).isEqualTo("text-val0");
        header = iterator.next();
        assertThat(header.getKey()).isEqualTo("text1");
        assertThat(header.getValue().toString()).isEqualTo("text-val1");
        header = iterator.next();
        assertThat(header.getKey()).isEqualTo("bin0");
        assertThat(header.getValue().toString()).isEqualTo("bin-val0");
        header = iterator.next();
        assertThat(header.getKey()).isEqualTo("bin1");
        assertThat(header.getValue().toString()).isEqualTo("bin-val1");
    }

    @SuppressWarnings("unused")
    @Test
    void testMultipleIterations() {
        int numItems = 0;
        for (Headers.Header header : headers) {
            numItems++;
            if (numItems == 2) {
                break;
            }
        }
        for (Headers.Header header : headers) {
            numItems++;
        }
        assertThat(numItems).isEqualTo(6);
    }
}
