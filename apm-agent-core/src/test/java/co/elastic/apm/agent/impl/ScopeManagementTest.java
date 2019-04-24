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
package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class ScopeManagementTest {

    private ElasticApmTracer tracer;
    private MockReporter reporter;
    private ConfigurationRegistry config;

    @BeforeEach
    void setUp() {
        reporter = new MockReporter();
        config = SpyConfiguration.createSpyConfig();
        tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(config)
            .reporter(reporter)
            .build();
    }

    @AfterEach
    void tearDown() {
        assertThat(tracer.getActive()).isNull();
    }

    /**
     * Disables assertions in {@link ElasticApmTracer}, runs the test and restores original setting
     */
    void runTestWithAssertionsDisabled(Runnable test) {
        boolean assertionsEnabled = tracer.assertionsEnabled;
        try {
            tracer.assertionsEnabled = false;
            test.run();
        } finally {
            tracer.assertionsEnabled = assertionsEnabled;
        }
    }

    @Test
    void testWrongDeactivationOrder() {
        runTestWithAssertionsDisabled(() -> {
            final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null).activate();
            final Span span = transaction.createSpan().activate();
            transaction.deactivate();
            span.deactivate();

            assertThat(tracer.getActive()).isNull();
        });
    }

    @Test
    void testActivateTwice() {
        runTestWithAssertionsDisabled(() -> {
            tracer.startTransaction(TraceContext.asRoot(), null, null)
                .activate().activate()
                .deactivate().deactivate();

            assertThat(tracer.getActive()).isNull();
        });
    }

    @Test
    void testMissingDeactivation() {
        runTestWithAssertionsDisabled(() -> {
            final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null).activate();
            transaction.createSpan().activate();
            transaction.deactivate();
            assertThat(tracer.getActive()).isEqualTo(transaction);
            transaction.deactivate();
            assertThat(tracer.getActive()).isNull();
        });
    }

    @Test
    void testContextAndSpanRunnableActivation() {
        runTestWithAssertionsDisabled(() -> {
            final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null).activate();
            transaction.markLifecycleManagingThreadSwitchExpected();
            transaction.withActive(transaction.withActive((Runnable) () ->
                assertThat(tracer.getActive()).isSameAs(transaction))).run();
            transaction.deactivate();

            assertThat(tracer.getActive()).isNull();
        });
    }

    @Test
    void testContextAndSpanCallableActivation() {
        runTestWithAssertionsDisabled(() -> {
            final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null).activate();
            transaction.markLifecycleManagingThreadSwitchExpected();
            try {
                assertThat(transaction.withActive(transaction.withActive(() -> tracer.currentTransaction())).call()).isSameAs(transaction);
            } catch (Exception e) {
                e.printStackTrace();
            }
            transaction.deactivate();

            assertThat(tracer.getActive()).isNull();
        });
    }

    @Test
    void testSpanAndContextRunnableActivation() {
        runTestWithAssertionsDisabled(() -> {
            final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null).activate();
            Runnable runnable = transaction.withActive((Runnable) () ->
                assertThat(tracer.currentTransaction()).isSameAs(transaction));
            transaction.markLifecycleManagingThreadSwitchExpected();
            transaction.withActive(runnable).run();
            transaction.deactivate();

            assertThat(tracer.getActive()).isNull();
        });
    }

    @Test
    void testSpanAndContextCallableActivation() {
        runTestWithAssertionsDisabled(() -> {
            final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null).activate();
            Callable<Transaction> callable = transaction.withActive(() -> tracer.currentTransaction());
            transaction.markLifecycleManagingThreadSwitchExpected();
            try {
                assertThat(transaction.withActive(callable).call()).isSameAs(transaction);
            } catch (Exception e) {
                e.printStackTrace();
            }
            transaction.deactivate();

            assertThat(tracer.getActive()).isNull();
        });
    }

    @Test
    void testContextAndSpanRunnableActivationInDifferentThread() throws Exception {
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null).activate();
        transaction.markLifecycleManagingThreadSwitchExpected();
        Executors.newSingleThreadExecutor().submit(transaction.withActive(transaction.withActive(() -> {
            assertThat(tracer.getActive()).isSameAs(transaction);
            assertThat(tracer.currentTransaction()).isSameAs(transaction);
        }))).get();
        transaction.deactivate();

        assertThat(tracer.getActive()).isNull();
    }

    @Test
    void testContextAndSpanCallableActivationInDifferentThread() throws Exception {
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null).activate();
        transaction.markLifecycleManagingThreadSwitchExpected();
        Future<Transaction> transactionFuture = Executors.newSingleThreadExecutor().submit(transaction.withActive(transaction.withActive(() -> {
            assertThat(tracer.getActive()).isSameAs(transaction);
            return tracer.currentTransaction();
        })));
        assertThat(transactionFuture.get()).isSameAs(transaction);
        transaction.deactivate();

        assertThat(tracer.getActive()).isNull();
    }

    @Test
    void testSpanAndContextRunnableActivationInDifferentThread() throws Exception {
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null).activate();
        Runnable runnable = transaction.withActive(() -> {
            assertThat(tracer.currentTransaction()).isSameAs(transaction);
            assertThat(tracer.getActive()).isInstanceOf(TraceContext.class);
        });
        transaction.markLifecycleManagingThreadSwitchExpected();
        Executors.newSingleThreadExecutor().submit(transaction.withActive(runnable)).get();
        transaction.deactivate();

        assertThat(tracer.getActive()).isNull();
    }

    @Test
    void testSpanAndContextCallableActivationInDifferentThread() throws Exception {
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null).activate();
        Callable<Transaction> callable = transaction.withActive(() -> {
            assertThat(tracer.getActive()).isInstanceOf(TraceContext.class);
            return tracer.currentTransaction();
        });
        transaction.markLifecycleManagingThreadSwitchExpected();
        assertThat(Executors.newSingleThreadExecutor().submit(transaction.withActive(callable)).get()).isSameAs(transaction);
        transaction.deactivate();

        assertThat(tracer.getActive()).isNull();
    }
}
