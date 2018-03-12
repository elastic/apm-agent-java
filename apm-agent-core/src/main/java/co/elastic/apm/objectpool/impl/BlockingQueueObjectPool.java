package co.elastic.apm.objectpool.impl;

import co.elastic.apm.objectpool.Recyclable;
import co.elastic.apm.objectpool.RecyclableObjectFactory;
import com.lmax.disruptor.EventFactory;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class BlockingQueueObjectPool<T extends Recyclable> extends AbstractObjectPool<T> implements Collection<T> {

    private final BlockingQueue<T> queue;

    /**
     * @param maxPooledElements       the size of the underlying queue
     * @param preAllocate             when set to true, the recyclableObjectFactory will be used to create maxPooledElements objects
     *                                which are then stored in the queue
     * @param recyclableObjectFactory a factory method which is used to create new instances of the recyclable object. This factory is
     *                                used when there are no objects in the queue and to preallocate the queue.
     */
    public BlockingQueueObjectPool(int maxPooledElements, boolean preAllocate, RecyclableObjectFactory<T> recyclableObjectFactory) {
        super(recyclableObjectFactory);
        this.queue = new ArrayBlockingQueue<>(maxPooledElements);
        if (preAllocate) {
            for (int i = 0; i < maxPooledElements; i++) {
                queue.offer(recyclableObjectFactory.createInstance());
            }
        }
    }

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
