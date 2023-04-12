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

import co.elastic.apm.agent.sdk.weakconcurrent.DetachedThreadLocal;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakSet;
import co.elastic.apm.agent.tracer.AbstractSpan;
import com.blogspot.mydailyjava.weaklockfree.AbstractWeakConcurrentMap;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentSet;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The canonical place to get a new instance of a {@link WeakMap}, {@link WeakSet}, or {@link DetachedThreadLocal}.
 * Do not instantiate a {@link AbstractWeakConcurrentMap} directly to benefit from the global cleanup of stale entries.
 */
public class WeakConcurrentProviderImpl implements WeakConcurrent.WeakConcurrentProvider {

    private static final WeakConcurrentSet<AbstractWeakConcurrentMap<?, ?, ?>> registeredMaps = new WeakConcurrentSet<>(WeakConcurrentSet.Cleaner.INLINE);

    public static <K, V extends AbstractSpan<?>> WeakMap<K, V> createWeakSpanMap() {
        SpanConcurrentHashMap<AbstractWeakConcurrentMap.WeakKey<K>, V> map = new SpanConcurrentHashMap<>();
        NullSafeWeakConcurrentMap<K, V> result = new NullSafeWeakConcurrentMap<>(map);
        registeredMaps.add(result);
        return result;
    }

    @Override
    public <K, V> WeakConcurrent.WeakMapBuilder<K, V> weakMapBuilder() {
        return new WeakConcurrent.WeakMapBuilder<K, V>() {
            @Nullable
            private WeakMap.DefaultValueSupplier<K, V> defaultValueSupplier;
            private int initialCapacity = 16;
            @Override
            public WeakConcurrent.WeakMapBuilder<K, V> withInitialCapacity(int initialCapacity) {
                this.initialCapacity = initialCapacity;
                return this;
            }

            @Override
            public WeakConcurrent.WeakMapBuilder<K, V> withDefaultValueSupplier(@Nullable WeakMap.DefaultValueSupplier<K, V> defaultValueSupplier) {
                this.defaultValueSupplier = defaultValueSupplier;
                return this;
            }

            @Override
            public WeakMap<K, V> build() {
                NullSafeWeakConcurrentMap<K, V> map = new NullSafeWeakConcurrentMap<K, V>(new ConcurrentHashMap<AbstractWeakConcurrentMap.WeakKey<K>, V>(initialCapacity), defaultValueSupplier);
                registeredMaps.add(map);
                return map;
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
                return new DetachedThreadLocalImpl<T>(WeakConcurrentProviderImpl.this.<Thread, T>weakMapBuilder()
                    .withDefaultValueSupplier(defaultValueSupplier)
                    .build());
            }
        };
    }

    public <V> WeakSet<V> buildSet() {
        return new NullSafeWeakConcurrentSet<V>(this.<V, Boolean>weakMapBuilder().build());
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
