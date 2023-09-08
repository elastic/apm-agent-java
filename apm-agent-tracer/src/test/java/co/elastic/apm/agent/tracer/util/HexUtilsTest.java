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
package co.elastic.apm.agent.tracer.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HexUtilsTest {

    @Test
    void hexConversionRoundTrip() throws IOException {
        byte[] bytes = new byte[8];
        String hexEncodedString = "09c2572177fdae24";
        HexUtils.nextBytes(hexEncodedString, 0, bytes);
        assertThat(HexUtils.bytesToHex(bytes)).isEqualTo(hexEncodedString);

        bytes = new byte[8];
        HexUtils.nextBytesAscii(hexEncodedString.getBytes(StandardCharsets.US_ASCII), 0, bytes);
        byte[] outputAscii = new byte[16];
        HexUtils.writeBytesAsHexAscii(bytes, 0, 8, outputAscii, 0);
        assertThat(new String(outputAscii, StandardCharsets.US_ASCII)).isEqualTo(hexEncodedString);
    }

    @Test
    void testInvalidHex() {
        assertThatThrownBy(() -> HexUtils.getNextByte("0$", 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Not a hex encoded string: 0$ at offset 0");

        assertThatThrownBy(() -> HexUtils.getNextByteAscii("0$".getBytes(StandardCharsets.US_ASCII), 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Not a hex encoded string");
    }

    @Test
    void testStringTooSmall() {
        assertThatThrownBy(() -> HexUtils.nextBytes("00", 0, new byte[2]))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Can't read 2 bytes from string 00 with offset 0");

        assertThatThrownBy(() -> HexUtils.nextBytesAscii("00".getBytes(StandardCharsets.US_ASCII), 0, new byte[2]))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testUnevenLength() {
        byte[] bytes = new byte[1];
        // reads the first two chars and converts "0a" to (byte) 10
        HexUtils.nextBytes("0a0", 0, bytes);
        assertThat(bytes).isEqualTo(new byte[]{10});

        bytes = new byte[1];
        HexUtils.nextBytesAscii("0a0".getBytes(StandardCharsets.US_ASCII), 0, bytes);
        assertThat(bytes).isEqualTo(new byte[]{10});
    }

}
