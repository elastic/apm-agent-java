/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.util;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This map is a mixture of a map with a single value and a map with multiple values.
 * <p>
 * When there is only one value associated with a key, this map just the key as-is when calling {@link #get(Object)}.
 * But when {@link #add(Object, Object)} has been called multiple times for a given key,
 * {@link #get(Object)} will return a collection of values.
 * </p>
 *
 * @param <K> The type of the key.
 * @param <V> The type of the value(s).
 *            Don't you dare to set it to a collection type.
 */
public class PotentiallyMultiValuedMap<K, V> extends ConcurrentHashMap<K, Object /* Collection<V> | V*/> {

    /**
     * Adds a value to this map.
     * <p>
     * If the given key already exists,
     * the current value and the given value are added to a collection,
     * which is set as the new value.
     * </p>
     *
     * @param key   The key.
     * @param value The value.
     */
    public void add(K key, V value) {
        if (containsKey(key)) {
            Object previousValue = get(key);
            if (previousValue instanceof Collection) {
                addValueToValueList(value, (Collection<V>) previousValue);
            } else {
                convertValueToMultiValue(key, (V) previousValue, value);
            }
        } else {
            put(key, value);
        }
    }

    /**
     * Gets the first value which is associated with a given key.
     *
     * @param key The key you want to get the associated value for.
     * @return The first value which is associated with a given key.
     */
    @Nullable
    public V getFirst(K key) {
        Object valueOrValueList = get(key);
        if (valueOrValueList instanceof Collection) {
            return (V) ((Collection) valueOrValueList).iterator().next();
        } else {
            return (V) valueOrValueList;
        }
    }

    /**
     * Gets all the values which age associated with a given key.
     * <p>
     * If there is only one value associated with the given key,
     * the value is wrapped inside a collection.
     * </p>
     *
     * @param key The key you want to get the associated value for.
     * @return All the values which age associated with a given key.
     */
    public Collection<V> getAll(K key) {
        if (!containsKey(key)) {
            return Collections.emptyList();
        }
        Object valueOrValueList = get(key);
        if (valueOrValueList instanceof Collection) {
            return (Collection<V>) valueOrValueList;
        } else {
            return Collections.singletonList((V) valueOrValueList);
        }
    }

    private void addValueToValueList(V value, Collection<V> valueList) {
        valueList.add(value);
    }

    private void convertValueToMultiValue(K key, V previousValue, V value) {
        Collection<V> valueList = new ArrayList<>(4);
        valueList.add(previousValue);
        valueList.add(value);
        put(key, valueList);
    }
}
