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
package co.elastic.apm.agent.sdk.util;

import co.elastic.apm.agent.sdk.weakconcurrent.DetachedThreadLocal;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakSet;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class WeakConcurrentProviderTestImpl implements WeakConcurrent.WeakConcurrentProvider {
    @Override
    public <K, V> WeakConcurrent.WeakMapBuilder<K, V> weakMapBuilder() {

        return new WeakConcurrent.WeakMapBuilder<K, V>() {

            @Nullable
            private WeakMap.DefaultValueSupplier<K, V> defaultValueSupplier;

            @Override
            public WeakConcurrent.WeakMapBuilder<K, V> withInitialCapacity(int initialCapacity) {
                return this;
            }

            @Override
            public WeakConcurrent.WeakMapBuilder<K, V> withDefaultValueSupplier(@Nullable WeakMap.DefaultValueSupplier<K, V> defaultValueSupplier) {
                this.defaultValueSupplier = defaultValueSupplier;
                return this;
            }

            @Override
            public WeakMap<K, V> build() {
                return new NonWeakMap<>(defaultValueSupplier);
            }
        };
    }

    @Override
    public <T> WeakConcurrent.ThreadLocalBuilder<T> threadLocalBuilder() {
        return new WeakConcurrent.ThreadLocalBuilder<T>() {

            @Nullable
            private WeakMap.DefaultValueSupplier<Thread, T> defaultValueSupplier;

            @Override
            public WeakConcurrent.ThreadLocalBuilder<T> withDefaultValueSupplier(@Nullable WeakMap.DefaultValueSupplier<Thread, T> defaultValueSupplier) {
                this.defaultValueSupplier = defaultValueSupplier;
                return this;
            }

            @Override
            public DetachedThreadLocal<T> build() {
                return new NonDetachedThreadLocal<>(defaultValueSupplier);
            }
        };
    }

    @Override
    public <E> WeakSet<E> buildSet() {
        return new NonWeakSet<>();
    }

    static class NonWeakMap<K, V> implements WeakMap<K, V> {

        private final ConcurrentMap<K, V> delegate = new ConcurrentHashMap<>();

        @Nullable
        private final DefaultValueSupplier<K, V> defaultValueSupplier;

        public NonWeakMap(@Nullable DefaultValueSupplier<K, V> defaultValueSupplier) {
            this.defaultValueSupplier = defaultValueSupplier;
        }

        @Nullable
        @Override
        public V get(K key) {
            V value = delegate.get(key);
            if (value == null && defaultValueSupplier != null) {
                value = defaultValueSupplier.getDefaultValue(key);
                delegate.put(key, value);
            }
            return value;
        }

        @Nullable
        @Override
        public V put(K key, V value) {
            return delegate.put(key, value);
        }

        @Nullable
        @Override
        public V remove(K key) {
            return delegate.remove(key);
        }

        @Override
        public boolean containsKey(K process) {
            return delegate.containsKey(process);
        }

        @Override
        public void clear() {
            delegate.clear();
        }

        @Nullable
        @Override
        public V putIfAbsent(K key, V value) {
            return delegate.putIfAbsent(key, value);
        }

        @Override
        public int approximateSize() {
            return delegate.size();
        }

        @Override
        public Iterator<Map.Entry<K, V>> iterator() {
            return delegate.entrySet().iterator();
        }
    }

    static class NonDetachedThreadLocal<T> implements DetachedThreadLocal<T> {

        private final ThreadLocal<T> delegate;

        public NonDetachedThreadLocal(@Nullable WeakMap.DefaultValueSupplier<Thread, T> defaultValueSupplier) {
            this.delegate = new ThreadLocal<>() {
                @Override
                @Nullable
                protected T initialValue() {
                    return defaultValueSupplier == null ? null : defaultValueSupplier.getDefaultValue(Thread.currentThread());
                }
            };
        }

        @Nullable
        @Override
        public T get() {
            return delegate.get();
        }

        @Nullable
        @Override
        public T getAndRemove() {
            T value = delegate.get();
            delegate.remove();
            return value;
        }

        @Override
        public void set(T value) {
            delegate.set(value);
        }

        @Override
        public void remove() {
            delegate.remove();
        }
    }

    static class NonWeakSet<K> implements WeakSet<K> {

        private final Set<K> delegate = new HashSet<>();

        @Override
        public boolean add(K element) {
            return delegate.add(element);
        }

        @Override
        public boolean contains(K element) {
            return false;
        }

        @Override
        public boolean remove(K element) {
            return delegate.remove(element);
        }

        @Override
        public Iterator<K> iterator() {
            return delegate.iterator();
        }
    }
}
