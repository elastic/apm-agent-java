package co.elastic.apm.agent.util;

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.sdk.weakmap.NullSafeWeakConcurrentMap;
import co.elastic.apm.agent.sdk.weakmap.WeakMapSupplier;
import com.blogspot.mydailyjava.weaklockfree.AbstractWeakConcurrentMap;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @param <K> key type
 * @param <V> context type
 */
public class ContextAwareConcurrentMap<K, V extends AbstractSpan<?>> extends ConcurrentHashMap<K, V> {

    public static <K, V extends AbstractSpan<?>> WeakConcurrentMap<K, V> createWeakMap() {
        ContextAwareConcurrentMap<AbstractWeakConcurrentMap.WeakKey<K>, V> map = new ContextAwareConcurrentMap<>();
        WeakConcurrentMap<K, V> result = new NullSafeWeakConcurrentMap<>(false, map);
        WeakMapSupplier.registerMap(result);
        return result;
    }

    @Nullable
    @Override
    public V remove(Object key) {
        V removed = super.remove(key);
        onRemove(removed);
        return removed;
    }

    @Override
    public V put(K key, V value) {
        V previous = super.put(key, value);
        onPut(previous, value);
        return previous;
    }

    @Override
    public V putIfAbsent(K key, V value) {
        V previous = super.putIfAbsent(key, value);
        onPut(previous, value);
        return previous;
    }

    @Override
    public void clear() {
        Iterator<V> entries = values().iterator();
        while (entries.hasNext()) {
            onRemove(entries.next());
        }
        super.clear();
    }

    private void onPut(@Nullable AbstractSpan<?> previous, AbstractSpan<?> value) {
        if (previous == null) {
            // new entry
            value.incrementReferences();
        } else if (previous != value) {
            // entry replaced
            value.incrementReferences();
            previous.decrementReferences();
        }
    }

    private void onRemove(@Nullable AbstractSpan<?> removed) {
        if (removed == null) {
            return;
        }
        removed.decrementReferences();
    }

}
