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

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

public class IOUtils {

    protected static final int BYTE_BUFFER_CAPACITY = 2048;

    private static final ObjectPool<? extends ObjectHandle<ByteBuffer>> BYTE_BUFFER_POOL = ObjectPooling.createWithDefaultFactory(new Callable<ByteBuffer>() {
        @Override
        public ByteBuffer call() throws Exception {
            return ByteBuffer.allocate(BYTE_BUFFER_CAPACITY);
        }
    });

    private static final Set<String> UNSUPPORTED_CHARSETS = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Map<String, ObjectPool<? extends ObjectHandle<CharsetDecoder>>> DECODER_POOLS
        = new ConcurrentHashMap<String, ObjectPool<? extends ObjectHandle<CharsetDecoder>>>();

    private static final String UTF8_CHARSET_NAME = StandardCharsets.UTF_8.name().toLowerCase();

    @Nullable
    private static ObjectHandle<CharsetDecoder> getPooledCharsetDecoder(String charsetName) {
        if (!isLowerCase(charsetName)) {
            charsetName = charsetName.toLowerCase();
        }
        ObjectPool<? extends ObjectHandle<CharsetDecoder>> decoderPool = DECODER_POOLS.get(charsetName);
        if (decoderPool != null) {
            return decoderPool.createInstance();
        }
        if (UNSUPPORTED_CHARSETS.contains(charsetName)) {
            return null;
        }
        try {
            final Charset charset = Charset.forName(charsetName);
            decoderPool = ObjectPooling.createWithDefaultFactory(new Callable<CharsetDecoder>() {
                @Override
                public CharsetDecoder call() throws Exception {
                    return charset.newDecoder();
                }
            });
            DECODER_POOLS.put(charsetName, decoderPool);
            return decoderPool.createInstance();
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            UNSUPPORTED_CHARSETS.add(charsetName);
            return null;
        }
    }

    private static boolean isLowerCase(String charsetName) {
        for (int i = 0; i < charsetName.length(); i++) {
            if (!Character.isLowerCase(charsetName.charAt(i))) {
                return false;
            }
        }
        return true;
    }


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
        try (
            ObjectHandle<ByteBuffer> bufferHandle = BYTE_BUFFER_POOL.createInstance();
            ObjectHandle<CharsetDecoder> decoderHandle = getPooledCharsetDecoder(UTF8_CHARSET_NAME);
        ) {
            final ByteBuffer buffer = bufferHandle.get();
            final CharsetDecoder charsetDecoder = decoderHandle.get();
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
        try (
            ObjectHandle<ByteBuffer> bufferHandle = BYTE_BUFFER_POOL.createInstance();
            ObjectHandle<CharsetDecoder> decoderHandle = getPooledCharsetDecoder(UTF8_CHARSET_NAME);
        ) {
            final ByteBuffer pooledBuffer = bufferHandle.get();
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
            return decode(charBuffer, buffer, decoderHandle.get());
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
        try (
            ObjectHandle<ByteBuffer> bufferHandle = BYTE_BUFFER_POOL.createInstance();
            ObjectHandle<CharsetDecoder> decoderHandle = getPooledCharsetDecoder(UTF8_CHARSET_NAME);
        ) {
            final ByteBuffer buffer = bufferHandle.get();
            buffer.put(b);
            ((Buffer) buffer).position(0);
            ((Buffer) buffer).limit(1);
            return decode(charBuffer, buffer, decoderHandle.get());
        }
    }

    public static <T> CoderResult decodeUtf8BytesFromSource(ByteSourceReader<T> reader, T src, final CharBuffer dest) {
        // to be compatible with Java 8, we have to cast to buffer because of different return types
        try (
            ObjectHandle<ByteBuffer> bufferHandle = BYTE_BUFFER_POOL.createInstance();
            ObjectHandle<CharsetDecoder> decoderHandle = getPooledCharsetDecoder(UTF8_CHARSET_NAME);
        ) {
            final ByteBuffer buffer = bufferHandle.get();
            int readableBytes = reader.availableBytes(src);
            CoderResult result = null;
            while (readableBytes > 0) {
                int length = Math.min(readableBytes, BYTE_BUFFER_CAPACITY);
                ((Buffer) buffer).limit(length);
                ((Buffer) buffer).position(0);
                reader.readInto(src, buffer);
                ((Buffer) buffer).position(0);
                result = decode(dest, buffer, decoderHandle.get());
                if (result.isError() || result.isOverflow()) {
                    return result;
                }
                readableBytes = reader.availableBytes(src);
            }

            return result == null ? CoderResult.OVERFLOW : result;
        }
    }

    @Nullable
    public static byte[] copyToByteArray(List<ByteBuffer> buffers) {
        int totalSize = 0;
        for (ByteBuffer buff : buffers) {
            totalSize += buff.position();
        }
        byte[] data = new byte[totalSize];

        int off = 0;
        for (ByteBuffer buff : buffers) {
            int len = buff.position();
            ((Buffer) buff).position(0);
            buff.get(data, off, len);
            off += len;
        }
        return data;
    }

    public interface ByteSourceReader<S> {
        int availableBytes(S source);

        void readInto(S source, ByteBuffer into);
    }

    /**
     * @param input       the byte data to decode
     * @param output      the buffer to decode into
     * @param charsetName the name of the charset
     * @return null, if the charset is not known/supported. Otherwise the result of the decoding operation.
     */
    @Nullable
    public static CoderResult decode(List<ByteBuffer> input, CharBuffer output, String charsetName) {
        try (ObjectHandle<CharsetDecoder> decoderHandle = getPooledCharsetDecoder(charsetName)) {
            if (decoderHandle == null) {
                return null; //charset is unsupported
            }
            CharsetDecoder charsetDecoder = decoderHandle.get();
            try {
                Iterator<ByteBuffer> it = input.iterator();
                while (it.hasNext()) {
                    ByteBuffer currInput = it.next();
                    boolean isLast = !it.hasNext();
                    CoderResult coderResult = charsetDecoder.decode(currInput, output, isLast);
                    if (coderResult.isError() || coderResult.isOverflow()) {
                        return coderResult;
                    }
                }
                return charsetDecoder.flush(output);
            } finally {
                charsetDecoder.reset();
            }
        }
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
