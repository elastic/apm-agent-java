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
package co.elastic.apm.agent.concurrent;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.baggage.BaggageContext;
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.impl.transaction.TraceStateImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.SyncTaskExecutor;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;

@RunWith(Parameterized.class)
public class ExecutorInstrumentationTest extends AbstractInstrumentationTest {

    private final Executor executor;

    public ExecutorInstrumentationTest(Supplier<ExecutorService> supplier) {
        executor = supplier.get();
    }

    @Parameterized.Parameters()
    public static Iterable<Supplier<Executor>> data() {
        return Arrays.asList(SimpleAsyncTaskExecutor::new, SyncTaskExecutor::new);
    }


    @After
    public void tearDown() {
        assertThat(tracer.getActive()).isNull();
    }

    @Test
    public void testExecutorExecute_Transaction() {
        TransactionImpl transaction = tracer.startRootTransaction(null).withName("Transaction").activate();
        executor.execute(() -> createAsyncSpan(transaction));
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

    @Test
    public void testBaggagePropagationWithTransaction() throws InterruptedException {
        TransactionImpl transaction = tracer.startRootTransaction(null).withName("Transaction").activate();
        BaggageContext transactionWithBaggage = tracer.currentContext().withUpdatedBaggage()
            .put("foo", "bar")
            .buildContext()
            .activate();

        AtomicReference<TraceStateImpl<?>> propagatedContext = new AtomicReference<>();
        CountDownLatch doneLatch = new CountDownLatch(1);
        executor.execute(() -> {
            propagatedContext.set(tracer.currentContext());
            doneLatch.countDown();
        });
        transactionWithBaggage.deactivate();
        transaction.deactivate().end();
        doneLatch.await();

        assertThat(propagatedContext.get().getBaggage())
            .hasSize(1)
            .containsEntry("foo", "bar");
        assertThat(propagatedContext.get().getTransaction()).isSameAs(transaction);
    }

    @Test
    public void testBaggagePropagationWithoutTransaction() throws InterruptedException {
        BaggageContext transactionWithBaggage = tracer.currentContext().withUpdatedBaggage()
            .put("foo", "bar")
            .buildContext()
            .activate();

        AtomicReference<TraceStateImpl<?>> propagatedContext = new AtomicReference<>();
        CountDownLatch doneLatch = new CountDownLatch(1);
        executor.execute(() -> {
            propagatedContext.set(tracer.currentContext());
            doneLatch.countDown();
        });
        transactionWithBaggage.deactivate();
        doneLatch.await();

        assertThat(propagatedContext.get().getBaggage())
            .hasSize(1)
            .containsEntry("foo", "bar");
        assertThat(propagatedContext.get().getTransaction()).isNull();
    }

    @Test
    public void testExecutorExecute_Span() {
        TransactionImpl transaction = tracer.startRootTransaction(null).withName("Transaction").activate();
        SpanImpl nonAsyncSpan = transaction.createSpan().withName("NonAsync").activate();
        executor.execute(() -> createAsyncSpan(transaction));
        try {
            // wait for the async operation to end
            assertThat(reporter.getFirstSpan(1000)).isNotNull();
        } finally {
            nonAsyncSpan.deactivate().end();
            transaction.deactivate().end();
        }
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(2);
        assertThat(nonAsyncSpan.isChildOf(transaction)).isTrue();
        assertThat(reporter.getFirstSpan().isChildOf(nonAsyncSpan)).isTrue();
    }


    private void createAsyncSpan(TransactionImpl expectedCurrent) {
        assertThat(tracer.currentTransaction()).isEqualTo(expectedCurrent);
        tracer.getActive().createSpan().withName("Async").end();
    }
}
