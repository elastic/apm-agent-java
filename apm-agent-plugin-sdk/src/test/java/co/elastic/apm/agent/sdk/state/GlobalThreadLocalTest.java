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

import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

public class GlobalThreadLocalTest {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Test
    void setNullValueShouldNotThrow() {
        GlobalThreadLocal.get(GlobalThreadLocalTest.class, "setNullValueShouldNotThrow").set(null);
    }

    @Test
    void testNonConstantDefaultValue() throws ExecutionException, InterruptedException {
        final GlobalThreadLocal<Object> threadLocal = GlobalThreadLocal.get(
            GlobalThreadLocalTest.class,
            "testNonConstantDefaultValue",
            new ObjectValueSupplier()
        );
        Object mainThreadDefaultValue = threadLocal.get();
        assertThat(mainThreadDefaultValue).isNotNull();
        Future<Object> future = executor.submit((Callable<Object>) threadLocal::get);
        assertThat(future.get()).isNotEqualTo(mainThreadDefaultValue);
    }

    @Test
    void testConstantDefaultValue() throws ExecutionException, InterruptedException {
        final GlobalThreadLocal<Object> threadLocal = GlobalThreadLocal.get(
            GlobalThreadLocalTest.class,
            "testConstantDefaultValue",
            new ConstantValueSupplier()
        );
        Object mainThreadDefaultValue = threadLocal.get();
        assertThat(mainThreadDefaultValue).isNotNull();
        Future<Object> future = executor.submit((Callable<Object>) threadLocal::get);
        assertThat(future.get()).isEqualTo(mainThreadDefaultValue);
    }

    @Test
    void testNullDefaultValue() {
        final GlobalThreadLocal<Object> threadLocal = GlobalThreadLocal.get(
            GlobalThreadLocalTest.class,
            "testNullDefaultValue",
            null
        );
        assertThat(threadLocal.get()).isNull();
    }

    @Test
    void testNonDefaultValue() throws ExecutionException, InterruptedException {
        final GlobalThreadLocal<Object> threadLocal = GlobalThreadLocal.get(
            GlobalThreadLocalTest.class,
            "testNonDefaultValue",
            null
        );

        threadLocal.set("main");
        assertThat(threadLocal.get()).isEqualTo("main");

        Future<Object> future = executor.submit(() -> {
            threadLocal.set("worker");
            return threadLocal.get();
        });
        assertThat(future.get()).isEqualTo("worker");

        future = executor.submit(threadLocal::getAndRemove);
        // we can rely on that because we use a single thread executor
        assertThat(future.get()).isEqualTo("worker");
        future = executor.submit((Callable<Object>) threadLocal::get);
        assertThat(future.get()).isNull();

        assertThat(threadLocal.getAndRemove()).isEqualTo("main");
        assertThat(threadLocal.get()).isNull();
    }

    private static class ObjectValueSupplier implements GlobalThreadLocal.DefaultValueSupplier<Object> {
        @Nullable
        @Override
        public Object getDefaultValueForThread() {
            return new Object();
        }
    }

    private static class ConstantValueSupplier implements GlobalThreadLocal.DefaultValueSupplier<Object> {
        private final Object defaultValue = new Object();

        @Nullable
        @Override
        public Object getDefaultValueForThread() {
            return defaultValue;
        }
    }
}
