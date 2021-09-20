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
package co.elastic.apm.agent.sdk.weakmap;

import co.elastic.apm.agent.sdk.state.GlobalState;
import co.elastic.apm.agent.sdk.state.GlobalVariables;

import javax.annotation.Nullable;
import java.util.ServiceLoader;

public final class WeakMaps {

    private static final WeakMapSupplier supplier;

    static {
        ClassLoader classLoader = WeakMapSupplier.class.getClassLoader();
        if (classLoader == null) {
            classLoader = ClassLoader.getSystemClassLoader();
        }
        // loads the implementation provided by the core module without depending on the class or class name
        supplier = ServiceLoader.load(WeakMapSupplier.class, classLoader).iterator().next();
    }

    public static <K, V> WeakMap<K, V> createMap() {
        return supplier.<K, V>buildWeakMap().build();
    }

    public static <K, V> WeakMapBuilder<K, V> buildWeakMap() {
        return supplier.buildWeakMap();
    }

    public static <T> ThreadLocalBuilder<T> buildThreadLocal() {
        return supplier.buildThreadLocal();
    }

    public static <E> WeakSet<E> createSet() {
        return supplier.createSet();
    }

    public interface WeakMapSupplier {

        <K, V> WeakMapBuilder<K, V> buildWeakMap();

        <T> ThreadLocalBuilder<T> buildThreadLocal();

        <E> WeakSet<E> createSet();
    }

    public interface WeakMapBuilder<K, V> {

        WeakMapBuilder<K, V> withInitialCapacity(int initialCapacity);

        WeakMapBuilder<K, V> withDefaultValueSupplier(@Nullable WeakMap.DefaultValueSupplier<K, V> defaultValueSupplier);

        WeakMap<K, V> build();
    }

    public interface ThreadLocalBuilder<T> {

        /**
         * Registers a globally shared instance of a {@link DetachedThreadLocal}.
         * Similar to {@link GlobalVariables} and {@link GlobalState},
         * this allows to get thread locals whose state is shared across plugin class loaders.
         * <p>
         * Be careful not to store classes from the target class loader or the plugin class loader in global thread locals.
         * This would otherwise lead to class loader leaks.
         * That's because a global thread local is referenced from the agent class loader.
         * If it held a reference to a class that's loaded by the plugin class loader, the target class loader (such as a webapp class loader)
         * is held alive by the following chain of hard references:
         * {@code Map of global thread locals (Agent CL) -plugin class instance-> -plugin class-> plugin CL -(parent)-> webapp CL}
         * </p>
         */
        ThreadLocalBuilder<T> asGlobalThreadLocal(Class<?> adviceClass, String key);

        ThreadLocalBuilder<T> withDefaultValueSupplier(@Nullable WeakMap.DefaultValueSupplier<Thread, T> defaultValueSupplier);

        DetachedThreadLocal<T> build();
    }
}
