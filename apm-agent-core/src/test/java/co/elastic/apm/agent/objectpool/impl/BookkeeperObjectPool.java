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
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link ObjectPool} wrapper implementation that keeps track of all created object instances, and thus allows to check
 * for any pooled object leak. Should only be used for testing as it keeps a reference to all in-flight pooled objects.
 *
 * @param <T> pooled object type
 */
public class BookkeeperObjectPool<T> implements ObjectPool<T> {

    private static final Logger logger = LoggerFactory.getLogger(BookkeeperObjectPool.class);

    private final ObjectPool<T> pool;
    private final Set<T> toReturn = Collections.synchronizedSet(Collections.<T>newSetFromMap(new IdentityHashMap<T, Boolean>()));
    // An ever-increasing counter for how many objects where requested from the pool
    private AtomicInteger objectCounter = new AtomicInteger();

    /**
     * @param pool pool to wrap
     */
    public BookkeeperObjectPool(ObjectPool<T> pool) {
        this.pool = pool;
    }

    @Override
    public T createInstance() {
        T instance = pool.createInstance();
        toReturn.add(instance);
        objectCounter.incrementAndGet();
        logger.debug("creating pooled object: " + instance);
        return instance;
    }

    @Override
    public void recycle(T obj) {
        logger.debug("recycling pooled object: " + obj);

        boolean returned = toReturn.remove(obj);
        if (!returned) {
            throw new IllegalStateException("trying to recycle object that has not been taken from this pool or has already been returned " + obj);
        }
        pool.recycle(obj);
    }

    @Override
    public int getObjectsInPool() {
        return pool.getObjectsInPool();
    }

    @Override
    public long getGarbageCreated() {
        return pool.getGarbageCreated();
    }

    @Override
    public void clear() {
        pool.clear();
    }

    /**
     * @return objects that have been created by pool but haven't been returned yet
     */
    public Collection<T> getRecyclablesToReturn() {
        return toReturn;
    }

    /**
     * Returns the number of times an object has been requested from the pool since its creation.
     * The returned value cannot be used as any indication as to the number of objects actually allocated by the pool.
     *
     * @return number of times {@link ObjectPool#createInstance()} was called on this pool since its creation
     */
    public int getRequestedObjectCount() {
        return objectCounter.get();
    }

    public void reset() {
        objectCounter.set(0);
        toReturn.clear();
    }
}
