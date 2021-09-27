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
import co.elastic.apm.agent.sdk.weakconcurrent.WeakSet;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentSet;

import java.util.Iterator;
import java.util.Map;

import static co.elastic.apm.agent.collections.NullCheck.isNullValue;

/**
 * {@link WeakConcurrentSet} implementation that prevents throwing {@link NullPointerException} and helps debugging if needed
 *
 * @param <V> value type
 */
public class NullSafeWeakConcurrentSet<V> implements WeakSet<V> {

    private final WeakMap<V, Boolean> map;

    NullSafeWeakConcurrentSet(WeakMap<V, Boolean> map) {
        this.map = map;
    }

    @Override
    public boolean add(V value) {
        if (isNullValue(value)) {
            return false;
        }
        return map.put(value, Boolean.TRUE) == null;
    }

    @Override
    public boolean contains(V value) {
        if (isNullValue(value)) {
            return false;
        }
        return map.containsKey(value);
    }

    @Override
    public boolean remove(V value) {
        if (isNullValue(value)) {
            return false;
        }
        return map.remove(value) != null;
    }

    @Override
    public Iterator<V> iterator() {
        return new ReducingIterator<V>(map.iterator());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Iterator<V> it = iterator(); it.hasNext(); ) {
            sb.append(it.next());
            if (it.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    private static class ReducingIterator<V> implements Iterator<V> {

        private final Iterator<Map.Entry<V, Boolean>> iterator;

        private ReducingIterator(Iterator<Map.Entry<V, Boolean>> iterator) {
            this.iterator = iterator;
        }

        @Override
        public void remove() {
            iterator.remove();
        }

        @Override
        public V next() {
            return iterator.next().getKey();
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }
    }
}
