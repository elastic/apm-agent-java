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
package co.elastic.apm.agent.bci.bytebuddy;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.pool.TypePool;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;

import static org.assertj.core.api.Assertions.assertThat;

class SoftlyReferencingTypePoolCacheTest {

    private LruTypePoolCache cache;

    @BeforeEach
    void setUp() {
        cache = new LruTypePoolCache(TypePool.Default.ReaderMode.FAST, 2);
    }

    @Test
    void testEvictStaleEntries() {
        cache.locate(ClassLoader.getSystemClassLoader()).register(String.class.getName(), new TypePool.Resolution.Simple(TypeDescription.STRING));
        assertThat(cache.locate(ClassLoader.getSystemClassLoader()).find(String.class.getName())).isNotNull();
        cache.evictStaleEntries(0);
        assertThat(cache.locate(ClassLoader.getSystemClassLoader()).find(String.class.getName())).isNull();
    }

    @Test
    void testLruSizeEviction() {
        cache.locate(ClassLoader.getSystemClassLoader()).register(String.class.getName(), new TypePool.Resolution.Simple(TypeDescription.STRING));
        cache.locate(ClassLoader.getSystemClassLoader()).register(Object.class.getName(), new TypePool.Resolution.Simple(TypeDescription.OBJECT));

        cache.getSharedCache().get(Object.class.getName()).getResolution(ClassLoader.getSystemClassLoader());
        cache.getSharedCache().get(String.class.getName()).getResolution(ClassLoader.getSystemClassLoader());

        // supposed to evict Object, as it's the least recently used value
        cache.locate(ClassLoader.getSystemClassLoader()).register(Throwable.class.getName(), new TypePool.Resolution.Simple(TypeDescription.THROWABLE));

        assertThat(cache.locate(ClassLoader.getSystemClassLoader()).find(String.class.getName())).isNotNull();
        assertThat(cache.locate(ClassLoader.getSystemClassLoader()).find(Throwable.class.getName())).isNotNull();
        assertThat(cache.locate(ClassLoader.getSystemClassLoader()).find(Object.class.getName())).isNull();
    }

    @Test
    void cacheSizeForPercentageOfCommittedHeap() {
        assertThat(LruTypePoolCache.cacheSizeForPercentageOfCommittedHeap(Integer.MAX_VALUE, Integer.MAX_VALUE, 0.01)).isEqualTo(Integer.MAX_VALUE);
        assertThat(LruTypePoolCache.cacheSizeForPercentageOfCommittedHeap(0, 0, 0.01)).isZero();
        int onePercentOfCommittedHeap = (int) (ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getCommitted() * 0.01);
        assertThat(LruTypePoolCache.cacheSizeForPercentageOfCommittedHeap(0, Integer.MAX_VALUE, 0.01))
            .isCloseTo(onePercentOfCommittedHeap / LruTypePoolCache.AVERAGE_SIZE_OF_TYPE_RESOLUTION, Percentage.withPercentage(0.1));
    }
}
