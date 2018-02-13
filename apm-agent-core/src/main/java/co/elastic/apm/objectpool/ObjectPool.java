package co.elastic.apm.objectpool;

import java.io.Closeable;

public interface ObjectPool<T extends Recyclable> extends Closeable {
    T createInstance();

    void recycle(T obj);

    int getObjectPoolSize();
}
