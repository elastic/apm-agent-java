package co.elastic.apm.agent.collections;

import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import co.elastic.apm.agent.tracer.reference.ReferenceCounted;
import co.elastic.apm.agent.tracer.reference.ReferenceCounter;

import javax.annotation.Nullable;

public class WeakMapReferenceCounter<K, V extends ReferenceCounted> implements ReferenceCounter<K, V> {

    private final WeakMap<K, V> map;

    public WeakMapReferenceCounter(WeakMap<K, V> map) {
        this.map = map;
    }

    @Override
    @Nullable
    public V get(K key) {
        return map.get(key);
    }

    @Override
    public boolean contains(K key) {
        return map.containsKey(key);
    }

    @Override
    public void put(K key, V value) {
        map.put(key, value);
    }

    @Override
    @Nullable
    public V remove(K key) {
        return map.remove(key);
    }
}
