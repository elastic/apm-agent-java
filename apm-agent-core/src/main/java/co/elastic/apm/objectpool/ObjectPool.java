package co.elastic.apm.objectpool;

import java.io.Closeable;
import java.util.Collection;

public interface ObjectPool<T extends Recyclable> extends Closeable {
    T tryCreateInstance();

    T createInstance();

    void fillFromOtherPool(ObjectPool<T> otherPool, int maxElements);

    void recycle(T obj);

    int getSize();

    int getObjectsInPool();

    long getGarbageCreated();
}
