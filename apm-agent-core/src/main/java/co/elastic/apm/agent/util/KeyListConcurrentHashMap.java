package co.elastic.apm.agent.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A subclass of {@link ConcurrentHashMap} which maintains a {@link List} of all the keys in the map.
 * It can be used to iterate over the map's keys without allocating an {@link java.util.Iterator}
 */
public class KeyListConcurrentHashMap<K, V> extends ConcurrentHashMap<K, V> {
    private final List<K> keyList = Collections.synchronizedList(new ArrayList<>());

    @Override
    public V put(K key, V value) {
        final V previousValue = super.put(key, value);
        if (previousValue == null) {
            keyList.add(key);
        }
        return previousValue;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Entry<? extends K, ? extends V> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public V remove(Object key) {
        keyList.remove(key);
        return super.remove(key);
    }

    @Override
    public void clear() {
        keyList.clear();
        super.clear();
    }

    @Override
    public V putIfAbsent(K key, V value) {
        final V previousValue = super.putIfAbsent(key, value);
        if (previousValue == null) {
            keyList.add(key);
        }
        return previousValue;
    }

    @Override
    public boolean remove(Object key, Object value) {
        final boolean remove = super.remove(key, value);
        if (remove) {
            keyList.remove(key);
        }
        return remove;
    }

    /**
     * Returns a mutable {@link List}, roughly equal to the {@link #keySet()}.
     * <p>
     * Note that in concurrent scenarios, the key list may be a subset of the values of the respective {@link #keySet()}.
     * Entries added via the {@code compute*} family of methods are not reflected in the list.
     * </p>
     * <p>
     * Do not modify this list.
     * </p>
     *
     * @return a {@link List}, roughly equal to the {@link #keySet()}
     */
    public List<K> keyList() {
        return keyList;
    }
}
