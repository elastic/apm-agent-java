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
package co.elastic.apm.agent.objectpool.impl;

import co.elastic.apm.agent.objectpool.ObservableObjectPool;
import co.elastic.apm.agent.objectpool.ObjectPoolTest;
import co.elastic.apm.agent.objectpool.TestRecyclable;
import co.elastic.apm.agent.sdk.internal.util.IOUtils;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.ArrayBlockingQueue;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class QueueBasedObjectPoolTest extends ObjectPoolTest<QueueBasedObjectPool<TestRecyclable>> {

    @Override
    protected QueueBasedObjectPool<TestRecyclable> createObjectPool(int maxSize) {
        return QueueBasedObjectPool.ofRecyclable(new MpmcAtomicArrayQueue<>(maxSize), false, TestRecyclable::new);
    }

    @Test
    void preAllocationShouldCreateObjectsInPool() {
        // we have to use a power of two as capacity, otherwise actual capacity will differ
        int capacity = 8;

        ObservableObjectPool<TestRecyclable> pool = QueueBasedObjectPool.ofRecyclable(new MpmcAtomicArrayQueue<>(capacity), true, TestRecyclable::new);

        assertThat(pool.getGarbageCreated()).isEqualTo(0);
        assertThat(pool.getObjectsInPool()).isEqualTo(capacity);
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

    @Nonnull
    private ByteArrayInputStream toInputStream(String s, Charset charset) {
        return new ByteArrayInputStream(s.getBytes(charset));
    }
}
