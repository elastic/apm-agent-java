package co.elastic.apm.agent.sdk.internal.collections;

import co.elastic.apm.agent.sdk.internal.InternalUtil;
import co.elastic.apm.agent.sdk.internal.pooling.ObjectPooling;

import java.util.Map;

public class LRUCache {

    private static final LRUCacheFactory factory;

    static {
        factory = InternalUtil.getServiceProvider(LRUCacheFactory.class);
    }


    /**
     * Creates a bounded LRU-cache. Keys and values are strongly referenced.
     * The returned map is guaranteed to be thread-safe.
     *
     * @param capacity the capacity of the cache
     */
    public static <K,V> Map<K,V> createCache(int capacity) {
        return factory.createCache(capacity);
    }
}
