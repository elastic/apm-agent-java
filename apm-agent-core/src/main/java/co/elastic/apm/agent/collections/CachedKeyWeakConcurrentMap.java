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
package co.elastic.apm.agent.collections;

import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import co.elastic.apm.agent.weakconcurrent.CachedLookupKey;
import com.blogspot.mydailyjava.weaklockfree.AbstractWeakConcurrentMap;

import java.util.concurrent.ConcurrentMap;

public class CachedKeyWeakConcurrentMap<K, V> extends AbstractWeakConcurrentMap<K, V, CachedLookupKey<K>> implements WeakMap<K, V> {

    CachedKeyWeakConcurrentMap(ConcurrentMap<WeakKey<K>, V> target) {
        super(target);
    }

    @Override
    protected CachedLookupKey<K> getLookupKey(K key) {
        return CachedLookupKey.get(key);
    }

    @Override
    protected void resetLookupKey(CachedLookupKey<K> lookupKey) {
        lookupKey.reset();
    }
}
