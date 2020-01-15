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
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.Resetter;

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
            garbageCreated.incrementAndGet();
            object = allocator.createInstance();
        }
        return object;
    }

    @Override
    public final void fillFromOtherPool(ObjectPool<T> otherPool, int maxElements) {
        for (int i = 0; i < maxElements; i++) {
            T obj = tryCreateInstance();
            if (obj == null) {
                return;
            }
            otherPool.recycle(obj);
        }
    }

    @Override
    public final void recycle(T obj) {
        resetter.recycle(obj);
        returnToAvailablePool(obj);
    }

    /**
     * Pushes object reference back into the available pooled instances
     *
     * @param obj recycled object to return to pool
     */
    abstract protected void returnToAvailablePool(T obj);

    @Override
    public long getGarbageCreated() {
        return garbageCreated.longValue();
    }


}
