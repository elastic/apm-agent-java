/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.agent.objectpool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

public abstract class ObjectPoolTest<T extends ObjectPool<TestRecyclable>> {

    private static final int MAX_SIZE = 16;
    private T objectPool;

    @BeforeEach
    void beforeEach() {
        objectPool = createObjectPool(MAX_SIZE);
    }

    public T getObjectPool() {
        return objectPool;
    }

    protected abstract T createObjectPool(int maxSize);

    @Test
    public void testMaxElements() {
        List<TestRecyclable> recyclables = new ArrayList<>();
        for (int i = 0; i < MAX_SIZE * 2; i++) {
            TestRecyclable instance = objectPool.createInstance();
            instance.setState(i + 1);
            recyclables.add(instance);
        }

        for (int i = 0; i < recyclables.size(); i++) {
            objectPool.recycle(recyclables.get(i));
            long garbageCreated = objectPool.getGarbageCreated();
            if (i < MAX_SIZE) {
                assertThat(objectPool.getObjectsInPool())
                    .isEqualTo(i + 1);
                assertThat(garbageCreated)
                    .describedAs("no garbage created until reaching pool capacity")
                    .isEqualTo(0);
            } else {
                assertThat(objectPool.getObjectsInPool())
                    .describedAs("pool capacity should stay at max capacity")
                    .isEqualTo(MAX_SIZE);
                assertThat(garbageCreated)
                    .describedAs("garbage created for each returned instance over pool capacity")
                    .isEqualTo(i - MAX_SIZE + 1);
            }
        }

        assertThat(objectPool.getObjectsInPool())
            .describedAs("pool max size should be enforced")
            .isEqualTo(MAX_SIZE);

        assertThat(objectPool.getGarbageCreated())
            .describedAs("pool created garbage should be equal to instances count")
            .isEqualTo(MAX_SIZE);
    }

    @Test
    public void testOverconsume() {

        // this test provides us in the expected state where we have the max number of objects in pool allocated
        testMaxElements();

        assertThat(objectPool.getObjectsInPool()).isEqualTo(MAX_SIZE);

        for (int i = 0; i < MAX_SIZE; i++) {
            assertThat(objectPool.createInstance()).isNotNull();
        }
        assertThat(objectPool.getObjectsInPool()).isEqualTo(0);

        assertThat(objectPool.createInstance()).isNotNull();
    }

    @Test
    public void testEmpty() {
        assertThat(objectPool.getObjectsInPool()).isEqualTo(0);
        assertThat(objectPool.createInstance()).isNotNull();
        assertThat(objectPool.getObjectsInPool()).isEqualTo(0);
    }

    @Test
    public void testRecycle() {
        TestRecyclable instance = objectPool.createInstance();
        instance.setState(1);
        objectPool.recycle(instance);
        assertThat(instance.getState()).isEqualTo(0);
        assertThat(instance).isSameAs(objectPool.createInstance());
    }

    @Test
    public void testRecycleInDifferentThread() {
        TestRecyclable instance = objectPool.createInstance();
        objectPool.recycle(instance);
        assertThat(objectPool.getObjectsInPool()).isEqualTo(1);

        final TestRecyclable recycledInstance = objectPool.createInstance();
        assertThat(objectPool.getObjectsInPool()).isEqualTo(0);

        assertSoftly(softly -> {
            Thread t1 = new Thread(() -> {
                objectPool.recycle(recycledInstance);
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

}
