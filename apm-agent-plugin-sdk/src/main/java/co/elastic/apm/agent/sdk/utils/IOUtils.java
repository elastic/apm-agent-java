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
package co.elastic.apm.agent.sdk.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;

public class IOUtils {
    protected static final int BYTE_BUFFER_CAPACITY = 2048;
    protected static final ThreadLocal<ByteBuffer> threadLocalByteBuffer = new ThreadLocal<ByteBuffer>() {
        @Override
        protected ByteBuffer initialValue() {
            return ByteBuffer.allocate(BYTE_BUFFER_CAPACITY);
        }
    };
    protected static final ThreadLocal<CharsetDecoder> threadLocalCharsetDecoder = new ThreadLocal<CharsetDecoder>() {
        @Override
        protected CharsetDecoder initialValue() {
            return StandardCharsets.UTF_8.newDecoder();
        }
    };

    public static boolean readUtf8Stream(final InputStream is, final CharBuffer charBuffer) throws IOException {
        // to be compatible with Java 8, we have to cast to buffer because of different return types
        final ByteBuffer buffer = threadLocalByteBuffer.get();
        final CharsetDecoder charsetDecoder = threadLocalCharsetDecoder.get();
        try {
            final byte[] bufferArray = buffer.array();
            for (int read = is.read(bufferArray); read != -1; read = is.read(bufferArray)) {
                ((Buffer) buffer).limit(read);
                final CoderResult coderResult = charsetDecoder.decode(buffer, charBuffer, true);
                ((Buffer) buffer).clear();
                if (coderResult.isError()) {
                    // this is not UTF-8
                    ((Buffer) charBuffer).clear();
                    return false;
                } else if (coderResult.isOverflow()) {
                    // stream yields more chars than the charBuffer can hold
                    break;
                }
            }
            charsetDecoder.flush(charBuffer);
            return true;
        } finally {
            ((Buffer) charBuffer).flip();
            ((Buffer) buffer).clear();
            charsetDecoder.reset();
            is.close();
        }
    }

    public static CoderResult decodeUtf8Bytes(final byte[] bytes, final CharBuffer charBuffer) {
        return decodeUtf8Bytes(bytes, 0, bytes.length, charBuffer);
    }

    public static CoderResult decodeUtf8Bytes(final byte[] bytes, final int offset, final int length, final CharBuffer charBuffer) {
        // to be compatible with Java 8, we have to cast to buffer because of different return types
        final ByteBuffer buffer;
        if (BYTE_BUFFER_CAPACITY < length) {
            // allocates a ByteBuffer wrapper object, the underlying byte[] is not copied
            buffer = ByteBuffer.wrap(bytes, offset, length);
        } else {
            buffer = threadLocalByteBuffer.get();
            buffer.put(bytes, offset, length);
            ((Buffer) buffer).position(0);
            ((Buffer) buffer).limit(length);
        }
        return decode(charBuffer, buffer);
    }

    public static CoderResult decodeUtf8Byte(final byte b, final CharBuffer charBuffer) {
        // to be compatible with Java 8, we have to cast to buffer because of different return types
        final ByteBuffer buffer = threadLocalByteBuffer.get();
        buffer.put(b);
        ((Buffer) buffer).position(0);
        ((Buffer) buffer).limit(1);
        return decode(charBuffer, buffer);
    }

    protected static CoderResult decode(CharBuffer charBuffer, ByteBuffer buffer) {
        final CharsetDecoder charsetDecoder = threadLocalCharsetDecoder.get();
        try {
            final CoderResult coderResult = charsetDecoder.decode(buffer, charBuffer, true);
            charsetDecoder.flush(charBuffer);
            return coderResult;
        } finally {
            ((Buffer) buffer).clear();
            charsetDecoder.reset();
        }
    }

}
