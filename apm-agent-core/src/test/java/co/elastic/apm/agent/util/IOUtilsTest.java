/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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

import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.ArrayBlockingQueue;

import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class IOUtilsTest  {

    @Test
    void readUtf8Stream() throws IOException {
        final CharBuffer charBuffer = CharBuffer.allocate(8);
        assertThat(IOUtils.readUtf8Stream(toInputStream("{foo}", UTF_8), charBuffer)).isTrue();
        assertThat(charBuffer.toString()).isEqualTo("{foo}");
    }

    @Test
    void readUtf8Bytes() {
        final CharBuffer charBuffer = CharBuffer.allocate(8);
        assertThat(IOUtils.decodeUtf8Bytes("{f".getBytes(UTF_8), charBuffer).isError()).isFalse();
        assertThat(IOUtils.decodeUtf8Bytes("oo}".getBytes(UTF_8), charBuffer).isError()).isFalse();
        charBuffer.flip();
        assertThat(charBuffer.toString()).isEqualTo("{foo}");
    }

    @Test
    void readUtf8BytesOffsetLength() {
        final CharBuffer charBuffer = CharBuffer.allocate(8);
        final byte[] bytes = "{foo}".getBytes(UTF_8);
        final byte[] buffer = new byte[512];
        System.arraycopy(bytes, 0, buffer, 42, bytes.length);
        assertThat(IOUtils.decodeUtf8Bytes(buffer, 42, bytes.length, charBuffer).isError()).isFalse();
        charBuffer.flip();
        assertThat(charBuffer.toString()).isEqualTo("{foo}");
    }

    @Test
    void readUtf8Byte() {
        final CharBuffer charBuffer = CharBuffer.allocate(8);
        for (byte b : "{foo}".getBytes(UTF_8)) {
            assertThat(IOUtils.decodeUtf8Byte(b, charBuffer).isError()).isFalse();
        }
        charBuffer.flip();
        assertThat(charBuffer.toString()).isEqualTo("{foo}");
    }

    @Test
    void testBytesLargerThanByteBuffer() {
        final CharBuffer charBuffer = CharBuffer.allocate(IOUtils.BYTE_BUFFER_CAPACITY * 2);
        final String longString = RandomStringUtils.randomAlphanumeric(IOUtils.BYTE_BUFFER_CAPACITY * 2);
        assertThat(IOUtils.decodeUtf8Bytes(longString.getBytes(UTF_8), charBuffer).isError()).isFalse();
        charBuffer.flip();
        assertThat(charBuffer.toString()).isEqualTo(longString);
    }

    @Test
    void testStreamLargerThanByteBuffer() throws IOException {
        final CharBuffer charBuffer = CharBuffer.allocate(IOUtils.BYTE_BUFFER_CAPACITY * 2);
        final String longString = RandomStringUtils.randomAlphanumeric(IOUtils.BYTE_BUFFER_CAPACITY * 2);
        assertThat(IOUtils.readUtf8Stream(toInputStream(longString, UTF_8), charBuffer)).isTrue();
        assertThat(charBuffer.toString()).isEqualTo(longString);
    }

    @Test
    void testReusedBuffer() throws IOException {
        final QueueBasedObjectPool<CharBuffer> charBuffers = QueueBasedObjectPool.of(new ArrayBlockingQueue<>(1), true,
            () -> CharBuffer.allocate(8), CharBuffer::clear);

        final CharBuffer charBuffer1 = charBuffers.createInstance();
        assertThat(IOUtils.readUtf8Stream(toInputStream("foo", UTF_8), charBuffer1)).isTrue();
        assertThat(charBuffer1.toString()).isEqualTo("foo");

        charBuffers.recycle(charBuffer1);

        final CharBuffer charBuffer2 = charBuffers.createInstance();
        assertThat(IOUtils.readUtf8Stream(toInputStream("barbaz", UTF_8), charBuffer2)).isTrue();
        assertThat(charBuffer2.toString()).isEqualTo("barbaz");
        assertThat((Object) charBuffer1).isSameAs(charBuffer2);

    }

    @Test
    void testOverflowStream() throws IOException {
        final CharBuffer charBuffer = CharBuffer.allocate(8);
        assertThat(IOUtils.readUtf8Stream(toInputStream("foobarbaz", UTF_8), charBuffer)).isTrue();
        assertThat(charBuffer.toString()).isEqualTo("foobarba");
    }

    @Test
    void testOverflowBytes() {
        final CharBuffer charBuffer = CharBuffer.allocate(8);
        assertThat(IOUtils.decodeUtf8Bytes("foobarbaz".getBytes(UTF_8), charBuffer).isOverflow()).isTrue();
        assertThat(IOUtils.decodeUtf8Bytes("qux".getBytes(UTF_8), charBuffer).isOverflow()).isTrue();
        charBuffer.flip();
        assertThat(charBuffer.toString()).isEqualTo("foobarba");
    }

    @Test
    void readUtf16Stream() throws IOException {
        final CharBuffer charBuffer = CharBuffer.allocate(16);
        assertThat(IOUtils.readUtf8Stream(toInputStream("{foo}", UTF_16), charBuffer)).isFalse();
        assertThat(charBuffer.length()).isZero();
    }

    @Test
    void readUtf16Bytes() {
        final CharBuffer charBuffer = CharBuffer.allocate(16);
        assertThat(IOUtils.decodeUtf8Bytes("{foo}".getBytes(UTF_16), charBuffer).isError()).isTrue();
        assertThat((CharSequence) charBuffer).isEqualTo(CharBuffer.allocate(16));
    }

    @Nonnull
    private ByteArrayInputStream toInputStream(String s, Charset charset) {
        return new ByteArrayInputStream(s.getBytes(charset));
    }
}
