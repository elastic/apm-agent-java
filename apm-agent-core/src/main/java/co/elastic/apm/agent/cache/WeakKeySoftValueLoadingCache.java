/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.agent.cache;

import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.ref.SoftReference;

/**
 * This cache is useful in situations when hard referencing the cache key and the value would otherwise lead to memory or class loader leaks.
 * <p>
 * Use use-case is storing a value,
 * like a {@link Class} or {@link java.lang.invoke.MethodHandle} which is specific to a certain {@link ClassLoader}.
 * Holding a strong reference to any of these from the agent code would lead to classloader leaks.
 * That's why the key is wrapped in a {@link java.lang.ref.WeakReference} and the value is wrapped in a {@link SoftReference}.
 * </p>
 * <p>
 * An otherwise not referenced {@link ClassLoader} might still not be collected for a longer time as usual
 * if it is indirectly referenced by the value which is wrapped in a {@link SoftReference}.
 * But there is no danger of OOMEs, as the softly referenced value will be collected when memory gets scarce
 * or if it has not been accessed for some time.
 * </p>
 * <p>
 * There can also be the situation that a value is collected although it is actually still used.
 * In that case, the value is re-loaded transparently.
 * For frequently accessed values that should not be the case,
 * as a {@link SoftReference} works a bit like a LRU cache
 * (see http://xmlandmore.blogspot.com/2014/12/jdk-8-when-softly-referenced-objects.html).
 * </p>
 * <p>
 * If the cache key has been collected,
 * the underlying {@link WeakMap} makes sure that the map entry is deleted.
 * </p>
 *
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public class WeakKeySoftValueLoadingCache<K, V> {

    private static final Logger logger = LoggerFactory.getLogger(WeakKeySoftValueLoadingCache.class);

    private final WeakMap<K, CacheValue<K, V>> cache = WeakConcurrent.buildMap();
    private final ValueSupplier<K, V> valueSupplier;

    public WeakKeySoftValueLoadingCache(ValueSupplier<K, V> valueSupplier) {
        this.valueSupplier = valueSupplier;
    }

    @Nullable
    public V get(K key) {
        final CacheValue<K, V> cacheValue = cache.get(key);
        if (cacheValue != null) {
            return cacheValue.get(key);
        } else {
            CacheValue<K, V> value = new CacheValue<>(key, valueSupplier);
            cache.put(key, value);
            return value.get(key);
        }
    }

    /**
     * Supplies a value, given a key.
     *
     * @param <K> the key type
     * @param <V> the value type
     */
    public interface ValueSupplier<K, V> {
        /**
         * This supplier is expected to return the same or equal-and-stateless object if called multiple times for the same key,
         * for thread safety.
         *
         * @param key the cache key
         * @return the value for the provided cache key
         */
        @Nullable
        V get(K key);
    }

    /**
     * A wrapper object for a cached value.
     * <p>
     * This is needed as a {@link java.util.concurrent.ConcurrentMap} does not allow values to be null,
     * but we want to be able to cache null values.
     * Also, this the cache value wrapps the value into a {@link SoftReference} and re-loads the value in case it has been collected.
     * </p>
     *
     * @param <K> the key type
     * @param <V> the value type
     */
    private static class CacheValue<K, V> {

        private final ValueSupplier<K, V> valueSupplier;
        @Nullable
        private SoftReference<V> valueReference;

        private CacheValue(K key, ValueSupplier<K, V> valueSupplier) {
            this.valueSupplier = valueSupplier;
            V value = valueSupplier.get(key);
            if (value != null) {
                valueReference = new SoftReference<>(value);
            }
        }

        @Nullable
        public V get(K key) {
            if (valueReference == null) {
                return null;
            }
            V value = this.valueReference.get();
            if (value != null) {
                return value;
            } else {
                logger.info("The value for the key {} has been collected, reloading it", key);
                // the value has been collected, so re-load it
                value = valueSupplier.get(key);
                // this is thread safe but the value might be created in multiple threads
                // the assumption is that this is preferable to the alternative which would involve volatile reads for each get,
                // even if the value has not been collected
                valueReference = new SoftReference<>(value);
                return value;
            }
        }
    }
}
