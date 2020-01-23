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

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HexUtilsTest {

    @Test
    void hexConversionRoundTrip() throws IOException {
        byte[] bytes = new byte[8];
        HexUtils.nextBytes("09c2572177fdae24", 0, bytes);
        assertThat(HexUtils.bytesToHex(bytes)).isEqualTo("09c2572177fdae24");
    }

    @Test
    void testInvalidHex() {
        assertThatThrownBy(() -> HexUtils.getNextByte("0$", 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Not a hex encoded string: 0$ at offset 0");
    }

    @Test
    void testStringTooSmall() {
        assertThatThrownBy(() -> HexUtils.nextBytes("00", 0, new byte[2]))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Can't read 2 bytes from string 00 with offset 0");
    }

    @Test
    void testUnevenLength() {
        final byte[] bytes = new byte[1];
        // reads the first two chars and converts "0a" to (byte) 10
        HexUtils.nextBytes("0a0", 0, bytes);
        assertThat(bytes).isEqualTo(new byte[]{10});
    }
}
