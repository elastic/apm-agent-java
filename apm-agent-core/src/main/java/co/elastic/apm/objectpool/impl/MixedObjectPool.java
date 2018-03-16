package co.elastic.apm.objectpool.impl;

import co.elastic.apm.objectpool.ObjectPool;
import co.elastic.apm.objectpool.Recyclable;
import co.elastic.apm.objectpool.RecyclableObjectFactory;

import javax.annotation.Nullable;
import java.io.IOException;

public class MixedObjectPool<T extends Recyclable> extends AbstractObjectPool<T> {

    private final ObjectPool<T> primaryPool;
    private final ObjectPool<T> secondaryPool;

    public MixedObjectPool(final int primaryPoolSize, final int secondaryPoolSize, final boolean preAllocate,
                           final RecyclableObjectFactory<T> recyclableObjectFactory) {
        super(recyclableObjectFactory);
        primaryPool = new ThreadLocalObjectPool<>(primaryPoolSize, preAllocate, recyclableObjectFactory);
        secondaryPool = new RingBufferObjectPool<>(secondaryPoolSize, preAllocate, recyclableObjectFactory);
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
