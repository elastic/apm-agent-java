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
package co.elastic.apm.agent.weakconcurrent;

import java.lang.ref.WeakReference;


/**
 * Enables a custom implementation of {@code com.blogspot.mydailyjava.weaklockfree.AbstractWeakConcurrentMap}
 * that can safely use a {@link ThreadLocal}-cached lookup key, enabling garbage-free lookups.
 * <p>
 * This class will be loaded from a dedicated class loader which is the parent of the agent class loader.
 * While the agent class loader may be unloaded, the class loader of this class will not.
 * </p>
 */
public class CachedLookupKey<K> {

    /*
     * Creating a subclass and storing a custom class in a ThreadLocal is only safe because this class will be loaded from a dedicated class loader that can't be unloaded
     */
    static final ThreadLocal<CachedLookupKey<?>> LOOKUP_KEY_CACHE = new ThreadLocal<CachedLookupKey<?>>() {
        @Override
        protected CachedLookupKey<?> initialValue() {
            return new CachedLookupKey<Object>();
        }
    };

    private K key;
    private int hashCode;

    @SuppressWarnings("unchecked")
    public static <K> CachedLookupKey<K> get(K key) {
        CachedLookupKey<K> lookupKey = (CachedLookupKey<K>) LOOKUP_KEY_CACHE.get();
        return lookupKey.withValue(key);
    }

    private CachedLookupKey<K> withValue(K key) {
        this.key = key;
        hashCode = System.identityHashCode(key);
        return this;
    }

    public void reset() {
        key = null;
        hashCode = 0;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof CachedLookupKey<?>) {
            return ((CachedLookupKey<?>) other).key == key;
        } else if (other instanceof WeakReference<?>) {
            return ((WeakReference<?>) other).get() == key;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
