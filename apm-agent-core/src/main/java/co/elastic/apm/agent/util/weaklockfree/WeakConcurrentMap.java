/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.util.weaklockfree;

import javax.annotation.Nullable;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <p>
 * A thread-safe map with weak keys. Entries are based on a key's system hash code and keys are considered
 * equal only by reference equality.
 * </p>
 * This class does not implement the {@link java.util.Map} interface because this implementation is incompatible
 * with the map contract. While iterating over a map's entries, any key that has not passed iteration is referenced non-weakly.
 * <p>
 * This implementation is based on com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap,
 * under Apache License 2.0.
 * This fork recycles the {@link LatentKey} so that lookups are garbage free.
 * As {@link ThreadLocal}s can lead to classloader leaks in some scenarios,
 * the PR https://github.com/raphw/weak-lock-free/pull/5 was not accepted.
 * </p>
 */
public class WeakConcurrentMap<K, V> extends ReferenceQueue<K> implements Runnable, Iterable<Map.Entry<K, V>> {

    private static final AtomicLong ID = new AtomicLong();

    final ConcurrentMap<WeakKey<K>, V> target;

    @Nullable
    private final Thread thread;

    /**
     * Latent keys are cached thread-locally to avoid allocations on lookups.
     * This is beneficial as the JIT unfortunately can't reliably replace the LatentKey allocation with stack allocations,
     * even though the LatentKey does not escape.
     */
    private static final ThreadLocal<LatentKey<?>> latentKeyThreadLocal = new ThreadLocal<LatentKey<?>>();

    /**
     * @param cleanerThread {@code true} if a thread should be started that removes stale entries.
     */
    public WeakConcurrentMap(boolean cleanerThread) {
        target = new ConcurrentHashMap<WeakKey<K>, V>();
        if (cleanerThread) {
            thread = new Thread(this);
            thread.setName("weak-ref-cleaner-" + ID.getAndIncrement());
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.setDaemon(true);
            thread.start();
        } else {
            thread = null;
        }
    }

    /**
     * @param key The key of the entry.
     * @return The value of the entry or the default value if it did not exist.
     */
    @Nullable
    public V get(K key) {
        V value;
        try (final LatentKey<K> latentKey = getKey(key)) {
            value = target.get(latentKey);
        }
        if (value == null) {
            value = defaultValue(key);
            if (value != null) {
                V previousValue = target.putIfAbsent(new WeakKey<K>(key, this), value);
                if (previousValue != null) {
                    value = previousValue;
                }
            }
        }
        return value;
    }

    private LatentKey<K> getKey(K key) {
        LatentKey<K> latentKey = (LatentKey<K>) latentKeyThreadLocal.get();
        if (latentKey != null) {
            return latentKey.set(key);
        } else {
            latentKey = new LatentKey<K>().set(key);
            latentKeyThreadLocal.set(latentKey);
            return latentKey;
        }
    }

    /**
     * @param key The key of the entry.
     * @return The value of the entry or null if it did not exist.
     */
    @Nullable
    public V getIfPresent(K key) {
        try (final LatentKey<K> latentKey = getKey(key)) {
            return target.get(latentKey);
        }
    }

    /**
     * @param key The key of the entry.
     * @return {@code true} if the key already defines a value.
     */
    public boolean containsKey(K key) {
        try (final LatentKey<K> latentKey = getKey(key)) {
            return target.containsKey(latentKey);
        }
    }

    /**
     * @param key   The key of the entry.
     * @param value The value of the entry.
     * @return The previous entry or {@code null} if it does not exist.
     */
    @Nullable
    public V put(K key, V value) {
        return target.put(new WeakKey<K>(key, this), value);
    }

    /**
     * @param key   The key of the entry.
     * @param value The value of the entry.
     * @return The previous entry or {@code null} if it does not exist.
     */
    @Nullable
    public V putIfAbsent(K key, V value) {
        V previous;
        try (final LatentKey<K> latentKey = getKey(key)) {
            previous = target.get(latentKey);
        }
        return previous == null ? target.putIfAbsent(new WeakKey<K>(key, this), value) : previous;
    }

    /**
     * @param key   The key of the entry.
     * @param value The value of the entry.
     * @return The previous entry or {@code null} if it does not exist.
     */
    @Nullable
    public V putIfProbablyAbsent(K key, V value) {
        return target.putIfAbsent(new WeakKey<K>(key, this), value);
    }

    /**
     * @param key The key of the entry.
     * @return The removed entry or {@code null} if it does not exist.
     */
    @Nullable
    public V remove(K key) {
        try (final LatentKey<K> latentKey = getKey(key)) {
            return target.remove(latentKey);
        }
    }

    /**
     * Clears the entire map.
     */
    public void clear() {
        target.clear();
    }

    /**
     * Creates a default value. There is no guarantee that the requested value will be set as a once it is created
     * in case that another thread requests a value for a key concurrently.
     *
     * @param key The key for which to create a default value.
     * @return The default value for a key without value or {@code null} for not defining a default value.
     */
    @Nullable
    protected V defaultValue(K key) {
        return null;
    }

    /**
     * @return The cleaner thread or {@code null} if no such thread was set.
     */
    @Nullable
    public Thread getCleanerThread() {
        return thread;
    }

    /**
     * Cleans all unused references.
     */
    public void expungeStaleEntries() {
        Reference<?> reference;
        while ((reference = poll()) != null) {
            target.remove(reference);
        }
    }

