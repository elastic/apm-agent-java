/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
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

import co.elastic.apm.objectpool.Recyclable;
import co.elastic.apm.objectpool.RecyclableObjectFactory;
import com.lmax.disruptor.EventFactory;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;

public class QueueBasedObjectPool<T extends Recyclable> extends AbstractObjectPool<T> implements Collection<T> {

    private final Queue<T> queue;

    /**
     * @param queue                   the underlying queue
     * @param preAllocate             when set to true, the recyclableObjectFactory will be used to create maxPooledElements objects
     *                                which are then stored in the queue
     * @param recyclableObjectFactory a factory method which is used to create new instances of the recyclable object. This factory is
     *                                used when there are no objects in the queue and to preallocate the queue.
     */
    public QueueBasedObjectPool(Queue<T> queue, boolean preAllocate, RecyclableObjectFactory<T> recyclableObjectFactory) {
        super(recyclableObjectFactory);
        this.queue = queue;
        if (preAllocate) {
            for (int i = 0; i < this.queue.size(); i++) {
                this.queue.offer(recyclableObjectFactory.createInstance());
            }
        }
    }

    @Nullable
    @Override
    public T tryCreateInstance() {
        return queue.poll();
    }

    @Override
    public void recycle(T obj) {
        obj.resetState();
        queue.offer(obj);
    }

    @Override
    public int getObjectsInPool() {
        // as the size of the ring buffer is an int, this can never overflow
        return queue.size();
    }

    @Override
    public void close() {
    }

    @Override
    public int getSize() {
        return queue.size();
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return queue.contains(o);
    }

    @Override
    public Iterator<T> iterator() {
        return queue.iterator();
    }

    @Override
    public Object[] toArray() {
        return queue.toArray();
    }

    @Override
    public <T1> T1[] toArray(T1[] a) {
        return queue.toArray(a);
    }

    @Override
    public boolean add(T t) {
        return queue.add(t);
    }

    @Override
    public boolean remove(Object o) {
        return queue.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return queue.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        return queue.addAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return queue.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return queue.retainAll(c);
    }

    @Override
    public void clear() {
        queue.clear();
    }

    private static class PooledObjectHolder<T> {
        @Nullable
        T value;

        public void set(T value) {
            this.value = value;
        }
    }

    private static class PooledObjectEventFactory<T> implements EventFactory<PooledObjectHolder<T>> {
        @Override
        public PooledObjectHolder<T> newInstance() {
            return new PooledObjectHolder<>();
        }
    }
}
