package co.elastic.apm.agent.sdk.internal.collections;

import java.util.Map;

public interface LRUCacheFactory {

    <K,V> Map<K,V> createCache(int capacity);
}