    /**
     * Returns the approximate size of this map where the returned number is at least as big as the actual number of entries.
     *
     * @return The minimum size of this map.
     */
    public int approximateSize() {
        return target.size();
    }

    @Override
    public void run() {
        try {
            while (true) {
                target.remove(remove());
            }
        } catch (InterruptedException ignored) {
            clear();
        }
    }

    @Override
    public Iterator<Map.Entry<K, V>> iterator() {
        return new EntryIterator(target.entrySet().iterator());
    }

    /*
     * Why this works:
     * ---------------
     *
     * Note that this map only supports reference equality for keys and uses system hash codes. Also, for the
     * WeakKey instances to function correctly, we are voluntarily breaking the Java API contract for
     * hashCode/equals of these instances.
     *
     *
     * System hash codes are immutable and can therefore be computed prematurely and are stored explicitly
     * within the WeakKey instances. This way, we always know the correct hash code of a key and always
     * end up in the correct bucket of our target map. This remains true even after the weakly referenced
     * key is collected.
     *
     * If we are looking up the value of the current key via WeakConcurrentMap::get or any other public
     * API method, we know that any value associated with this key must still be in the map as the mere
     * existence of this key makes it ineligible for garbage collection. Therefore, looking up a value
     * using another WeakKey wrapper guarantees a correct result.
     *
     * If we are looking up the map entry of a WeakKey after polling it from the reference queue, we know
     * that the actual key was already collected and calling WeakKey::get returns null for both the polled
     * instance and the instance within the map. Since we explicitly stored the identity hash code for the
     * referenced value, it is however trivial to identify the correct bucket. From this bucket, the first
     * weak key with a null reference is removed. Due to hash collision, we do not know if this entry
     * represents the weak key. However, we do know that the reference queue polls at least as many weak
     * keys as there are stale map entries within the target map. If no key is ever removed from the map
     * explicitly, the reference queue eventually polls exactly as many weak keys as there are stale entries.
     *
     * Therefore, we can guarantee that there is no memory leak.
     */

    private static class WeakKey<T> extends WeakReference<T> {

        private final int hashCode;

        WeakKey(T key, ReferenceQueue<? super T> queue) {
            super(key, queue);
            hashCode = System.identityHashCode(key);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof LatentKey<?>) {
                return ((LatentKey<?>) other).key == get();
            } else {
                return ((WeakKey<?>) other).get() == get();
            }
        }
    }

    /*
     * A latent key must only be used for looking up instances within a map. For this to work, it implements an identical contract for
     * hash code and equals as the WeakKey implementation. At the same time, the latent key implementation does not extend WeakReference
     * and avoids the overhead that a weak reference implies.
     */
    private static class LatentKey<T> implements AutoCloseable {

        T key;

        private int hashCode;

        LatentKey<T> set(T key) {
            this.key = key;
            hashCode = System.identityHashCode(key);
            return this;
        }

        /**
         * Failing to reset a latent key can lead to memory leaks as the key is strongly referenced.
         */
        @Override
        public void close() {
            key = null;
            hashCode = 0;
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof LatentKey<?>) {
                return ((LatentKey<?>) other).key == key;
            } else {
                return ((WeakKey<?>) other).get() == key;
            }
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    /**
     * A {@link WeakConcurrentMap} where stale entries are removed as a side effect of interacting with this map.
     */
    public static class WithInlinedExpunction<K, V> extends WeakConcurrentMap<K, V> {

        public WithInlinedExpunction() {
            super(false);
        }

        @Override
        public V get(K key) {
            expungeStaleEntries();
            return super.get(key);
        }

        @Override
        public boolean containsKey(K key) {
            expungeStaleEntries();
            return super.containsKey(key);
        }

        @Override
        public V put(K key, V value) {
            expungeStaleEntries();
            return super.put(key, value);
        }

        @Override
        public V remove(K key) {
            expungeStaleEntries();
            return super.remove(key);
        }

        @Override
        public Iterator<Map.Entry<K, V>> iterator() {
            expungeStaleEntries();
            return super.iterator();
        }

        @Override
        public int approximateSize() {
            expungeStaleEntries();
            return super.approximateSize();
        }
    }

    private class EntryIterator implements Iterator<Map.Entry<K, V>> {

        private final Iterator<Map.Entry<WeakKey<K>, V>> iterator;

        private Map.Entry<WeakKey<K>, V> nextEntry;

        private K nextKey;

        private EntryIterator(Iterator<Map.Entry<WeakKey<K>, V>> iterator) {
            this.iterator = iterator;
            findNext();
        }

        private void findNext() {
            while (iterator.hasNext()) {
                nextEntry = iterator.next();
                nextKey = nextEntry.getKey().get();
                if (nextKey != null) {
                    return;
                }
            }
            nextEntry = null;
            nextKey = null;
        }

        @Override
        public boolean hasNext() {
            return nextKey != null;
        }

        @Override
        public Map.Entry<K, V> next() {
            if (nextKey == null) {
                throw new NoSuchElementException();
            }
            try {
                return new SimpleEntry(nextKey, nextEntry);
            } finally {
                findNext();
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private class SimpleEntry implements Map.Entry<K, V> {

        private final K key;

        final Map.Entry<WeakKey<K>, V> entry;

        private SimpleEntry(K key, Map.Entry<WeakKey<K>, V> entry) {
            this.key = key;
            this.entry = entry;
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return entry.getValue();
        }

        @Override
        public V setValue(V value) {
            if (value == null) throw new NullPointerException();
            return entry.setValue(value);
        }
    }
}
