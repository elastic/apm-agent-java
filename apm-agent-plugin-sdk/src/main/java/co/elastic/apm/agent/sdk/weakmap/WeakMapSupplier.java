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
package co.elastic.apm.agent.sdk.weakmap;

import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentSet;

/**
 * The canonical place to get a new instance of a {@link WeakConcurrentMap}.
 * Do not instantiate a {@link WeakConcurrentMap} directly to benefit from the global cleanup of stale entries.
 */
public class WeakMapSupplier {
    private static final WeakConcurrentSet<WeakConcurrentMap<?, ?>> registeredMaps = new WeakConcurrentSet<>(WeakConcurrentSet.Cleaner.INLINE);
    private static final WeakConcurrentSet<WeakConcurrentSet<?>> registeredSets = new WeakConcurrentSet<>(WeakConcurrentSet.Cleaner.INLINE);

    public static <K, V> WeakConcurrentMap<K, V> createMap() {
        WeakConcurrentMap<K, V> result = new NullSafeWeakConcurrentMap<>(false);
        registerMap(result);
        return result;
    }

    /**
     * Registers map for global stale entry cleanup
     *
     * @param map map to register
     */
    public static void registerMap(WeakConcurrentMap<?, ?> map) {
        registeredMaps.add(map);
    }

    public static <V> WeakConcurrentSet<V> createSet() {
        WeakConcurrentSet<V> weakSet = new NullSafeWeakConcurrentSet<>(WeakConcurrentSet.Cleaner.MANUAL);
        registeredSets.add(weakSet);
        return weakSet;
    }

    /**
     * Calls {@link WeakConcurrentMap#expungeStaleEntries()} on all registered maps,
     * causing the entries of already collected keys to be removed.
     * Avoids that the maps take unnecessary space for the {@link java.util.Map.Entry}, the {@link java.lang.ref.WeakReference} and the value.
     * Failing to call this does not mean the keys cannot be collected.
     */
    public static void expungeStaleEntries() {
        for (WeakConcurrentMap<?, ?> weakMap : registeredMaps) {
            weakMap.expungeStaleEntries();
        }
        for (WeakConcurrentSet<?> weakSet : registeredSets) {
            weakSet.expungeStaleEntries();
        }
    }
}
