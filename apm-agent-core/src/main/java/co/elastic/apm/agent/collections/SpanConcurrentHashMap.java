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

import co.elastic.apm.agent.tracer.AbstractSpan;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hash map dedicated to storage of in-flight spans and transactions, reference count is being incremented/decremented
 * when entry is added/removed. Usage of this map is intended for providing GC-based storage of context associated
 * to a framework-level object key, when the latter is collected by GC it allows to decrement and then recycle the
 * span/transaction.
 *
 * @param <K> key type
 * @param <V> context type
 */
public class SpanConcurrentHashMap<K, V extends AbstractSpan<?>> extends ConcurrentHashMap<K, V> {

    SpanConcurrentHashMap() {
    }

    @Nullable
    @Override
    public V remove(Object key) {
        V removed = super.remove(key);
        onRemove(removed);
        return removed;
    }

    @Override
    public V put(K key, V value) {
        V previous = super.put(key, value);
        onPut(previous, value);
        return previous;
    }

    @Override
    public V putIfAbsent(K key, V value) {
        V previous = super.putIfAbsent(key, value);
        if (previous == null) {
            onPut(null, value);
        }
        return previous;
    }

    @Override
    public void clear() {
        Iterator<V> entries = values().iterator();
        while (entries.hasNext()) {
            onRemove(entries.next());
        }
        super.clear();
    }

    private void onPut(@Nullable AbstractSpan<?> previous, AbstractSpan<?> value) {
        if (previous == null) {
            // new entry
            value.incrementReferences();
        } else if (previous != value) {
            // entry replaced
            value.incrementReferences();
            previous.decrementReferences();
        }
    }

    private void onRemove(@Nullable AbstractSpan<?> removed) {
        if (removed == null) {
            return;
        }
        removed.decrementReferences();
    }

}
