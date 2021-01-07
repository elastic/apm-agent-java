package co.elastic.apm.agent.sdk.weakmap;

import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentSet;

import static co.elastic.apm.agent.sdk.weakmap.NullCheck.isNullValue;

/**
 * {@link WeakConcurrentSet} implementation that prevents throwing {@link NullPointerException} and helps debugging if needed
 *
 * @param <V> value type
 */
public class NullSafeWeakConcurrentSet<V> extends WeakConcurrentSet<V> {

    public NullSafeWeakConcurrentSet(Cleaner cleaner) {
        super(cleaner);
    }

    @Override
    public boolean add(V value) {
        if(isNullValue(value)){
            return false;
        }
        return super.add(value);
    }

    @Override
    public boolean contains(V value) {
        if(isNullValue(value)){
            return false;
        }
        return super.contains(value);
    }

    @Override
    public boolean remove(V value) {
        if(isNullValue(value)){
            return false;
        }
        return super.remove(value);
    }
}
