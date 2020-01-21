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
package co.elastic.apm.agent.objectpool;

import co.elastic.apm.agent.objectpool.impl.AbstractObjectPool;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * No-Op object pool that does not perform any pooling and will always create new instances
 *
 * @param <T> pooled object type
 */
public class NoopObjectPool<T> extends AbstractObjectPool<T> {

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
}
