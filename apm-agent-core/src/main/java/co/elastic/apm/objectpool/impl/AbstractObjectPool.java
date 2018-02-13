package co.elastic.apm.objectpool.impl;

import co.elastic.apm.objectpool.ObjectPool;
import co.elastic.apm.objectpool.Recyclable;
import co.elastic.apm.objectpool.RecyclableObjectFactory;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractObjectPool<T extends Recyclable> implements ObjectPool<T> {

    private final RecyclableObjectFactory<T> recyclableObjectFactory;
    private final AtomicInteger garbageCreated = new AtomicInteger();

    protected AbstractObjectPool(RecyclableObjectFactory<T> recyclableObjectFactory) {
        this.recyclableObjectFactory = recyclableObjectFactory;
    }

    @Override
    public T createInstance() {
        T recyclable = tryCreateInstance();
        if (recyclable == null) {
            // queue is empty, falling back to creating a new instance
            garbageCreated.incrementAndGet();
            return recyclableObjectFactory.createInstance();
        } else {
            return recyclable;
        }
    }

    @Override
    public void fillFromOtherPool(ObjectPool<T> otherPool, int maxElements) {
        for (int i = 0; i < maxElements; i++) {
            T obj = createInstance();
            if (obj == null) {
                return;
            }
            otherPool.recycle(obj);
        }
    }

    @Override
    public long getGarbageCreated() {
        return garbageCreated.longValue();
    }


}
