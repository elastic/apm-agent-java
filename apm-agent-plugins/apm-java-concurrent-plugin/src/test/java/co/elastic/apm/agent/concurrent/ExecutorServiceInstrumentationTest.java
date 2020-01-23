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
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class ExecutorServiceInstrumentationTest extends AbstractInstrumentationTest {

    private final ExecutorService executor;
    private Transaction transaction;

    public ExecutorServiceInstrumentationTest(Supplier<ExecutorService> supplier) {
        executor = supplier.get();
    }

    @Parameterized.Parameters()
    public static Iterable<Supplier<ExecutorService>> data() {
        return Arrays.asList(() -> ExecutorServiceWrapper.wrap(Executors.newSingleThreadExecutor()),
            () -> ExecutorServiceWrapper.wrap(Executors.newSingleThreadScheduledExecutor()),
            () -> ExecutorServiceWrapper.wrap(new ForkJoinPool()),
            () -> GlobalEventExecutor.INSTANCE);
    }

    @Before
    public void setUp() {
        transaction = tracer.startTransaction(TraceContext.asRoot(), null, null).withName("Transaction").activate();
    }

    @After
    public void tearDown() {
        assertThat(tracer.getActive()).isNull();
    }

    @Test
    public void testExecutorSubmitRunnableAnonymousInnerClass() throws Exception {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                createAsyncSpan();
            }
        }).get();

        assertOnlySpanIsChildOfOnlyTransaction();
    }

    @Test
    public void testExecutorSubmitRunnableLambda() throws Exception {
        executor.submit(() -> createAsyncSpan()).get(1, TimeUnit.SECONDS);
        assertOnlySpanIsChildOfOnlyTransaction();
    }

    @Test
    public void testExecutorExecute() throws Exception {
        executor.execute(this::createAsyncSpan);
        assertOnlySpanIsChildOfOnlyTransaction();
    }

    @Test
    public void testExecutorSubmitRunnableWithResult() throws Exception {
        executor.submit(this::createAsyncSpan, null);
        assertOnlySpanIsChildOfOnlyTransaction();
    }

    @Test
    public void testExecutorSubmitCallableMethodReference() throws Exception {
        executor.submit(() -> {
            createAsyncSpan();
            return null;
        }).get(1, TimeUnit.SECONDS);
        assertOnlySpanIsChildOfOnlyTransaction();
    }

    private void assertOnlySpanIsChildOfOnlyTransaction() throws InterruptedException {
        try {
            // wait for the async operation to end
            assertThat(reporter.getFirstSpan(1000)).isNotNull();
        } finally {
            transaction.deactivate().end();
        }
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getFirstSpan().isChildOf(reporter.getFirstTransaction())).isTrue();
    }

    private void createAsyncSpan() {
        assertThat(tracer.getActive().getTraceContext().getId()).isEqualTo(transaction.getTraceContext().getId());
        tracer.getActive().createSpan().withName("Async").end();
    }
}
