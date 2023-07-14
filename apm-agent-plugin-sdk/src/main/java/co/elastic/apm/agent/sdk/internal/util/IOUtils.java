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


import co.elastic.apm.agent.sdk.internal.InternalUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CoderResult;

public class IOUtils {


    private static final IOUtils.IOUtilsProvider supplier;

    static {
        supplier = InternalUtil.getServiceProvider(IOUtils.IOUtilsProvider.class);
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
        return supplier.readUtf8Stream(is, charBuffer);
    }

    public static <T> CoderResult decodeUtf8BytesFromSource(ByteSourceReader<T> reader, T src, final CharBuffer dest) {
        return supplier.decodeUtf8BytesFromSource(reader, src, dest);
    }

    public interface ByteSourceReader<S> {
        int availableBytes(S source);

        void readInto(S source, ByteBuffer into);
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
        return supplier.decodeUtf8Bytes(bytes, charBuffer);
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
        return supplier.decodeUtf8Bytes(bytes, offset, length, charBuffer);
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
        return supplier.decodeUtf8Byte(b, charBuffer);
    }

    public interface IOUtilsProvider {
        boolean readUtf8Stream(final InputStream is, final CharBuffer charBuffer) throws IOException;

        <T> CoderResult decodeUtf8BytesFromSource(IOUtils.ByteSourceReader<T> reader, T src, final CharBuffer dest);

        CoderResult decodeUtf8Bytes(final byte[] bytes, final CharBuffer charBuffer);

        CoderResult decodeUtf8Bytes(final byte[] bytes, final int offset, final int length, final CharBuffer charBuffer);

        CoderResult decodeUtf8Byte(final byte b, final CharBuffer charBuffer);
    }

}
