package co.elastic.apm.agent.sdk.internal.pooling;

public interface ObjectPool<T> {

    T createInstance();

}
