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
package co.elastic.apm.agent.util;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;


public class ByteUtilsTest {

    @Test
    public void putLong() {
        byte[] array = new byte[8];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        ByteUtils.putLong(array, 0, 42);
        assertThat(buffer.getLong()).isEqualTo(42);
    }

    @Test
    public void getLong() {
        byte[] array = new byte[8];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.putLong(42);
        assertThat(ByteUtils.getLong(array, 0)).isEqualTo(42);
    }
}
