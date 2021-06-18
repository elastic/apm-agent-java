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
package co.elastic.apm.agent.impl.transaction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdTest {

    @Test
    void testReInit() {
        final Id id = Id.new64BitId();

        id.fromHexString("0000000000000001", 0);
        assertThat(id.toString()).isEqualTo("0000000000000001");
        assertThat(id.isEmpty()).isFalse();

        id.fromHexString("0000000000000002", 0);
        assertThat(id.toString()).isEqualTo("0000000000000002");
        assertThat(id.isEmpty()).isFalse();
    }

    @Test
    void testReset() {
        final Id id = Id.new64BitId();

        id.fromHexString("0000000000000001", 0);
        assertThat(id.toString()).isEqualTo("0000000000000001");
        assertThat(id.isEmpty()).isFalse();

        id.resetState();
        assertThat(id.toString()).isEqualTo("0000000000000000");
        assertThat(id.isEmpty()).isTrue();
    }

    @Test
    void testInitEmpty() {
        final Id id = Id.new64BitId();
        assertThat(id.toString()).isEqualTo("0000000000000000");
        assertThat(id.isEmpty()).isTrue();

        id.fromHexString("0000000000000000", 0);
        assertThat(id.toString()).isEqualTo("0000000000000000");
        assertThat(id.isEmpty()).isTrue();

        id.fromLongs(0);
        assertThat(id.toString()).isEqualTo("0000000000000000");
        assertThat(id.isEmpty()).isTrue();
    }

    @Test
    void testFromAndToLong() {
        final Id id = Id.new128BitId();

        id.fromLongs(21, 42);
        assertThat(id.isEmpty()).isFalse();
        assertThat(id.readLong(0)).isEqualTo(21);
        assertThat(id.readLong(8)).isEqualTo(42);
    }
}
