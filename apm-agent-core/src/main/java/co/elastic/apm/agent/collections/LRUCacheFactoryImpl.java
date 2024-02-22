package co.elastic.apm.agent.collections;

import co.elastic.apm.agent.sdk.internal.collections.LRUCacheFactory;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;

import java.util.Map;

public class LRUCacheFactoryImpl implements LRUCacheFactory {
    @Override
    public <K, V> Map<K, V> createCache(int capacity) {
        return new ConcurrentLinkedHashMap.Builder<K,V>()
            .concurrencyLevel(Runtime.getRuntime().availableProcessors())
            .maximumWeightedCapacity(capacity)
            .build();
    }
}
