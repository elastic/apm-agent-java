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
package co.elastic.apm.agent.objectpool;

import co.elastic.apm.agent.objectpool.Recyclable;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

public class ObjectPoolTest {

    private static final int MAX_SIZE = 16;
    private ObjectPool<TestRecyclable> objectPool;

    @BeforeEach
    void setUp() {
//        objectPool = new ThreadLocalObjectPool<>(10, false, TestRecyclable::new);
        objectPool = QueueBasedObjectPool.ofRecyclable(new MpmcAtomicArrayQueue<>(MAX_SIZE), true, TestRecyclable::new);
    }

    @Test
    public void testMaxElements() throws Exception {
        for (int i = 0; i < MAX_SIZE * 2; i++) {
            objectPool.recycle(new TestRecyclable(i));
        }
        assertThat(objectPool.getObjectsInPool()).isEqualTo(MAX_SIZE);
    }

    @Test
    public void testOverconsume() throws Exception {
        for (int i = 0; i < MAX_SIZE * 2; i++) {
            objectPool.recycle(new TestRecyclable(i));
        }
        assertThat(objectPool.getObjectsInPool()).isEqualTo(MAX_SIZE);

        for (int i = 0; i < MAX_SIZE; i++) {
            assertThat(objectPool.createInstance()).isNotNull();
        }
        assertThat(objectPool.getObjectsInPool()).isEqualTo(0);

        assertThat(objectPool.createInstance()).isNotNull();
    }

    @Test
    public void testEmpty() throws Exception {
        assertThat(objectPool.getObjectsInPool()).isEqualTo(0);
        assertThat(objectPool.createInstance()).isNotNull();
        assertThat(objectPool.getObjectsInPool()).isEqualTo(0);
    }

    @Test
    public void testRecycle() throws Exception {
        final TestRecyclable instance = objectPool.createInstance();
        instance.state = 1;
        objectPool.recycle(instance);
        assertThat(instance.state).isEqualTo(0);
        assertThat(instance).isSameAs(objectPool.createInstance());
    }

    @Test
    public void testRecycleInDifferentThread() throws Exception {
        objectPool.recycle(new TestRecyclable());
        assertThat(objectPool.getObjectsInPool()).isEqualTo(1);
        TestRecyclable instance = objectPool.createInstance();
        assertThat(objectPool.getObjectsInPool()).isEqualTo(0);

        assertSoftly(softly -> {
            final Thread t1 = new Thread(() -> {
                objectPool.recycle(instance);
                assertThat(objectPool.getObjectsInPool()).isEqualTo(1);
            });
            t1.start();
            try {
                t1.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        assertThat(objectPool.getObjectsInPool()).isEqualTo(1);
    }

    private static class TestRecyclable implements Recyclable {

        private int state;

        TestRecyclable() {
        }

        TestRecyclable(int state) {
            this.state = state;
        }

        @Override
        public void resetState() {
            state = 0;
        }
    }
}
