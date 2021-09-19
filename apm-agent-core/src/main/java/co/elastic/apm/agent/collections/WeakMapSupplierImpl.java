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

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.sdk.weakmap.DetachedThreadLocal;
import co.elastic.apm.agent.sdk.weakmap.WeakMap;
import co.elastic.apm.agent.sdk.weakmap.WeakMaps;
import co.elastic.apm.agent.sdk.weakmap.WeakSet;
import com.blogspot.mydailyjava.weaklockfree.AbstractWeakConcurrentMap;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentSet;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The canonical place to get a new instance of a {@link WeakMap}, {@link WeakSet}, or {@link DetachedThreadLocal}.
 * Do not instantiate a {@link AbstractWeakConcurrentMap} directly to benefit from the global cleanup of stale entries.
 */
public class WeakMapSupplierImpl implements WeakMaps.WeakMapSupplier {

    private static final WeakConcurrentSet<AbstractWeakConcurrentMap<?, ?, ?>> registeredMaps = new WeakConcurrentSet<>(WeakConcurrentSet.Cleaner.INLINE);
    private static final ConcurrentMap<String, DetachedThreadLocalImpl<?>> globalThreadLocals = new ConcurrentHashMap<>();

    public static <K, V extends AbstractSpan<?>> WeakMap<K, V> createWeakSpanMap() {
        SpanConcurrentHashMap<AbstractWeakConcurrentMap.WeakKey<K>, V> map = new SpanConcurrentHashMap<>();
        NullSafeWeakConcurrentMap<K, V> result = new NullSafeWeakConcurrentMap<>(map);
        registeredMaps.add(result);
        return result;
    }

    @Override
    public <K, V> WeakMaps.WeakMapBuilder<K, V> buildWeakMap() {
        return new WeakMaps.WeakMapBuilder<K, V>() {
            @Nullable
            private WeakMap.DefaultValueSupplier<K, V> defaultValueSupplier;
            private int initialCapacity = 16;
            @Override
            public WeakMaps.WeakMapBuilder<K, V> withInitialCapacity(int initialCapacity) {
                this.initialCapacity = initialCapacity;
                return this;
            }

            @Override
            public WeakMaps.WeakMapBuilder<K, V> withDefaultValueSupplier(@Nullable WeakMap.DefaultValueSupplier<K, V> defaultValueSupplier) {
                this.defaultValueSupplier = defaultValueSupplier;
                return this;
            }

            @Override
            public WeakMap<K, V> build() {
                NullSafeWeakConcurrentMap<K, V> map = new NullSafeWeakConcurrentMap<K, V>(new ConcurrentHashMap<>(initialCapacity), defaultValueSupplier);
                registeredMaps.add(map);
                return map;
            }
        };
    }

    @Override
    public <T> WeakMaps.ThreadLocalBuilder<T> buildThreadLocal() {
        return new WeakMaps.ThreadLocalBuilder<T>() {

            @Nullable
            private WeakMap.DefaultValueSupplier<Thread, T> defaultValueSupplier;
            @Nullable
            private String globalKey;
            @Override
            public WeakMaps.ThreadLocalBuilder<T> asGlobalThreadLocal(Class<?> adviceClass, String key) {
                globalKey = adviceClass.getName() + "." + key;
                return this;
            }

            @Override
            public WeakMaps.ThreadLocalBuilder<T> withDefaultValueSupplier(@Nullable WeakMap.DefaultValueSupplier<Thread, T> defaultValueSupplier) {
                this.defaultValueSupplier = defaultValueSupplier;
                return this;
            }

            @Override
            public DetachedThreadLocal<T> build() {
                DetachedThreadLocalImpl<?> threadLocal = null;
                if (globalKey != null) {
                    threadLocal = globalThreadLocals.get(globalKey);
                }
                if (threadLocal == null) {
                    threadLocal = new DetachedThreadLocalImpl<T>(WeakMapSupplierImpl.this.<Thread, T>buildWeakMap()
                        .withDefaultValueSupplier(defaultValueSupplier)
                        .build());
                }
                if (globalKey != null) {
                    globalThreadLocals.putIfAbsent(globalKey, threadLocal);
                    threadLocal = globalThreadLocals.get(globalKey);
                }

                return (DetachedThreadLocalImpl<T>) threadLocal;
            }
        };
    }

    public <V> WeakSet<V> createSet() {
        return new NullSafeWeakConcurrentSet<V>(this.<V, Boolean>buildWeakMap().build());
    }

    /**
     * Calls {@link AbstractWeakConcurrentMap#expungeStaleEntries()} on all registered maps,
     * causing the entries of already collected keys to be removed.
     * Avoids that the maps take unnecessary space for the {@link java.util.Map.Entry}, the {@link java.lang.ref.WeakReference} and the value.
     * Failing to call this does not mean the keys cannot be collected.
     */
    public static void expungeStaleEntries() {
        for (AbstractWeakConcurrentMap<?, ?, ?> weakMap : registeredMaps) {
            weakMap.expungeStaleEntries();
        }
    }
}
