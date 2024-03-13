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

import javax.annotation.Nullable;

public class ObjectHandleImpl<T> implements co.elastic.apm.agent.tracer.pooling.ObjectHandle<T>, co.elastic.apm.agent.sdk.internal.pooling.ObjectHandle<T> {

    public static class Allocator<T> implements co.elastic.apm.agent.tracer.pooling.Allocator<ObjectHandleImpl<T>> {

        @Nullable
        private ObservableObjectPool<ObjectHandleImpl<T>> pool;

        private final co.elastic.apm.agent.tracer.pooling.Allocator<T> delegate;

        public Allocator(co.elastic.apm.agent.tracer.pooling.Allocator<T> delegate) {
            this.delegate = delegate;
        }

        public void setPool(@Nullable ObservableObjectPool<ObjectHandleImpl<T>> pool) {
            this.pool = pool;
        }

        @Override
        public ObjectHandleImpl<T> createInstance() {
            if (pool == null) {
                throw new IllegalStateException("Pool has not been initialized!");
            }
            return new ObjectHandleImpl<>(pool, delegate.createInstance());
        }
    }

    public static final Resetter<ObjectHandleImpl<?>> NOOP_RESETTER = new Resetter<ObjectHandleImpl<?>>() {
        @Override
        public void recycle(ObjectHandleImpl<?> object) {
        }
    };

    private final ObservableObjectPool<ObjectHandleImpl<T>> owner;
    private final T pooledObject;

    public ObjectHandleImpl(ObservableObjectPool<ObjectHandleImpl<T>> owner, T pooledObject) {
        this.owner = owner;
        this.pooledObject = pooledObject;
    }

    @Override
    public T get() {
        return pooledObject;
    }

    @Override
    public void close() {
        owner.recycle(this);
    }
}
