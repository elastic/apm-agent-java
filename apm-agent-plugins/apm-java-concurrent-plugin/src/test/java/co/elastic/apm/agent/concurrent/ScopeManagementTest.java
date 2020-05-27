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
import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.impl.TracerInternalApiUtils;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class ScopeManagementTest extends AbstractInstrumentationTest {

    private ElasticApmTracer tracer;
    private MockReporter reporter;
    private ConfigurationRegistry config;
    private JavaConcurrent javaConcurrent;

    @BeforeEach
    void setUp() {
        reporter = new MockReporter();
        config = SpyConfiguration.createSpyConfig();
        tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(config)
            .reporter(reporter)
            .build();
        javaConcurrent = new JavaConcurrent(tracer);
    }

    @AfterEach
    void tearDown() {
        assertThat(tracer.getActive()).isNull();
    }

    /**
     * Disables assertions in {@link ElasticApmTracer}, runs the test and restores original setting
     */
    void runTestWithAssertionsDisabled(Runnable test) {
        TracerInternalApiUtils.runWithoutAssertions(tracer, test);
    }

    @Test
    void testWrongDeactivationOrder() {
        runTestWithAssertionsDisabled(() -> {
            final Transaction transaction = tracer.startRootTransaction(null).activate();
            final Span span = transaction.createSpan().activate();
            transaction.deactivate();
            span.deactivate();

            assertThat(tracer.getActive()).isNull();
        });
    }

    @Test
    void testActivateTwice() {
        runTestWithAssertionsDisabled(() -> {
            tracer.startRootTransaction(null)
                .activate().activate()
                .deactivate().deactivate();

            assertThat(tracer.getActive()).isNull();
        });
    }

    @Test
    void testRedundantActivation() {
        runTestWithAssertionsDisabled(() -> {
            final Transaction transaction = tracer.startRootTransaction(null).activate();
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
            final Transaction transaction = tracer.startRootTransaction(null).activate();
            javaConcurrent.withContext(javaConcurrent.withContext((Runnable) () ->
                assertThat(tracer.getActive()).isSameAs(transaction))).run();
            transaction.deactivate();

            assertThat(tracer.getActive()).isNull();
        });
    }

    @Test
    void testContextAndSpanCallableActivation() {
        runTestWithAssertionsDisabled(() -> {
            final Transaction transaction = tracer.startRootTransaction(null).activate();
            try {
                assertThat(javaConcurrent.withContext(javaConcurrent.withContext(() -> tracer.currentTransaction())).call()).isSameAs(transaction);
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
            final Transaction transaction = tracer.startRootTransaction(null).activate();
            Runnable runnable = javaConcurrent.withContext((Runnable) () ->
                assertThat(tracer.currentTransaction()).isSameAs(transaction));
            javaConcurrent.withContext(runnable).run();
            transaction.deactivate();

            assertThat(tracer.getActive()).isNull();
        });
    }

    @Test
    void testSpanAndContextCallableActivation() {
        runTestWithAssertionsDisabled(() -> {
            final Transaction transaction = tracer.startRootTransaction(null).activate();
            Callable<Transaction> callable = javaConcurrent.withContext(() -> tracer.currentTransaction());
            try {
                assertThat(javaConcurrent.withContext(callable).call()).isSameAs(transaction);
            } catch (Exception e) {
                e.printStackTrace();
            }
            transaction.deactivate();

            assertThat(tracer.getActive()).isNull();
        });
    }

    @Test
    void testContextAndSpanRunnableActivationInDifferentThread() throws Exception {
        final Transaction transaction = tracer.startRootTransaction(null).activate();
        Executors.newSingleThreadExecutor().submit(javaConcurrent.withContext(javaConcurrent.withContext(() -> {
            assertThat(tracer.getActive()).isSameAs(transaction);
            assertThat(tracer.currentTransaction()).isSameAs(transaction);
        }))).get();
        transaction.deactivate();

        assertThat(tracer.getActive()).isNull();
    }

    @Test
    void testContextAndSpanCallableActivationInDifferentThread() throws Exception {
        final Transaction transaction = tracer.startRootTransaction(null).activate();
        Future<Transaction> transactionFuture = Executors.newSingleThreadExecutor().submit(javaConcurrent.withContext(javaConcurrent.withContext(() -> {
            assertThat(tracer.getActive()).isSameAs(transaction);
            return tracer.currentTransaction();
        })));
        assertThat(transactionFuture.get()).isSameAs(transaction);
        transaction.deactivate();

        assertThat(tracer.getActive()).isNull();
    }

    @Test
    void testSpanAndContextRunnableActivationInDifferentThread() throws Exception {
        final Transaction transaction = tracer.startRootTransaction(null).activate();
        Runnable runnable = javaConcurrent.withContext(() -> {
            assertThat(tracer.currentTransaction()).isSameAs(transaction);
            assertThat(tracer.getActive()).isSameAs(transaction);
        });
        Executors.newSingleThreadExecutor().submit(javaConcurrent.withContext(runnable)).get();
        transaction.deactivate();

        assertThat(tracer.getActive()).isNull();
    }

    @Test
    void testSpanAndContextCallableActivationInDifferentThread() throws Exception {
        final Transaction transaction = tracer.startRootTransaction(null).activate();
        assertThat(Executors.newSingleThreadExecutor().submit(javaConcurrent.withContext(() -> {
            assertThat(tracer.currentTransaction()).isSameAs(transaction);
            return tracer.currentTransaction();
        })).get()).isSameAs(transaction);
        transaction.deactivate();

        assertThat(tracer.getActive()).isNull();
    }

    @Test
    void testAsyncActivationAfterEnd() throws Exception {
        final Transaction transaction = tracer.startRootTransaction(null).activate();
        Callable<Transaction> callable = javaConcurrent.withContext(() -> {
            assertThat(tracer.getActive()).isSameAs(transaction);
            return tracer.currentTransaction();
        });
        transaction.deactivate().end();
        reporter.decrementReferences();
        assertThat(transaction.isReferenced()).isTrue();

        assertThat(Executors.newSingleThreadExecutor().submit(callable).get()).isSameAs(transaction);
        assertThat(transaction.isReferenced()).isFalse();
        // recycled because the transaction is finished, reported and the reference counter is 0
        assertThat(transaction.getTraceContext().getTraceId().isEmpty()).isTrue();

        assertThat(tracer.getActive()).isNull();
    }
}
