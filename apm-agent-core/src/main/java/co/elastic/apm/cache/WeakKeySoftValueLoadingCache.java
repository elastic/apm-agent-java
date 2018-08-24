/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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
package co.elastic.apm.cache;

import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;

import javax.annotation.Nullable;
import java.lang.ref.SoftReference;

public class WeakKeySoftValueLoadingCache<K, V> {

    private final WeakConcurrentMap<K, CacheValue<K, V>> cache = new WeakConcurrentMap.WithInlinedExpunction<>();
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

    public interface ValueSupplier<K, V> {
        @Nullable
        V get(K key);
    }

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
