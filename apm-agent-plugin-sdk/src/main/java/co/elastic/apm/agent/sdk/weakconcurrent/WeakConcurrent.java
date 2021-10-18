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
package co.elastic.apm.agent.sdk.weakconcurrent;

import javax.annotation.Nullable;
import java.util.ServiceLoader;

public final class WeakConcurrent {

    private static final WeakConcurrentProvider supplier;

    static {
        ClassLoader classLoader = WeakConcurrentProvider.class.getClassLoader();
        if (classLoader == null) {
            classLoader = ClassLoader.getSystemClassLoader();
        }
        // loads the implementation provided by the core module without depending on the class or class name
        supplier = ServiceLoader.load(WeakConcurrentProvider.class, classLoader).iterator().next();
    }

    /**
     * A shorthand for {@code WeakConcurrent.<K, V>weakMapBuilder().build()}
     * to avoid having to specify the generic arguments in simple cases.
     */
    public static <K, V> WeakMap<K, V> buildMap() {
        return supplier.<K, V>weakMapBuilder().build();
    }

    public static <K, V> WeakMapBuilder<K, V> weakMapBuilder() {
        return supplier.weakMapBuilder();
    }

    /**
     * A shorthand for {@code WeakConcurrent.<T>threadLocalBuilder().build()}
     * to avoid having to specify the generic arguments in simple cases.
     */
    public static <T> DetachedThreadLocal<T> buildThreadLocal() {
        return supplier.<T>threadLocalBuilder().build();
    }

    public static <T> ThreadLocalBuilder<T> threadLocalBuilder() {
        return supplier.threadLocalBuilder();
    }

    public static <E> WeakSet<E> buildSet() {
        return supplier.buildSet();
    }

    /**
     * This is an internal class.
     * Provides the implementation for creating weak concurrent maps/sets/thread locals.
     */
    public interface WeakConcurrentProvider {

        <K, V> WeakMapBuilder<K, V> weakMapBuilder();

        <T> ThreadLocalBuilder<T> threadLocalBuilder();

        <E> WeakSet<E> buildSet();
    }

    public interface WeakMapBuilder<K, V> {

        WeakMapBuilder<K, V> withInitialCapacity(int initialCapacity);

        WeakMapBuilder<K, V> withDefaultValueSupplier(@Nullable WeakMap.DefaultValueSupplier<K, V> defaultValueSupplier);

        WeakMap<K, V> build();
    }

    public interface ThreadLocalBuilder<T> {

        ThreadLocalBuilder<T> withDefaultValueSupplier(@Nullable WeakMap.DefaultValueSupplier<Thread, T> defaultValueSupplier);

        DetachedThreadLocal<T> build();
    }
}
