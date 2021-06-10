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
package co.elastic.apm.agent.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A subclass of {@link ConcurrentHashMap} which maintains a {@link List} of all the keys in the map.
 * It can be used to iterate over the map's keys without allocating an {@link java.util.Iterator}
 */
public class KeyListConcurrentHashMap<K, V> extends ConcurrentHashMap<K, V> {
    private final List<K> keyList = Collections.synchronizedList(new ArrayList<K>());

    @Override
    public V put(K key, V value) {
        synchronized (this) {
            final V previousValue = super.put(key, value);
            if (previousValue == null) {
                keyList.add(key);
            }
            return previousValue;
        }
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        synchronized (this) {
            for (Entry<? extends K, ? extends V> entry : m.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public V remove(Object key) {
        synchronized (this) {
            keyList.remove(key);
            return super.remove(key);
        }
    }

    @Override
    public void clear() {
        synchronized (this) {
            keyList.clear();
            super.clear();
        }
    }

    @Override
    public V putIfAbsent(K key, V value) {
        synchronized (this) {
            final V previousValue = super.putIfAbsent(key, value);
            if (previousValue == null) {
                keyList.add(key);
            }
            return previousValue;
        }
    }

    @Override
    public boolean remove(Object key, Object value) {
        synchronized (this) {
            final boolean remove = super.remove(key, value);
            if (remove) {
                keyList.remove(key);
            }
            return remove;
        }
    }

    /**
     * Returns a mutable {@link List}, roughly equal to the {@link #keySet()}.
     * <p>
     * Note that in concurrent scenarios, the key list may be a subset of the values of the respective {@link #keySet()}.
     * Entries added via the {@code compute*} family of methods are not reflected in the list.
     * </p>
     * <p>
     * Do not modify this list.
     * </p>
     *
     * @return a {@link List}, roughly equal to the {@link #keySet()}
     */
    public List<K> keyList() {
        return keyList;
    }
}
