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

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * Object pool that wrap two object pools as a single pool with the following properties:
 * <ul>
 * <li>object instances are created from 1st pool, then 2cnd if first fails</li>
 * <li>objects returned to the pool are stored in 2cnd pool</li>
 * </ul>
 *
 * @param <T>
 */
public class MixedObjectPool<T> extends AbstractObjectPool<T> {

    private final ObjectPool<T> primaryPool;
    private final ObjectPool<T> secondaryPool;

    public MixedObjectPool(Allocator<T> allocator, ObjectPool<T> primaryPool, ObjectPool<T> secondaryPool) {
        super(allocator, null);
        this.primaryPool = primaryPool;
        this.secondaryPool = secondaryPool;
    }

    @Nullable
    @Override
    public T tryCreateInstance() {
        final T recyclable = primaryPool.tryCreateInstance();
        if (recyclable == null) {
            secondaryPool.fillFromOtherPool(primaryPool, primaryPool.getSize());
            return primaryPool.tryCreateInstance();
        }
        return recyclable;
    }

    @Override
    void returnToAvailablePool(T obj) {
        secondaryPool.recycle(obj);
    }

    @Override
    public int getSize() {
        return -1;
    }

    @Override
    public int getObjectsInPool() {
        return -1;
    }

    @Override
    public void close() throws IOException {
        primaryPool.close();
        secondaryPool.close();
    }
}
