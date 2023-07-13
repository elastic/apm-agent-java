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
/**
 * This class has been copied from Otel release 1.27.0.
 * We can't directly use it as it is compiled for Java 8 while we require java 7.
 */
/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package co.elastic.apm.agent.impl.baggage.otel;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Note: This class is based on code from Apache Commons Codec. It is comprised of code from these
 * classes:
 *
 * <ul>
 *   <li><a
 *       href="https://github.com/apache/commons-codec/blob/482df6cabfb288acb6ab3e4a732fdb93aecfa7c2/src/main/java/org/apache/commons/codec/net/URLCodec.java">org.apache.commons.codec.net.URLCodec</a>
 *   <li><a
 *       href="https://github.com/apache/commons-codec/blob/482df6cabfb288acb6ab3e4a732fdb93aecfa7c2/src/main/java/org/apache/commons/codec/net/Utils.java">org.apache.commons.codec.net.Utils</a>
 * </ul>
 *
 * <p>Implements baggage-octet decoding in accordance with th <a
 * href="https://w3c.github.io/baggage/#definition">Baggage header content</a> specification. All
 * US-ASCII characters excluding CTLs, whitespace, DQUOTE, comma, semicolon and backslash are
 * encoded in `www-form-urlencoded` encoding scheme.
 */
class BaggageCodec {

    private static final byte ESCAPE_CHAR = '%';
    private static final int RADIX = 16;

    private BaggageCodec() {
    }

    /**
     * Decodes an array of URL safe 7-bit characters into an array of original bytes. Escaped
     * characters are converted back to their original representation.
     *
     * @param bytes array of URL safe characters
     * @return array of original bytes
     */
    private static byte[] decode(byte[] bytes) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i];
            if (b == ESCAPE_CHAR) {
                try {
                    int u = digit16(bytes[++i]);
                    int l = digit16(bytes[++i]);
                    buffer.write((char) ((u << 4) + l));
                } catch (ArrayIndexOutOfBoundsException e) { // FIXME
                    throw new IllegalArgumentException("Invalid URL encoding: ", e);
                }
            } else {
                buffer.write(b);
            }
        }
        return buffer.toByteArray();
    }

    /**
     * Decodes an array of URL safe 7-bit characters into an array of original bytes. Escaped
     * characters are converted back to their original representation.
     *
     * @param value   string of URL safe characters
     * @param charset encoding of given string
     * @return decoded value
     */
    static String decode(String value, Charset charset) {
        byte[] bytes = decode(value.getBytes(StandardCharsets.US_ASCII));
        return new String(bytes, charset);
    }

    /**
     * Returns the numeric value of the character {@code b} in radix 16.
     *
     * @param b The byte to be converted.
     * @return The numeric value represented by the character in radix 16.
     */
    private static int digit16(byte b) {
        int i = Character.digit((char) b, RADIX);
        if (i == -1) {
            throw new IllegalArgumentException( // FIXME
                "Invalid URL encoding: not a valid digit (radix " + RADIX + "): " + b);
        }
        return i;
    }
}
