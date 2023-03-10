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
package co.elastic.apm.agent.objectpool;

import co.elastic.apm.agent.objectpool.impl.AbstractObjectPool;
import co.elastic.apm.agent.tracer.pooling.Allocator;
import co.elastic.apm.agent.tracer.pooling.Recyclable;

import javax.annotation.Nullable;

/**
 * No-Op object pool that does not perform any pooling and will always create new instances
 *
 * @param <T> pooled object type
 */
public class NoopObjectPool<T> extends AbstractObjectPool<T> {

    public static <T extends Recyclable> NoopObjectPool<T> ofRecyclable(Allocator<T> allocator) {
        return new NoopObjectPool<>(allocator, Resetter.ForRecyclable.get());
    }

    public NoopObjectPool(Allocator<T> allocator, Resetter<T> resetter) {
        super(allocator, resetter);
    }

    @Nullable
    @Override
    public T tryCreateInstance() {
        // will never try to reuse any instance, thus return null makes it create all the time
        return null;
    }

    @Override
    protected boolean returnToPool(T obj) {
        return false;
    }

    @Override
    public int getObjectsInPool() {
        return 0;
    }

    @Override
    public void clear() {
    }
}
