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
package co.elastic.apm.agent.util;

import co.elastic.apm.agent.objectpool.impl.BookkeeperObjectPool;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import org.jctools.queues.atomic.AtomicQueueFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jctools.queues.spec.ConcurrentQueueSpec.createBoundedMpmc;

class NoRandomAccessMapTest {
    private NoRandomAccessMap<String, String> map = new NoRandomAccessMap<>();

    @AfterEach
    void reset() {
        map.resetState();
        assertThat(map.isEmpty()).isTrue();
    }

    @Test
    void testOneEntry() {
        map.add("foo", "bar");
        int numElements = 0;
        for (NoRandomAccessMap.Entry entry : map) {
            numElements++;
            assertThat(entry.getKey()).isEqualTo("foo");
            assertThat(entry.getValue()).isEqualTo("bar");
        }
        assertThat(numElements).isEqualTo(1);
    }

    @Test
    void testNullValue() {
        map.add("foo", null);
        int numElements = 0;
        for (NoRandomAccessMap.Entry entry : map) {
            numElements++;
            assertThat(entry.getKey()).isEqualTo("foo");
            assertThat(entry.getValue()).isNull();
        }
        assertThat(numElements).isEqualTo(1);
    }

    @Test
    void testTwoEntries() {
        map.add(String.valueOf(0), "value");
        map.add(String.valueOf(1), "value");
        int index = 0;
        int numElements = 0;
        for (NoRandomAccessMap.Entry entry : map) {
            numElements++;
            assertThat(entry.getKey()).isEqualTo(String.valueOf(index++));
            assertThat(entry.getValue()).isEqualTo("value");
        }
        assertThat(numElements).isEqualTo(2);
    }

    @Test
    void testCopyFrom() {
        map.add(String.valueOf(0), "value");
        map.add(String.valueOf(1), "value");
        NoRandomAccessMap<String, String> copy = new NoRandomAccessMap<>();
        copy.add("foo", "bar");
        copy.copyFrom(map);
        int index = 0;
        int numElements = 0;
        for (NoRandomAccessMap.Entry entry : copy) {
            numElements++;
            assertThat(entry.getKey()).isEqualTo(String.valueOf(index++));
            assertThat(entry.getValue()).isEqualTo("value");
        }
        assertThat(numElements).isEqualTo(2);
    }

    @Test
    void testTwoIterations() {
        map.add(String.valueOf(0), "value");
        map.add(String.valueOf(1), "value");
        int numElements = 0;
        for (NoRandomAccessMap.Entry entry : map) {
            numElements++;
            break;
        }
        for (NoRandomAccessMap.Entry entry : map) {
            numElements++;
        }
        assertThat(numElements).isEqualTo(3);
    }

    @Test
    void testMultiThreadReadWrite() throws InterruptedException {
        final int NUM_THREADS = 100;
        final int NUM_CYCLES = 1000;
        QueueBasedObjectPool<NoRandomAccessMap<String, String>> rawMapPool = QueueBasedObjectPool.ofRecyclable(
            AtomicQueueFactory.newQueue(createBoundedMpmc(NUM_THREADS)),
            true,
            NoRandomAccessMap::new
        );
        BookkeeperObjectPool<NoRandomAccessMap<String, String>> mapPool = new BookkeeperObjectPool<>(rawMapPool);
        Random random = new Random();
        ExecutorService writersExecutor = Executors.newFixedThreadPool(NUM_THREADS);
        ExecutorService readersExecutor = Executors.newFixedThreadPool(NUM_THREADS);
        final BlockingQueue<MapWrapper> mapQueue = new ArrayBlockingQueue<>(NUM_THREADS * 2);

        for (int i = 0; i < NUM_THREADS; i++) {
            writersExecutor.submit(() -> {
                for (int j = 0; j < NUM_CYCLES; j++) {
                    NoRandomAccessMap<String, String> localMap = mapPool.createInstance();
                    // this checks through size
                    assertThat(localMap.isEmpty()).isTrue();
                    // this checks through iterator.hasNext()
                    assertThat(localMap).isEmpty();
                    String wrapperId = String.valueOf(random.nextInt());
                    int numHeaders = random.nextInt(10);
                    for (int k = 0; k < numHeaders; k++) {
                        localMap.add(wrapperId, wrapperId);
                    }
                    mapQueue.offer(new MapWrapper(wrapperId, numHeaders, localMap));
                }
            });
        }
        for (int i = 0; i < NUM_THREADS; i++) {
            readersExecutor.submit(() -> {
                for (int j = 0; j < NUM_CYCLES; j++) {
                    MapWrapper mapWrapper = mapQueue.take();
                    String wrapperId = mapWrapper.getId();
                    int numHeaders = mapWrapper.getNumHeaders();
                    NoRandomAccessMap<String, String> localMap = mapWrapper.getMap();
                    assertThat(localMap.size()).isEqualTo(numHeaders);
                    Iterator<NoRandomAccessMap.Entry<String, String>> iterator = localMap.iterator();
                    for (int k = 0; k < numHeaders; k++) {
                        NoRandomAccessMap.Entry<String, String> entry = iterator.next();
                        assertThat(entry.getKey()).isEqualTo(wrapperId);
                        assertThat(entry.getValue()).isEqualTo(wrapperId);
                    }
                    mapPool.recycle(localMap);
                }
                return null;
            });
        }
        writersExecutor.shutdown();
        writersExecutor.awaitTermination(1000, TimeUnit.MILLISECONDS);
        readersExecutor.shutdown();
        readersExecutor.awaitTermination(1000, TimeUnit.MILLISECONDS);
        assertThat(mapPool.getRequestedObjectCount()).isEqualTo(NUM_THREADS * NUM_CYCLES);
        assertThat(mapPool.getObjectsInPool()).isGreaterThan(NUM_THREADS);
        assertThat(mapPool.getObjectsInPool()).isLessThan(NUM_THREADS * 2);
    }

    private static class MapWrapper {
        private final String id;
        private final int numHeaders;
        private final NoRandomAccessMap<String, String> map;

        private MapWrapper(String id, int numHeaders, NoRandomAccessMap<String, String> map) {
            this.id = id;
            this.numHeaders = numHeaders;
            this.map = map;
        }

        public String getId() {
            return id;
        }

        public int getNumHeaders() {
            return numHeaders;
        }

        public NoRandomAccessMap<String, String> getMap() {
            return map;
        }
    }
}
