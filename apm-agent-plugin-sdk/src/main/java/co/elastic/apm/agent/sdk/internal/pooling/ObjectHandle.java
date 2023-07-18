package co.elastic.apm.agent.sdk.internal.pooling;

public interface ObjectHandle<T> extends AutoCloseable {

    T get();

    @Override
    void close();
}
