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
package co.elastic.apm.agent.sdk.internal.util;


import co.elastic.apm.agent.sdk.internal.pooling.ObjectHandle;
import co.elastic.apm.agent.sdk.internal.pooling.ObjectPool;
import co.elastic.apm.agent.sdk.internal.pooling.ObjectPooling;

import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

public class IOUtils {

    protected static final int BYTE_BUFFER_CAPACITY = 2048;

    private static class DecoderWithBuffer {
        final ByteBuffer byteBuffer = java.nio.ByteBuffer.allocate(BYTE_BUFFER_CAPACITY);
        final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
    }

    private static final ObjectPool<? extends ObjectHandle<DecoderWithBuffer>> POOL = ObjectPooling.createWithDefaultFactory(new Callable<DecoderWithBuffer>() {
        @Override
        public DecoderWithBuffer call() throws Exception {
            return new DecoderWithBuffer();
        }
    });


    /**
     * Reads the provided {@link InputStream} into the {@link CharBuffer} without causing allocations.
     * <p>
     * The {@link InputStream} is assumed to yield an UTF-8 encoded string.
     * </p>
     * <p>
     * If the {@link InputStream} yields more chars than the {@link CharBuffer#limit()} of the provided {@link CharBuffer},
     * the rest of the input is silently ignored.
     * </p>
     *
     * @param is         the source {@link InputStream}, which should be encoded with UTF-8.
     * @param charBuffer the {@link CharBuffer} the {@link InputStream} should be written into
     * @return {@code true}, if the input stream could be decoded with the UTF-8 charset, {@code false} otherwise.
     * @throws IOException in case of errors reading from the provided {@link InputStream}
     */
    public static boolean readUtf8Stream(final InputStream is, final CharBuffer charBuffer) throws IOException {
        // to be compatible with Java 8, we have to cast to buffer because of different return types
        try (ObjectHandle<DecoderWithBuffer> pooled = POOL.createInstance()) {
            final ByteBuffer buffer = pooled.get().byteBuffer;
            final CharsetDecoder charsetDecoder = pooled.get().decoder;
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
    }

    /**
     * Decodes a UTF-8 encoded byte array into a char buffer, without allocating memory.
     * <p>
     * The {@code byte[]} is assumed to yield an UTF-8 encoded string.
     * If this is not true, the returned {@link CoderResult} will have the {@link CoderResult#isError()} flag set to true.
     * </p>
     * <p>
     * If the {@code byte[]} yields more chars than the {@link CharBuffer#limit()} of the provided {@link CharBuffer},
     * the returned {@link CoderResult} will have the {@link CoderResult#isOverflow()} flag set to true.
     * </p>
     * <p>
     * NOTE: This method does not {@link CharBuffer#flip()} the provided {@link CharBuffer} so that this method can be called multiple times
     * with the same {@link CharBuffer}.
     * If you are done with appending to the {@link CharBuffer}, you have to call {@link CharBuffer#flip()} manually.
     * </p>
     *
     * @param bytes      the source byte[], which should be encoded with UTF-8.
     * @param charBuffer the {@link CharBuffer} the {@link InputStream} should be written into
     * @return a {@link CoderResult}, indicating the success or failure of the decoding
     */
    public static CoderResult decodeUtf8Bytes(final byte[] bytes, final CharBuffer charBuffer) {
        return decodeUtf8Bytes(bytes, 0, bytes.length, charBuffer);
    }

    /**
     * Decodes a UTF-8 encoded byte array into a char buffer, without allocating memory.
     * <p>
     * The {@code byte[]} is assumed to yield an UTF-8 encoded string.
     * If this is not true, the returned {@link CoderResult} will have the {@link CoderResult#isError()} flag set to true.
     * </p>
     * <p>
     * If the {@code byte[]} yields more chars than the {@link CharBuffer#limit()} of the provided {@link CharBuffer},
     * the returned {@link CoderResult} will have the {@link CoderResult#isOverflow()} flag set to true.
     * </p>
     * <p>
     * NOTE: This method does not {@link CharBuffer#flip()} the provided {@link CharBuffer} so that this method can be called multiple times
     * with the same {@link CharBuffer}.
     * If you are done with appending to the {@link CharBuffer}, you have to call {@link CharBuffer#flip()} manually.
     * </p>
     *
     * @param bytes      the source byte[], which should be encoded with UTF-8
     * @param charBuffer the {@link CharBuffer} the {@link InputStream} should be written into
     * @param offset     the start offset in array <code>bytes</code> at which the data is read
     * @param length     the maximum number of bytes to read
     * @return a {@link CoderResult}, indicating the success or failure of the decoding
     */
    public static CoderResult decodeUtf8Bytes(final byte[] bytes, final int offset, final int length, final CharBuffer charBuffer) {
        try (ObjectHandle<DecoderWithBuffer> pooled = POOL.createInstance()) {
            final ByteBuffer pooledBuffer = pooled.get().byteBuffer;
            final CharsetDecoder charsetDecoder = pooled.get().decoder;
            // to be compatible with Java 8, we have to cast to buffer because of different return types
            final ByteBuffer buffer;
            if (pooledBuffer.capacity() < length) {
                // allocates a ByteBuffer wrapper object, the underlying byte[] is not copied
                buffer = ByteBuffer.wrap(bytes, offset, length);
            } else {
                buffer = pooledBuffer;
                buffer.put(bytes, offset, length);
                ((Buffer) buffer).position(0);
                ((Buffer) buffer).limit(length);
            }
            return decode(charBuffer, buffer, charsetDecoder);
        }
    }

    /**
     * Decodes a single UTF-8 encoded byte into a char buffer, without allocating memory.
     * <p>
     * The {@code byte} is assumed to yield an UTF-8 encoded string.
     * If this is not true, the returned {@link CoderResult} will have the {@link CoderResult#isError()} flag set to true.
     * </p>
     * <p>
     * If the provided {@link CharBuffer} has already reached its {@link CharBuffer#limit()},
     * the returned {@link CoderResult} will have the {@link CoderResult#isOverflow()} flag set to true.
     * </p>
     * <p>
     * NOTE: This method does not {@link CharBuffer#flip()} the provided {@link CharBuffer} so that this method can be called multiple times
     * with the same {@link CharBuffer}.
     * If you are done with appending to the {@link CharBuffer}, you have to call {@link CharBuffer#flip()} manually.
     * </p>
     *
     * @param b          the source byte[], which should be encoded with UTF-8
     * @param charBuffer the {@link CharBuffer} the {@link InputStream} should be written into
     * @return a {@link CoderResult}, indicating the success or failure of the decoding
     */
    public static CoderResult decodeUtf8Byte(final byte b, final CharBuffer charBuffer) {
        // to be compatible with Java 8, we have to cast to buffer because of different return types
        try (ObjectHandle<DecoderWithBuffer> pooled = POOL.createInstance()) {
            final ByteBuffer buffer = pooled.get().byteBuffer;
            final CharsetDecoder charsetDecoder = pooled.get().decoder;
            buffer.put(b);
            ((Buffer) buffer).position(0);
            ((Buffer) buffer).limit(1);
            return decode(charBuffer, buffer, charsetDecoder);
        }
    }

    public static <T> CoderResult decodeUtf8BytesFromSource(ByteSourceReader<T> reader, T src, final CharBuffer dest) {
        // to be compatible with Java 8, we have to cast to buffer because of different return types
        try (ObjectHandle<DecoderWithBuffer> pooled = POOL.createInstance()) {
            final ByteBuffer buffer = pooled.get().byteBuffer;
            final CharsetDecoder charsetDecoder = pooled.get().decoder;
            int readableBytes = reader.availableBytes(src);
            CoderResult result = null;
            while (readableBytes > 0) {
                int length = Math.min(readableBytes, BYTE_BUFFER_CAPACITY);
                ((Buffer) buffer).limit(length);
                ((Buffer) buffer).position(0);
                reader.readInto(src, buffer);
                ((Buffer) buffer).position(0);
                result = decode(dest, buffer, charsetDecoder);
                if (result.isError() || result.isOverflow()) {
                    return result;
                }
                readableBytes = reader.availableBytes(src);
            }

            return result == null ? CoderResult.OVERFLOW : result;
        }
    }

    public interface ByteSourceReader<S> {
        int availableBytes(S source);

        void readInto(S source, ByteBuffer into);
    }


    private static CoderResult decode(CharBuffer charBuffer, ByteBuffer buffer, CharsetDecoder charsetDecoder) {
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
