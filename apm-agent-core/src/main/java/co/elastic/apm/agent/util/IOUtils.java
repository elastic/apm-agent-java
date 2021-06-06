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

import co.elastic.apm.agent.bci.VisibleForAdvice;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@VisibleForAdvice
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
    @VisibleForAdvice
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
    @VisibleForAdvice
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
    @VisibleForAdvice
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
    @VisibleForAdvice
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

    /**
     * Why it's synchronized : if the same JVM try to lock file, we got an java.nio.channels.OverlappingFileLockException.
     * So we need to block until the file is totally written.
     */
    public static synchronized File exportResourceToDirectory(String resource, String parentDirectory, String tempFileNamePrefix,
                                                              String tempFileNameExtension) {
        try (InputStream resourceStream = IOUtils.class.getResourceAsStream("/" + resource)) {
            if (resourceStream == null) {
                throw new IllegalStateException(resource + " not found");
            }
            String hash = md5Hash(IOUtils.class.getResourceAsStream("/" + resource));
            File tempFile = new File(parentDirectory, tempFileNamePrefix + "-" + hash + tempFileNameExtension);
            if (!tempFile.exists()) {
                try (FileOutputStream out = new FileOutputStream(tempFile)) {
                    FileChannel channel = out.getChannel();
                    // If multiple JVM start on same compute, they can write in same file
                    // and this file will be corrupted.
                    try (FileLock ignored = channel.lock()) {
                        if (tempFile.length() == 0) {
                            byte[] buffer = new byte[1024];
                            for (int length; (length = resourceStream.read(buffer)) != -1; ) {
                                out.write(buffer, 0, length);
                            }
                        }
                    }
                }
            } else if (!md5Hash(new FileInputStream(tempFile)).equals(hash)) {
                throw new IllegalStateException("Invalid MD5 checksum of " + tempFile + ". Please delete this file.");
            }
            return tempFile;
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String md5Hash(InputStream resourceAsStream) throws IOException, NoSuchAlgorithmException {
        try (InputStream is = resourceAsStream) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[1024];
            DigestInputStream dis = new DigestInputStream(is, md);
            while (dis.read(buffer) != -1) {}
            return String.format("%032x", new BigInteger(1, md.digest()));
        }
    }
}
