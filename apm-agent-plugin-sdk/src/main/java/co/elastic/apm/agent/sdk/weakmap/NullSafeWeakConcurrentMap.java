/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.sdk.weakmap;

import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * WeakConcurrentMap implementation that prevents throwing {@link NullPointerException} and helps debugging if needed
 *
 * @param <K> key type
 * @param <V> value type
 */
class NullSafeWeakConcurrentMap<K, V> extends WeakConcurrentMap<K, V> {

    private static final Logger logger = LoggerFactory.getLogger(NullSafeWeakConcurrentMap.class);

    NullSafeWeakConcurrentMap(boolean cleanerThread) {
        super(cleanerThread);
    }

    @Nullable
    @Override
    public V get(K key) {
        if (isNull(key)) {
            // overriden implementation silently adds entries from default value when there is none
            // in the case of 'null', we won't return the default value nor create a map entry with it.
            return null;
        }
        return super.get(key);
    }

    @Nullable
    @Override
    public V getIfPresent(K key) {
        if (isNull(key)) {
            return null;
        }
        return super.getIfPresent(key);
    }

    @Override
    public boolean containsKey(K key) {
        if (isNull(key)) {
            return false;
        }
        return super.containsKey(key);
    }

    @Nullable
    @Override
    public V put(K key, V value) {
        if (isNull(key) || isNull(value)) {
            return null;
        }
        return super.put(key, value);
    }

    @Nullable
    @Override
    public V putIfAbsent(K key, V value) {
        if (isNull(key) || isNull(value)) {
            return null;
        }
        return super.putIfAbsent(key, value);
    }

    @Nullable
    @Override
    public V putIfProbablyAbsent(K key, V value) {
        if (isNull(key) || isNull(value)) {
            return null;
        }
        return super.putIfProbablyAbsent(key, value);
    }

    @Nullable
    @Override
    public V remove(K key) {
        if (isNull(key)) {
            return null;
        }
        return super.remove(key);
    }

    /**
     * checks if key or value is {@literal null}
     *
     * @param v key or value
     * @return {@literal true} if key is non-null, {@literal false} if null
     */
    private static <T> boolean isNull(@Nullable T v) {
        if (null != v) {
            return false;
        }
        String msg = "trying to use null key or value";
        if (logger.isDebugEnabled()) {
            logger.debug(msg, new RuntimeException(msg));
        } else {
            logger.warn(msg);
        }
        return true;
    }
}
