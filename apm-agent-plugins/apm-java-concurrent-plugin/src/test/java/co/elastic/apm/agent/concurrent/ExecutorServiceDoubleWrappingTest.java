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

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

public class ExecutorServiceDoubleWrappingTest extends AbstractInstrumentationTest {
    private static final Object TEST_OBJECT = new Object();

    private final RunnableWrapperExecutorService executor = RunnableWrapperExecutorService.wrap(Executors.newSingleThreadExecutor(), tracer);
    private Transaction transaction;

    @Before
    public void setUp() {
        transaction = tracer.startTransaction(TraceContext.asRoot(), null, null).withName("Transaction").activate();
    }

    @After
    public void tearDown() {
        transaction.deactivate().end();
        assertThat(tracer.getActive()).isNull();
    }

    @Test
    public void testWrappingTransactionExecuteTwice() throws InterruptedException {
        executor.execute(this::createAsyncSpan);
        assertThat(reporter.getFirstSpan(500).getNameAsString()).isEqualTo("Async");
    }

    @Test
    public void testWrappingTransactionSubmitRunnableTwice() throws InterruptedException, ExecutionException {
        Future<?> future = executor.submit(this::createAsyncSpan);
        assertThat(future.get()).isNull();
        assertThat(reporter.getFirstSpan(500).getNameAsString()).isEqualTo("Async");
    }

    @Test
    public void testWrappingTransactionSubmitRunnableWithResultTwice() throws InterruptedException, ExecutionException {
        Future<?> future = executor.submit(this::createAsyncSpan, TEST_OBJECT);
        assertThat(future.get()).isEqualTo(TEST_OBJECT);
        Span span = reporter.getFirstSpan(500);
        assertThat(span.getNameAsString()).isEqualTo("Async");
        assertThat(span.getTraceContext().getParentId()).isEqualTo(transaction.getTraceContext().getId());
    }

    @Test
    public void testWrappingTransactionSubmitCallableTwice() throws InterruptedException, ExecutionException {
        Future<?> future = executor.submit(() -> {
            createAsyncSpan();
            return TEST_OBJECT;
        });
        assertThat(future.get()).isEqualTo(TEST_OBJECT);
        Span span = reporter.getFirstSpan(500);
        assertThat(span.getNameAsString()).isEqualTo("Async");
        assertThat(span.getTraceContext().getParentId()).isEqualTo(transaction.getTraceContext().getId());
    }

    private void createAsyncSpan() {
        int numWrappers = 0;
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement stackTraceElement : stackTrace) {
            if (stackTraceElement.getClassName().equals("co.elastic.apm.agent.impl.async.SpanInScopeRunnableWrapper") &&
                stackTraceElement.getMethodName().equals("run")) {
                numWrappers++;
            } else if (stackTraceElement.getClassName().equals("co.elastic.apm.agent.impl.async.SpanInScopeCallableWrapper") &&
                stackTraceElement.getMethodName().equals("call")) {
                numWrappers++;
            }
        }
        assertThat(numWrappers).isEqualTo(1);
        assertThat(tracer.currentTransaction()).isEqualTo(transaction);
        assertThat(tracer.getActive().getTraceContext().getId()).isEqualTo(transaction.getTraceContext().getId());
        tracer.getActive().createSpan().withName("Async").end();
    }
}
