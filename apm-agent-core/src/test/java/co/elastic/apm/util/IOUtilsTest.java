/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.util;

import co.elastic.apm.objectpool.impl.QueueBasedObjectPool;
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
    void testStringLargerThanBuffer() throws IOException {
        final CharBuffer charBuffer = CharBuffer.allocate(2000);
        final String longString = RandomStringUtils.randomAlphanumeric(2000);
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
    void testOverflow() throws IOException {
        final CharBuffer charBuffer = CharBuffer.allocate(8);
        assertThat(IOUtils.readUtf8Stream(toInputStream("foobarbaz", UTF_8), charBuffer)).isTrue();
        assertThat(charBuffer.toString()).isEqualTo("foobarba");
    }

    @Test
    void readUtf16Stream() throws IOException {
        final CharBuffer charBuffer = CharBuffer.allocate(16);
        assertThat(IOUtils.readUtf8Stream(toInputStream("{foo}", UTF_16), charBuffer)).isFalse();
        assertThat(charBuffer.length()).isZero();
    }

    @Nonnull
    private ByteArrayInputStream toInputStream(String s, Charset charset) {
        return new ByteArrayInputStream(s.getBytes(charset));
    }
}
