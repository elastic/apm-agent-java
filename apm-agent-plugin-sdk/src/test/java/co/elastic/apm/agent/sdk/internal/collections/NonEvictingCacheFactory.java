package co.elastic.apm.agent.sdk.internal.collections;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NonEvictingCacheFactory implements LRUCacheFactory{
    @Override
    public <K, V> Map<K, V> createCache(int capacity) {
        return new ConcurrentHashMap<>();
    }
}
