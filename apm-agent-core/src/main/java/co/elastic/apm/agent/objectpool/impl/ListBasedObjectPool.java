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
package co.elastic.apm.agent.objectpool.impl;

import co.elastic.apm.agent.objectpool.Resetter;
import co.elastic.apm.agent.tracer.pooling.Allocator;
import co.elastic.apm.agent.tracer.pooling.Recyclable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * An object pool based on a plain {@link List}.
 * <p>
 * Useful in scenarios where the object pool is used in single-threaded scenarios.
 * </p>
 *
 * @param <T>
 */
public class ListBasedObjectPool<T> extends AbstractObjectPool<T> {

    private final List<T> pool;
    private final int limit;

    public static <T extends Recyclable> ListBasedObjectPool<T> ofRecyclable(int limit, Allocator<T> allocator) {
        return ListBasedObjectPool.<T>ofRecyclable(new ArrayList<T>(), limit, allocator);
    }

    public static <T extends Recyclable> ListBasedObjectPool<T> ofRecyclable(List<T> list, int limit, Allocator<T> allocator) {
        return new ListBasedObjectPool<T>(list, limit, allocator, Resetter.ForRecyclable.<T>get());
    }

    public ListBasedObjectPool(List<T> pool, int limit, Allocator<T> allocator, Resetter<T> resetter) {
        super(allocator, resetter);
        this.pool = pool;
        this.limit = limit;
    }

    @Nullable
    @Override
    public T tryCreateInstance() {
        if (!pool.isEmpty()) {
            return pool.remove(pool.size() - 1);
        }
        return null;
    }

    @Override
    protected boolean returnToPool(T obj) {
        if (pool.size() < limit) {
            pool.add(obj);
            return true;
        }
        return false;
    }

    @Override
    public int getObjectsInPool() {
        return pool.size();
    }

    @Override
    public void clear() {
        pool.clear();
    }

}
