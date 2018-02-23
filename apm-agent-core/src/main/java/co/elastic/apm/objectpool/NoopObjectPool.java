package co.elastic.apm.objectpool;

import java.io.IOException;

public class NoopObjectPool<T extends Recyclable> implements ObjectPool<T> {

    private final RecyclableObjectFactory<T> recyclableObjectFactory;

    public NoopObjectPool(RecyclableObjectFactory<T> recyclableObjectFactory) {
        this.recyclableObjectFactory = recyclableObjectFactory;
    }

    @Override
    public T tryCreateInstance() {
        return recyclableObjectFactory.createInstance();
    }

    @Override
    public T createInstance() {
        return recyclableObjectFactory.createInstance();
    }

    @Override
    public void fillFromOtherPool(ObjectPool<T> otherPool, int maxElements) {

    }

    @Override
    public void recycle(T obj) {

    }

    @Override
    public int getSize() {
        return 0;
    }

    @Override
    public int getObjectsInPool() {
        return 0;
    }

    @Override
    public long getGarbageCreated() {
        return 0;
    }

    @Override
    public void close() throws IOException {
    }
}
