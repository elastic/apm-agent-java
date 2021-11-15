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

import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentMap;

import static co.elastic.apm.agent.collections.NullCheck.isNullKey;
import static co.elastic.apm.agent.collections.NullCheck.isNullValue;

/**
 * {@link CachedKeyWeakConcurrentMap} implementation that prevents throwing {@link NullPointerException} and helps debugging if needed
 *
 * @param <K> key type
 * @param <V> value type
 */
public class NullSafeWeakConcurrentMap<K, V> extends CachedKeyWeakConcurrentMap<K, V> {

    private final DefaultValueSupplier<K, V> defaultValueSupplier;

    NullSafeWeakConcurrentMap(ConcurrentMap<WeakKey<K>, V> target) {
        this(target, new NullValueSupplier<K, V>());
    }

    NullSafeWeakConcurrentMap(ConcurrentMap<WeakKey<K>, V> target, @Nullable DefaultValueSupplier<K, V> defaultValueSupplier) {
        super(target);
        this.defaultValueSupplier = defaultValueSupplier != null ? defaultValueSupplier : new NullValueSupplier<K, V>();
    }

    @Nullable
    @Override
    public V get(K key) {
        if (isNullKey(key)) {
            // super implementation silently adds entries from default value when there is none
            // in the case of 'null', we won't return the default value nor create a map entry with it.
            return null;
        }
        return super.get(key);
    }

    @Nullable
    @Override
    public V getIfPresent(K key) {
        if (isNullKey(key)) {
            return null;
        }
        return super.getIfPresent(key);
    }

    @Override
    public boolean containsKey(K key) {
        if (isNullKey(key)) {
            return false;
        }
        return super.containsKey(key);
    }

    @Nullable
    @Override
    public V put(K key, V value) {
        if (isNullKey(key) || isNullValue(value)) {
            return null;
        }
        return super.put(key, value);
    }

    @Nullable
    @Override
    public V putIfAbsent(K key, V value) {
        if (isNullKey(key) || isNullValue(value)) {
            return null;
        }
        return super.putIfAbsent(key, value);
    }

    @Nullable
    @Override
    public V putIfProbablyAbsent(K key, V value) {
        if (isNullKey(key) || isNullValue(value)) {
            return null;
        }
        return super.putIfProbablyAbsent(key, value);
    }

    @Nullable
    @Override
    public V remove(K key) {
        if (isNullKey(key)) {
            return null;
        }
        return super.remove(key);
    }

    @Nullable
    @Override
    protected V defaultValue(K key) {
        return defaultValueSupplier.getDefaultValue(key);
    }

    private static class NullValueSupplier<K, V> implements WeakMap.DefaultValueSupplier<K, V> {
        @Nullable
        @Override
        public V getDefaultValue(K key) {
            return null;
        }
    }
}
