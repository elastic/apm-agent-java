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
package co.elastic.apm.agent.impl.transaction;

import co.elastic.apm.agent.tracer.util.HexUtils;

import java.nio.charset.StandardCharsets;

abstract class CharAccessor<T> {

    abstract int length(T text);

    abstract char charAt(T text, int index);

    abstract void readHex(T text, int offset, byte[] into);

    abstract byte readHexByte(T text, int offset);

    abstract String asString(T text);

    int getLeadingWhitespaceCount(T text) {
        int count = 0;
        int len = length(text);
        while (count < len && Character.isWhitespace(charAt(text, count))) {
            count++;
        }
        return count;
    }

    int getTrailingWhitespaceCount(T text) {
        int count = 0;
        int len = length(text);
        while (count < len && Character.isWhitespace(charAt(text, len - 1 - count))) {
            count++;
        }
        return count;
    }

    public boolean containsAtOffset(T text, int offset, CharSequence substringToCheck) {
        int len = length(text);
        if (offset < 0 || offset > len) {
            throw new IllegalArgumentException("Bad offset: " + offset);
        }
        int subStrLen = substringToCheck.length();
        if (offset + subStrLen > len) {
            return false;
        }

        for (int i = 0; i < subStrLen; i++) {
            if (charAt(text, i + offset) != substringToCheck.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    public static CharAccessor<CharSequence> forCharSequence() {
        return CharSeq.INSTANCE;
    }

    public static CharAccessor<byte[]> forAsciiBytes() {
        return AsciiBytes.INSTANCE;
    }


    private static class CharSeq extends CharAccessor<CharSequence> {

        static final CharSeq INSTANCE = new CharSeq();

        @Override
        int length(CharSequence text) {
            return text.length();
        }

        @Override
        char charAt(CharSequence text, int index) {
            return text.charAt(index);
        }

        @Override
        void readHex(CharSequence text, int offset, byte[] into) {
            HexUtils.nextBytes(text, offset, into);
        }

        @Override
        byte readHexByte(CharSequence text, int offset) {
            return HexUtils.getNextByte(text, offset);
        }

        @Override
        String asString(CharSequence text) {
            return text.toString();
        }
    }

    private static class AsciiBytes extends CharAccessor<byte[]> {

        static final AsciiBytes INSTANCE = new AsciiBytes();

        @Override
        int length(byte[] text) {
            return text.length;
        }

        @Override
        char charAt(byte[] text, int index) {
            return (char) text[index];
        }

        @Override
        void readHex(byte[] text, int offset, byte[] into) {
            HexUtils.nextBytesAscii(text, offset, into);
        }

        @Override
        byte readHexByte(byte[] text, int offset) {
            return HexUtils.getNextByteAscii(text, offset);
        }

        @Override
        String asString(byte[] text) {
            return new String(text, StandardCharsets.UTF_8);
        }
    }

}
