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
import co.elastic.apm.agent.impl.async.SpanInScopeCallableWrapper;
import co.elastic.apm.agent.impl.async.SpanInScopeRunnableWrapper;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FailingExecutorInstrumentationTest extends AbstractInstrumentationTest {

    private ExecutorService executor;
    private AtomicInteger runCounter;
    private AtomicInteger submitWithWrapperCounter;

    @BeforeEach
    void setUp() {
        executor = ExecutorServiceWrapper.wrap(new ForkJoinPool() {
            @Override
            public ForkJoinTask<?> submit(Runnable task) {
                if (task instanceof SpanInScopeRunnableWrapper) {
                    submitWithWrapperCounter.incrementAndGet();
                    throw new ClassCastException();
                }
                return super.submit(task);
            }

            @Override
            public <V> ForkJoinTask<V> submit(Callable<V> task) {
                if (task instanceof SpanInScopeCallableWrapper) {
                    submitWithWrapperCounter.incrementAndGet();
                    throw new IllegalArgumentException();
                }
                return super.submit(task);
            }

            @Override
            public void execute(Runnable task) {
                throw new IllegalArgumentException();
            }

            @Override
            public <T> ForkJoinTask<T> submit(Runnable task, T result) {
                throw new UnsupportedOperationException();
            }
        });
        tracer.startTransaction(TraceContext.asRoot(), null, null).activate();
        runCounter = new AtomicInteger();
        submitWithWrapperCounter = new AtomicInteger();
    }

    @AfterEach
    void tearDown() {
        tracer.currentTransaction().deactivate().end();
    }

    @Test
    void testRunnableWrappersNotSupported() throws Exception {
        executor.submit(() -> {
            assertThat(runCounter.incrementAndGet()).isEqualTo(1);
        }).get();
        assertThat(submitWithWrapperCounter.get()).isEqualTo(1);

        assertThat(ExecutorInstrumentation.excluded.contains(executor)).isTrue();
        executor.submit(() -> {
            assertThat(runCounter.incrementAndGet()).isEqualTo(2);
        }).get();
        assertThat(submitWithWrapperCounter.get()).isEqualTo(1);
    }

    @Test
    void testCallableWrappersNotSupported() throws Exception {
        executor.submit(() -> {
            assertThat(runCounter.incrementAndGet()).isEqualTo(1);
            return null;
        }).get();
        assertThat(submitWithWrapperCounter.get()).isEqualTo(1);

        assertThat(ExecutorInstrumentation.excluded.contains(executor)).isTrue();
        executor.submit(() -> {
            assertThat(runCounter.incrementAndGet()).isEqualTo(2);
        }).get();
        assertThat(submitWithWrapperCounter.get()).isEqualTo(1);
    }

    @Test
    void testOnlyRetryOnce() {
        assertThatThrownBy(() -> executor.execute(() -> {
        })).isInstanceOf(IllegalArgumentException.class);
        assertThat(ExecutorInstrumentation.excluded.contains(executor)).isTrue();
    }

    @Test
    void testUnrelatedException() {
        assertThatThrownBy(() -> executor.submit(() -> {
        }, null)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(ExecutorInstrumentation.excluded.contains(executor)).isFalse();
    }

}
