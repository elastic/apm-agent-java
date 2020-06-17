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
package co.elastic.apm.agent.concurrent;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * We can't instrument bootstrap classes, like {@link ForkJoinPool} in unit tests currently.
 * This class makes sure the relevat methods can be instrumented.
 */
public class InstrumentableForkJoinPool extends ForkJoinPool {

    public static <V> ForkJoinTask<V> newTask(Supplier<V> supplier) {
        return new AdaptedSupplier<>(supplier);
    }

    @Override
    public <T> T invoke(ForkJoinTask<T> task) {
        return super.invoke(task);
    }

    @Override
    public void execute(ForkJoinTask<?> task) {
        super.execute(task);
    }

    @Override
    public void execute(Runnable task) {
        super.execute(task);
    }

    @Override
    public <T> ForkJoinTask<T> submit(ForkJoinTask<T> task) {
        return super.submit(task);
    }

    @Override
    public <T> ForkJoinTask<T> submit(Callable<T> task) {
        return super.submit(task);
    }

    @Override
    public <T> ForkJoinTask<T> submit(Runnable task, T result) {
        return super.submit(task, result);
    }

    @Override
    public ForkJoinTask<?> submit(Runnable task) {
        return super.submit(task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
        return super.invokeAll(tasks);
    }

    private static class AdaptedSupplier<V> extends ForkJoinTask<V> implements Runnable {

        private final Supplier<V> supplier;
        private V result;

        public AdaptedSupplier(Supplier<V> supplier) {
            this.supplier = supplier;
        }

        @Override
        public V getRawResult() {
            return result;
        }

        @Override
        protected void setRawResult(V value) {
            result = value;
        }

        @Override
        protected boolean exec() {
            result = supplier.get();
            return true;
        }

        @Override
        public final void run() { invoke(); }

    }
}
