package co.elastic.apm.agent.tracer.reference;

import javax.annotation.Nullable;

public interface ReferenceCounter<K, V extends ReferenceCounted> {

    @Nullable
    V get(K key);

    boolean contains(K key);

    void put(K key, V value);

    @Nullable
    V remove(K key);
}
