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
package co.elastic.apm.agent.objectpool.impl;

import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.ObjectPoolTest;
import co.elastic.apm.agent.objectpool.TestRecyclable;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookkeeperObjectPoolTest extends ObjectPoolTest<BookkeeperObjectPool<TestRecyclable>> {

    @Override
    protected BookkeeperObjectPool<TestRecyclable> createObjectPool(int maxSize) {
        ObjectPool<TestRecyclable> queuePool = QueueBasedObjectPool.ofRecyclable(new MpmcAtomicArrayQueue<>(maxSize), false, TestRecyclable::new);
        return new BookkeeperObjectPool<>(queuePool);
    }

    @Test
    void singlePooledObject() {
        BookkeeperObjectPool<TestRecyclable> pool = getObjectPool();

        assertThat(pool.getGarbageCreated()).isEqualTo(0);

        TestRecyclable first = pool.createInstance();

        assertThat(pool.getRecyclablesToReturn())
            .describedAs("object taken from pool should be in the collection to return")
            .contains(first);

        pool.recycle(first);

        assertThat(pool.getRecyclablesToReturn())
            .describedAs("returned objects to pool should not be in the 'to return' collection")
            .isEmpty();

    }

    // apart from the common object pool tests, there are only a few cases where this implementation
    // has a distinct behavior, which are potential object pool mis-usages this one should properly allow to detect

    @Test
    void tryToRecycleObjectTwice() {
        BookkeeperObjectPool<TestRecyclable> pool = getObjectPool();
        TestRecyclable o = pool.createInstance();

        pool.recycle(o);

        assertThrows(IllegalStateException.class, () -> pool.recycle(o)); // TODO : see how to to it with AssertJ
    }

    @Test
    void tryToRecycleObjectNotCreatedByPool() {
        BookkeeperObjectPool<TestRecyclable> pool = getObjectPool();
        TestRecyclable o = new TestRecyclable();

        assertThrows(IllegalStateException.class, () -> pool.recycle(o));
    }

}
