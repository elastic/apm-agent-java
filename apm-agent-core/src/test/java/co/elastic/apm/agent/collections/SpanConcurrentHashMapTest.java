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
package co.elastic.apm.agent.collections;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.impl.context.AbstractContext;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SpanConcurrentHashMapTest {

    @Nullable
    private Object key;

    @Test
    void putRemove() {
        TestSpan testSpan = new TestSpan();
        checkRefCount(testSpan, 0);

        key = new Object();
        WeakMap<Object, TestSpan> map = WeakConcurrentProviderImpl.createWeakSpanMap();
        map.put(key, testSpan);

        checkRefCount(testSpan, 1);

        assertThat(map.remove(key)).isSameAs(testSpan);
        checkRefCount(testSpan, 0);

        // remove when already removed should not decrement further
        assertThat(map.remove(key)).isNull();
        checkRefCount(testSpan, 0);
    }

    @ParameterizedTest
    @EnumSource(PutOperation.class)
    void putTwice(PutOperation operation) {
        TestSpan testSpan = new TestSpan();
        key = new Object();
        WeakMap<Object, TestSpan> map = WeakConcurrentProviderImpl.createWeakSpanMap();

        checkRefCount(testSpan, 0);

        operation.execute(map, key, testSpan);

        checkRefCount(testSpan, 1);

        operation.execute(map, key, testSpan);

        checkRefCount(testSpan, 1);
    }

    @Test
    void swapValues() {
        TestSpan ts1 = new TestSpan();
        TestSpan ts2 = new TestSpan();

        key = new Object();
        WeakMap<Object, TestSpan> map = WeakConcurrentProviderImpl.createWeakSpanMap();

        map.put(key, ts1);
        map.put(key, ts2);

        assertThat(map).hasSize(1);

        checkRefCount(ts1, 0);
        checkRefCount(ts2, 1);
    }

    @Test
    void testPutIfAbsent() {
        TestSpan ts1 = new TestSpan();
        TestSpan ts2 = new TestSpan();

        key = new Object();
        WeakMap<Object, TestSpan> map = WeakConcurrentProviderImpl.createWeakSpanMap();

        map.putIfAbsent(key, ts1);
        map.putIfAbsent(key, ts2);

        assertThat(map).hasSize(1);

        checkRefCount(ts1, 1);
        checkRefCount(ts2, 0);
    }

    private enum PutOperation {
        put,
        putIfAbsent;

        void execute(WeakMap<Object, TestSpan> map, Object key, TestSpan value) {
            switch (this) {
                case put:
                    map.put(key, value);
                    break;
                case putIfAbsent:
                     map.putIfAbsent(key, value);
                    break;
                default:
                    throw new IllegalStateException("");
            }
        }
    }

    @Test
    void clear() {
        WeakMap<Object, TestSpan> map = WeakConcurrentProviderImpl.createWeakSpanMap();

        List<AbstractSpan<?>> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            TestSpan span = new TestSpan();
            list.add(span);
            map.put(i, span);
        }
        checkRefCount(list, 1);

        // clear twice, should only decrement once
        map.clear();
        checkRefCount(list, 0);

        map.clear();
        checkRefCount(list, 0);
    }

    @Test
    void weakMapDecrementOnStaleKeyGC() {
        key = new Object();
        TestSpan span = new TestSpan();

        WeakMap<Object, AbstractSpan<?>> map = WeakConcurrentProviderImpl.createWeakSpanMap();

        map.put(key, span);

        checkRefCount(span, 1);

        await().untilAsserted(() -> assertThat(map.approximateSize()).isEqualTo(1));

        key = null;

        await().untilAsserted(() -> {
            System.gc();
            WeakConcurrentProviderImpl.expungeStaleEntries();
            assertThat(map.approximateSize()).isEqualTo(0);
        });

        checkRefCount(span, 0);
    }


    private void checkRefCount(List<AbstractSpan<?>> spans, int expected) {
        spans.forEach(span -> checkRefCount(span, expected));
    }

    private void checkRefCount(AbstractSpan<?> span, int expected) {
        assertThat(span.getReferenceCount()).isEqualTo(expected);
    }

    private static class TestSpan extends AbstractSpan<TestSpan> {

        public TestSpan() {
            super(MockTracer.create());
        }

        @Nullable
        @Override
        public Transaction getTransaction() {
            return null;
        }

        @Override
        public AbstractContext getContext() {
            return null;
        }

        @Override
        protected void beforeEnd(long epochMicros) {

        }

        @Override
        protected void afterEnd() {

        }

        @Override
        protected void recycle() {

        }

        @Override
        protected TestSpan thiz() {
            return null;
        }
    }

}
