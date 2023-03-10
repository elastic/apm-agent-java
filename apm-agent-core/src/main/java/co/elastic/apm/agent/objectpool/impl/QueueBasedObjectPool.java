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
import java.util.Queue;

public class QueueBasedObjectPool<T> extends AbstractObjectPool<T> {

    private final Queue<T> queue;

    /**
     * Creates a queue based pooled for types that implement {@link Recyclable}, use {@link #of(Queue, boolean, Allocator, Resetter)}
     * for other pooled object types.
     *
     * @param queue       the underlying queue
     * @param preAllocate when set to true, queue will be be pre-allocated with object instance.
     * @param allocator   a factory used to create new instances of the recyclable object. This factory is used when
     *                    there are no objects in the queue and to preallocate the queue
     */
    public static <T extends Recyclable> QueueBasedObjectPool<T> ofRecyclable(Queue<T> queue, boolean preAllocate, Allocator<T> allocator) {
        return new QueueBasedObjectPool<>(queue, preAllocate, allocator, Resetter.ForRecyclable.<T>get());
    }

    /**
     * Creates a queue based pooled for types that do not implement {@link Recyclable}, use {@link #ofRecyclable(Queue, boolean, Allocator)}
     * for types that implement {@link Recyclable}.
     *
     * @param queue       the underlying queue
     * @param preAllocate when set to true, queue will be be pre-allocated with object instances fitting queue size
     * @param allocator   a factory used to create new instances of the recyclable object. This factory is used when
     *                    there are no objects in the queue and to preallocate the queue
     * @param resetter    a reset strategy class
     */
    public static <T> QueueBasedObjectPool<T> of(Queue<T> queue, boolean preAllocate, Allocator<T> allocator, Resetter<T> resetter) {
        return new QueueBasedObjectPool<>(queue, preAllocate, allocator, resetter);
    }

    private QueueBasedObjectPool(Queue<T> queue, boolean preAllocate, Allocator<T> allocator, Resetter<T> resetter) {
        super(allocator, resetter);
        this.queue = queue;
        if (preAllocate) {
            boolean addMore;
            do {
                addMore = queue.offer(allocator.createInstance());
            } while (addMore);
        }
    }

    @Nullable
    @Override
    public T tryCreateInstance() {
        return queue.poll();
    }

    @Override
    protected boolean returnToPool(T obj) {
        return queue.offer(obj);
    }

    @Override
    public int getObjectsInPool() {
        // as the size of the ring buffer is an int, this can never overflow
        return queue.size();
    }

    @Override
    public void clear() {
        queue.clear();
    }

}
