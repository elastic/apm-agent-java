/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.concurrent;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.ContextInScopeRunnableWrapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class FailingExecutorInstrumentationTest extends AbstractInstrumentationTest {

    private ExecutorService executor;
    private AtomicInteger counter = new AtomicInteger();

    @BeforeEach
    void setUp() {
        executor = ExecutorServiceWrapper.wrap(new ForkJoinPool() {
            @Override
            public ForkJoinTask<?> submit(Runnable task) {
                if (task instanceof ContextInScopeRunnableWrapper) {
                    throw new ClassCastException();
                }
                return super.submit(task);
            }

            @Override
            public <V> ForkJoinTask<V> submit(Callable<V> task) {
                if (task instanceof ContextInScopeRunnableWrapper) {
                    throw new IllegalArgumentException();
                }
                return super.submit(task);
            }

            @Override
            public void execute(Runnable task) {
                throw new IllegalArgumentException();
            }
        });
        tracer.startTransaction().activate();
    }

    @AfterEach
    void tearDown() {
        tracer.currentTransaction().deactivate().end();
    }

    @Test
    void testRunnableWrappersNotSupported() throws Exception {
        executor.submit(this::failIfCalledTwice).get();
    }

    @Test
    void testCallableWrappersNotSupported() throws Exception {
        executor.submit(() -> {
            failIfCalledTwice();
            return null;
        }).get();
    }

    @Test
    void testNoInfiniteLoop() {
        Assertions.assertThatThrownBy(() -> executor.execute(() -> {
        })).isInstanceOf(IllegalArgumentException.class);
    }

    // verifies that the Runnables/Callables are executed at most once
    void failIfCalledTwice() {
        assertThat(counter.incrementAndGet()).isLessThan(2);
    }
}
