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

import java.nio.charset.StandardCharsets;

public class HexUtils {

    public static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private HexUtils() {
        // only static utility methods, don't instantiate
    }

    /**
     * Converts a byte array to a hex encoded (aka base 16 encoded) string
     * <p>
     * From https://stackoverflow.com/a/9855338
     * </p>
     *
     * @param bytes The input byte array.
     * @return A hex encoded string representation of the byte array.
     */
    public static String bytesToHex(byte[] bytes) {
        final StringBuilder sb = new StringBuilder(bytes.length * 2);
        writeBytesAsHex(bytes, sb);
        return sb.toString();
    }

    public static void writeBytesAsHex(byte[] bytes, StringBuilder sb) {
        writeBytesAsHex(bytes, 0, bytes.length, sb);
    }

    public static void writeBytesAsHex(byte[] bytes, int offset, int length, StringBuilder sb) {
        for (int i = offset; i < offset + length; i++) {
            writeByteAsHex(bytes[i], sb);
        }
    }


    public static void writeBytesAsHexAscii(byte[] input, int inputOffset, int inputLength, byte[] outputAscii, int outputOffset) {
        if (outputAscii.length - outputOffset < inputLength * 2) {
            throw new IllegalArgumentException("Output buffer is not big enough!");
        }
        for (int i = 0; i < inputLength; i++) {
            writeBytesAsHexAscii(input[inputOffset + i], outputAscii, outputOffset + i * 2);
        }
    }

    public static void writeBytesAsHexAscii(byte input, byte[] outputAscii, int outputOffset) {
        int v = input & 0xFF;
        outputAscii[outputOffset] = (byte) HEX_CHARS[v >>> 4];
        outputAscii[outputOffset + 1] = (byte) HEX_CHARS[v & 0x0F];
    }

    public static void writeByteAsHex(byte b, StringBuilder sb) {
        int v = b & 0xFF;
        sb.append(HEX_CHARS[v >>> 4]);
        sb.append(HEX_CHARS[v & 0x0F]);
    }

    public static byte getNextByte(CharSequence hexEncodedString, int offset) {
        final int hi = hexCharToBinary(hexEncodedString.charAt(offset));
        final int lo = hexCharToBinary(hexEncodedString.charAt(offset + 1));
        if (hi == -1 || lo == -1) {
            throw new IllegalArgumentException("Not a hex encoded string: " + hexEncodedString + " at offset " + offset);
        }
        return (byte) ((hi << 4) + lo);
    }

    public static byte getNextByteAscii(byte[] asciiText, int offset) {
        final int hi = hexCharToBinary((char) asciiText[offset]);
        final int lo = hexCharToBinary((char) asciiText[offset + 1]);
        if (hi == -1 || lo == -1) {
            throw new IllegalArgumentException("Not a hex encoded string");
        }
        return (byte) ((hi << 4) + lo);
    }

    private static int hexCharToBinary(char ch) {
        if ('0' <= ch && ch <= '9') {
            return ch - '0';
        }
        if ('A' <= ch && ch <= 'F') {
            return ch - 'A' + 10;
        }
        if ('a' <= ch && ch <= 'f') {
            return ch - 'a' + 10;
        }
        return -1;
    }

    public static void nextBytes(CharSequence hexEncodedString, int offset, byte[] bytes) {
        final int charsToRead = bytes.length * 2;
        if (hexEncodedString.length() < offset + charsToRead) {
            throw new IllegalArgumentException(String.format("Can't read %d bytes from string %s with offset %d", bytes.length, hexEncodedString, offset));
        }
        for (int i = 0; i < charsToRead; i += 2) {
            bytes[i / 2] = getNextByte(hexEncodedString, offset + i);
        }
    }

    public static void nextBytesAscii(byte[] asciiText, int offset, byte[] bytes) {
        final int charsToRead = bytes.length * 2;
        if (asciiText.length < offset + charsToRead) {
            throw new IllegalArgumentException(String.format("Can't read %d bytes from byte array with length %d with offset %d", bytes.length, asciiText.length, offset));
        }
        for (int i = 0; i < charsToRead; i += 2) {
            bytes[i / 2] = getNextByteAscii(asciiText, offset + i);
        }
    }

    public static void decodeAscii(byte[] asciiHexEncodedString, int srcOffset, int srcLength, byte[] bytes, int destOffset) {
        if (asciiHexEncodedString.length < srcOffset + srcLength) {
            throw new IllegalArgumentException(String.format("Can't read %d chars from string %s with offset %d", srcLength, new String(asciiHexEncodedString, StandardCharsets.UTF_8), srcOffset));
        }
        for (int i = 0; i < srcLength; i += 2) {
            bytes[destOffset + (i / 2)] = getNextByteAscii(asciiHexEncodedString, srcOffset + i);
        }
    }

    public static void decode(String hexEncodedString, int srcOffset, int srcLength, byte[] bytes, int destOffset) {
        if (hexEncodedString.length() < srcOffset + srcLength) {
            throw new IllegalArgumentException(String.format("Can't read %d chars from string %s with offset %d", srcLength, hexEncodedString, srcOffset));
        }
        for (int i = 0; i < srcLength; i += 2) {
            bytes[destOffset + (i / 2)] = getNextByte(hexEncodedString, srcOffset + i);
        }
    }

}
