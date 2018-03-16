package co.elastic.apm.objectpool;

import javax.annotation.Nullable;
import java.io.Closeable;

public interface ObjectPool<T extends Recyclable> extends Closeable {
    @Nullable
    T tryCreateInstance();

    T createInstance();

    void fillFromOtherPool(ObjectPool<T> otherPool, int maxElements);

    void recycle(T obj);

    int getSize();

    int getObjectsInPool();

    long getGarbageCreated();
}
