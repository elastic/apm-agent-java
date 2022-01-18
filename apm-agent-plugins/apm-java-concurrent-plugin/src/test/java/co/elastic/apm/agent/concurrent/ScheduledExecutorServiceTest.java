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
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class ScheduledExecutorServiceTest extends AbstractInstrumentationTest {

    private ScheduledThreadPoolExecutor scheduler;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        transaction = tracer.startRootTransaction(null).withName("transaction").activate();
        scheduler = new ScheduledThreadPoolExecutor(1) {
            @Override
            public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
                return super.schedule(callable, delay, unit);
            }

            @Override
            public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
                return super.schedule(command, delay, unit);
            }
        };
    }

    @AfterEach
    void tearDown() {
        if (tracer.getActive() != null) {
            transaction.deactivate().end();
        }
    }

    @Test
    void testScheduleCallable() throws Exception {
        final ScheduledFuture<? extends AbstractSpan<?>> future = scheduler.schedule(() -> tracer.getActive(), 0, TimeUnit.SECONDS);
        assertThat(future.get()).isEqualTo(transaction);
        assertThat(tracer.getActive()).isEqualTo(transaction);
    }

    @Test
    void testScheduleRunnable() throws Exception {
        AtomicReference<AbstractSpan<?>> ref = new AtomicReference<>();
        scheduler.schedule(() -> ref.set(tracer.getActive()), 0, TimeUnit.SECONDS).get();
        assertThat(ref.get()).isEqualTo(transaction);
        assertThat(tracer.getActive()).isEqualTo(transaction);
    }

    @Test
    void testScheduleCallable_delayAndEndTransaction() throws Exception {
        final ScheduledFuture<? extends AbstractSpan<?>> scheduledFuture = scheduler.schedule(() -> tracer.getActive(), 50, TimeUnit.MILLISECONDS);
        verifyEndedTransactionIsStillReferenced(scheduledFuture);
        assertThat(scheduledFuture.get()).isEqualTo(transaction);
    }

    @Test
    void testScheduleRunnable_delayAndEndTransaction() throws Exception {
        AtomicReference<AbstractSpan<?>> ref = new AtomicReference<>();
        ScheduledFuture<?> scheduledTaskFuture = scheduler.schedule(() -> ref.set(tracer.getActive()), 50, TimeUnit.MILLISECONDS);
        verifyEndedTransactionIsStillReferenced(scheduledTaskFuture);
        assertThat(ref.get()).isEqualTo(transaction);
    }

    private void verifyEndedTransactionIsStillReferenced(ScheduledFuture<?> scheduledTaskFuture) throws InterruptedException, java.util.concurrent.ExecutionException {
        transaction.deactivate().end();
        // decrementing references to mock what the real reporter would do
        transaction.decrementReferences();
        // make sure the transaction is still referenced and not recycled yet
        assertThat(transaction.isReferenced()).isTrue();
        assertThat(transaction.getTraceContext().getId().isEmpty()).isFalse();

        scheduledTaskFuture.get();

        // make sure the transaction is recycled after the task has terminated
        assertThat(transaction.isReferenced()).isFalse();
        assertThat(transaction.getTraceContext().getId().isEmpty()).isTrue();
    }
}
