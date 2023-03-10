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

import co.elastic.apm.agent.tracer.pooling.Recyclable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * This map is a mixture of a map with a single value and a map with multiple values.
 * <p>
 * When there is only one value associated with a key, this map just the key as-is when calling {@link #get(String)}.
 * But when {@link #add(String, String)} has been called multiple times for a given key,
 * {@link #get(String)} will return a collection of values.
 * </p>
 */
public class PotentiallyMultiValuedMap implements Recyclable, co.elastic.apm.agent.tracer.metadata.PotentiallyMultiValuedMap {

    private final List<String> keys;
    private final List<Object> values;

    public PotentiallyMultiValuedMap() {
        this(10);
    }

    public PotentiallyMultiValuedMap(int initialSize) {
        keys = new ArrayList<>(initialSize);
        values = new ArrayList<>(initialSize);
    }

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
    @Override
    public void add(String key, String value) {
        final int index = indexOfIgnoreCase(key);
        if (index >= 0) {
            Object previousValue = values.get(index);
            if (previousValue instanceof List) {
                addValueToValueList(value, (List<String>) previousValue);
            } else {
                convertValueToMultiValue(index, (String) previousValue, value);
            }
        } else {
            keys.add(key);
            values.add(value);
        }
    }


    public void set(String key, String[] values) {
        if (values.length > 0) {
            if (values.length == 1) {
                keys.add(key);
                this.values.add(values[0]);
            } else {
                keys.add(key);
                this.values.add(Arrays.asList(values));
            }
        }
    }

    private int indexOfIgnoreCase(String key) {
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).equalsIgnoreCase(key)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Gets the first value which is associated with a given key.
     *
     * @param key The key you want to get the associated value for.
     * @return The first value which is associated with a given key.
     */
    @Override
    @Nullable
    public String getFirst(String key) {
        Object valueOrValueList = get(key);
        if (valueOrValueList instanceof List) {
            return (String) ((List) valueOrValueList).get(0);
        } else {
            return (String) valueOrValueList;
        }
    }

    @Override
    @Nullable
    public Object get(String key) {
        final int index = indexOfIgnoreCase(key);
        if (index == -1) {
            return null;
        }
        return values.get(index);
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
    public List<String> getAll(String key) {
        final int index = indexOfIgnoreCase(key);
        if (index == -1) {
            return Collections.emptyList();
        }
        Object valueOrValueList = values.get(index);
        if (valueOrValueList instanceof List) {
            return (List<String>) valueOrValueList;
        } else {
            return Collections.singletonList((String) valueOrValueList);
        }
    }

    private void addValueToValueList(String value, List<String> valueList) {
        valueList.add(value);
    }

    private void convertValueToMultiValue(int index, String previousValue, String value) {
        List<String> valueList = new ArrayList<>(4);
        valueList.add(previousValue);
        valueList.add(value);
        values.set(index, valueList);
    }

    @Override
    public boolean isEmpty() {
        return keys.isEmpty();
    }

    @Override
    public void resetState() {
        keys.clear();
        values.clear();
    }

    public String getKey(int i) {
        return keys.get(i);
    }

    public Object getValue(int i) {
        return values.get(i);
    }

    public int size() {
        return keys.size();
    }

    public void copyFrom(PotentiallyMultiValuedMap other) {
        this.keys.addAll(other.keys);
        this.values.addAll(other.values);
    }

    public void removeIgnoreCase(String key) {
        final int index = indexOfIgnoreCase(key);
        if (index != -1) {
            keys.remove(index);
            values.remove(index);
        }
    }

    public void set(int index, String value) {
        values.set(index, value);
    }

    @Override
    public boolean containsIgnoreCase(String key) {
        return indexOfIgnoreCase(key) != -1;
    }
}
