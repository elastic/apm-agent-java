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
package co.elastic.apm.agent.configuration.converter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class ByteValueTest {

    @Test
    void testParseUnitSuccess() {
        assertSoftly(softly -> {
            softly.assertThat(ByteValue.of("1b").getBytes()).isEqualTo(1L);
            softly.assertThat(ByteValue.of("1B").getBytes()).isEqualTo(1L);
            softly.assertThat(ByteValue.of("2kb").getBytes()).isEqualTo(2048L);
            softly.assertThat(ByteValue.of("2kB").getBytes()).isEqualTo(2048L);
            softly.assertThat(ByteValue.of("3mb").getBytes()).isEqualTo(3L * 1024 * 1024);
            softly.assertThat(ByteValue.of("3MB").getBytes()).isEqualTo(3L * 1024 * 1024);
            softly.assertThat(ByteValue.of("3gb").getBytes()).isEqualTo(3L * 1024 * 1024 * 1024);
            softly.assertThat(ByteValue.of("3GB").getBytes()).isEqualTo(3L * 1024 * 1024 * 1024);
        });
    }

    @Test
    void testParseUnitInvalid() {
        for (String invalid : List.of("1Kib", "-1b", " 1b", "1 b", "1b ", "1tb")) {
            assertThatCode(() -> ByteValue.of(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
    }

}
