/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.objectpool.impl;

import co.elastic.apm.agent.objectpool.Allocator;
import co.elastic.apm.agent.objectpool.Recyclable;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;

public class ListBasedObjectPool<T> extends AbstractObjectPool<T> {

    private final List<T> pool;
    private final int limit;
    private final Resetter<T> resetter;

    public static <T extends Recyclable> ListBasedObjectPool<T> ofRecyclable(List<T> list, int limit, Allocator<T> allocator) {
        return new ListBasedObjectPool<>(list, limit, allocator, Resetter.ForRecyclable.<T>get());
    }

    public ListBasedObjectPool(List<T> pool, int limit, Allocator<T> allocator, Resetter<T> resetter) {
        super(allocator);
        this.pool = pool;
        this.limit = limit;
        this.resetter = resetter;
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
    public void recycle(T obj) {
        if (pool.size() < limit) {
            resetter.recycle(obj);
            pool.add(obj);
        }
    }

    @Override
    public int getSize() {
        return pool.size();
    }

    @Override
    public int getObjectsInPool() {
        return pool.size();
    }

    @Override
    public void close() throws IOException {
    }
}
