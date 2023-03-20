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

import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.Resetter;
import co.elastic.apm.agent.tracer.pooling.Allocator;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractObjectPool<T> implements ObjectPool<T> {

    protected final Allocator<T> allocator;
    protected final Resetter<T> resetter;
    private final AtomicInteger garbageCreated;

    protected AbstractObjectPool(Allocator<T> allocator, Resetter<T> resetter) {
        this.allocator = allocator;
        this.resetter = resetter;
        this.garbageCreated = new AtomicInteger();
    }

    @Override
    public final T createInstance() {
        T object = tryCreateInstance();
        if (object == null) {
            // pool does not have available instance, falling back to creating a new one
            object = allocator.createInstance();
        }
        return object;
    }

    @Override
    public final void recycle(T obj) {
        resetter.recycle(obj);
        if (!returnToPool(obj)) {
            // when not able to return object to pool, it means this object will be garbage-collected
            garbageCreated.incrementAndGet();
        }
    }

    @Override
    public final long getGarbageCreated() {
        return garbageCreated.longValue();
    }

    /**
     * Pushes object reference back into the available pooled instances
     *
     * @param obj recycled object to return to pool
     * @return true if object has been returned to pool, false if pool is already full
     */
    abstract protected boolean returnToPool(T obj);

    /**
     * Tries to create an instance in pool
     *
     * @return {@code null} if pool capacity is exhausted
     */
    @Nullable
    abstract protected T tryCreateInstance();
}
