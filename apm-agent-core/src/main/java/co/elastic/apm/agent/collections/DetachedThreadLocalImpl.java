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

import co.elastic.apm.agent.sdk.weakmap.DetachedThreadLocal;
import co.elastic.apm.agent.sdk.weakmap.WeakMap;

import javax.annotation.Nullable;

public class DetachedThreadLocalImpl<T> implements DetachedThreadLocal<T> {

    private final WeakMap<Thread, T> map;

    DetachedThreadLocalImpl(WeakMap<Thread, T> map) {
        this.map = map;
    }

    @Nullable
    @Override
    public T get() {
        return map.get(Thread.currentThread());
    }

    @Nullable
    public T getAndRemove() {
        T value = get();
        if (value != null) {
            remove();
        }
        return value;
    }

    @Override
    public void set(@Nullable T value) {
        if (NullCheck.isNullKey(value)) {
            return;
        }
        map.put(Thread.currentThread(), value);
    }

    @Override
    public void remove() {
        map.remove(Thread.currentThread());
    }

}
