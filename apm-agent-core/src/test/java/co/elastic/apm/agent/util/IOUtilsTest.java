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

import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void exportResourceToTemp() throws UnsupportedEncodingException, URISyntaxException {
        File tmp = IOUtils.exportResourceToTemp("elasticapm.properties", UUID.randomUUID().toString(), "tmp");
        tmp.deleteOnExit();

        Path referenceFile = Paths.get(IOUtilsTest.class.getResource("/elasticapm.properties").toURI());

        assertThat(tmp)
            .hasSameContentAs(referenceFile.toFile());
    }

    @Test
    void exportResourceToTempIdempotence() throws InterruptedException
    {
        String destination = UUID.randomUUID().toString();
        File tmp = IOUtils.exportResourceToTemp("elasticapm.properties", destination, "tmp");
        tmp.deleteOnExit();
        long actual = tmp.lastModified();
        Thread.sleep(1000);
        File after = IOUtils.exportResourceToTemp("elasticapm.properties", destination, "tmp");
        assertThat(actual).isEqualTo(after.lastModified());
    }

    @Test
    void exportResourceToTemp_throwExceptionIfNotFound() {
        assertThatThrownBy(() -> IOUtils.exportResourceToTemp("nonexist", UUID.randomUUID().toString(), "tmp")).hasMessage("nonexist not found");
    }

    @Test
    void exportResourceToTempInMultipleThreads() throws InterruptedException, ExecutionException, IOException
    {
        final int nbThreads = 10;
        final ExecutorService executorService = Executors.newFixedThreadPool(nbThreads);
        final CountDownLatch countDownLatch = new CountDownLatch(nbThreads);
        final List<Future<File>> futureList = new ArrayList<>(nbThreads);
        final String tempFileNamePrefix = UUID.randomUUID().toString();

        for (int i = 0; i < nbThreads; i++) {
            futureList.add(executorService.submit(() -> {
                countDownLatch.countDown();
                countDownLatch.await();
                File file = IOUtils.exportResourceToTemp("elasticapm.properties", tempFileNamePrefix, "tmp");
                file.deleteOnExit();
                return file;
            }));
        }
        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.SECONDS);

        for (Future<File> future : futureList) {
            assertThat(future.get()).isNotNull();
            assertThat(future.get()).exists();
        }
    }

    @Nonnull
    private ByteArrayInputStream toInputStream(String s, Charset charset) {
        return new ByteArrayInputStream(s.getBytes(charset));
    }
}
