/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.objectpool.impl;

import co.elastic.apm.objectpool.ObjectPool;
import co.elastic.apm.objectpool.Recyclable;
import co.elastic.apm.objectpool.RecyclableObjectFactory;

import javax.annotation.Nullable;
import java.io.IOException;

public class MixedObjectPool<T extends Recyclable> extends AbstractObjectPool<T> {

    private final ObjectPool<T> primaryPool;
    private final ObjectPool<T> secondaryPool;

    public MixedObjectPool(final RecyclableObjectFactory<T> recyclableObjectFactory, ObjectPool<T> primaryPool, ObjectPool<T> secondaryPool) {
        super(recyclableObjectFactory);
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
    public void recycle(T obj) {
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
