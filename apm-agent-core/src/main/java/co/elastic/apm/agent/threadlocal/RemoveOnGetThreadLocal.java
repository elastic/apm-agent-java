/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent.threadlocal;

import com.blogspot.mydailyjava.weaklockfree.DetachedThreadLocal;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class RemoveOnGetThreadLocal<T> extends DetachedThreadLocal<T> {

    private static final ConcurrentMap<String, RemoveOnGetThreadLocal<?>> registry = new ConcurrentHashMap<>();
    @Nullable
    private final T defaultValue;

    private RemoveOnGetThreadLocal(@Nullable T defaultValue) {
        super(Cleaner.INLINE);
        this.defaultValue = defaultValue;
    }

    public static <T> RemoveOnGetThreadLocal<T> get(Class<?> adviceClass, String key) {
        return get(adviceClass.getName() + "." + key, null);
    }

    public static <T> RemoveOnGetThreadLocal<T> get(Class<?> adviceClass, String key, @Nullable T defaultValue) {
        return get(adviceClass.getName() + "." + key, defaultValue);
    }

    private static <T> RemoveOnGetThreadLocal<T> get(String key, @Nullable T defaultValue) {
        RemoveOnGetThreadLocal<?> threadLocal = registry.get(key);
        if (threadLocal == null) {
            registry.putIfAbsent(key, new RemoveOnGetThreadLocal<T>(defaultValue));
            threadLocal = registry.get(key);
        }
        return (RemoveOnGetThreadLocal<T>) threadLocal;
    }

    @Nullable
    public T getAndRemove() {
        T value = get();
        if (value != null) {
            clear();
        }
        return value;
    }

    @Override
    @Nullable
    protected T initialValue(Thread thread) {
        return defaultValue;
    }

}
