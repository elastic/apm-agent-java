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

import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.ObjectPoolTest;
import co.elastic.apm.agent.objectpool.TestRecyclable;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;
import org.junit.jupiter.api.Test;

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

        ObjectPool<TestRecyclable> pool = QueueBasedObjectPool.ofRecyclable(new MpmcAtomicArrayQueue<>(capacity), true, TestRecyclable::new);

        assertThat(pool.getGarbageCreated()).isEqualTo(0);
        assertThat(pool.getObjectsInPool()).isEqualTo(capacity);
    }
}
