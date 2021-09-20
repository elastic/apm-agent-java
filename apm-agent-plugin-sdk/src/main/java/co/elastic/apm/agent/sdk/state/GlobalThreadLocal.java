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
package co.elastic.apm.agent.sdk.state;

import co.elastic.apm.agent.sdk.weakmap.NullCheck;
import com.blogspot.mydailyjava.weaklockfree.DetachedThreadLocal;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Allows registering a globally shared instance of a {@link DetachedThreadLocal} that optionally allows for removal on get.
 * Similar to {@link GlobalVariables} and {@link GlobalState},
 * this allows to get thread locals whose state is shared across plugin class loaders.
 *
 * @param <T>
 */
public class GlobalThreadLocal<T> extends DetachedThreadLocal<T> {

    private static final ConcurrentMap<String, GlobalThreadLocal<?>> registry = new ConcurrentHashMap<>();

    private final DefaultValueSupplier<T> defaultValueSupplier;

    private GlobalThreadLocal(@Nullable DefaultValueSupplier<T> defaultValueSupplier) {
        super(Cleaner.INLINE);
        this.defaultValueSupplier = defaultValueSupplier != null ? defaultValueSupplier : new NullValueSupplier();
    }

    public static <T> GlobalThreadLocal<T> get(Class<?> adviceClass, String key) {
        return get(adviceClass.getName() + "." + key, null);
    }

    public static <T> GlobalThreadLocal<T> get(Class<?> adviceClass, String key, @Nullable DefaultValueSupplier<T> defaultValueSupplier) {
        return get(adviceClass.getName() + "." + key, defaultValueSupplier);
    }

    static <T> GlobalThreadLocal<T> get(String key, @Nullable DefaultValueSupplier<T> defaultValueSupplier) {
        GlobalThreadLocal<?> threadLocal = registry.get(key);
        if (threadLocal == null) {
            registry.putIfAbsent(key, new GlobalThreadLocal<T>(defaultValueSupplier));
            threadLocal = registry.get(key);
        }
        return (GlobalThreadLocal<T>) threadLocal;
    }

    @Nullable
    public T getAndRemove() {
        T value = get();
        if (value != null) {
            clear();
        }
        return value;
    }

    public T get(T defaultValue) {
        T value = get();
        if (value != null) {
            return value;
        }
        return defaultValue;
    }

    @Override
    public void set(@Nullable T value) {
        if (NullCheck.isNullKey(value)) {
            return;
        }
        super.set(value);
    }

    @Override
    @Nullable
    protected T initialValue(Thread thread) {
        return defaultValueSupplier.getDefaultValueForThread();
    }

    public interface DefaultValueSupplier<T> {
        @Nullable
        T getDefaultValueForThread();
    }

    private class NullValueSupplier implements DefaultValueSupplier<T> {
        @Nullable
        @Override
        public T getDefaultValueForThread() {
            return null;
        }
    }
}
