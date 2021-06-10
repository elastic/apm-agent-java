/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.TracerInternalApiUtils;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class ScopeManagementTest extends AbstractInstrumentationTest {

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
            transaction.deactivate().end();
            span.deactivate().end();

            assertThat(tracer.getActive()).isNull();
        });
    }

    @Test
    void testActivateTwice() {
        runTestWithAssertionsDisabled(() -> {
            tracer.startRootTransaction(null)
                .activate().activate()
                .deactivate().deactivate()
                .end();

            assertThat(tracer.getActive()).isNull();
        });
    }

    @Test
    void testRedundantActivation() {
        disableRecyclingValidation();
        runTestWithAssertionsDisabled(() -> {
            final Transaction transaction = tracer.startRootTransaction(null).activate();
            transaction.createSpan().activate().end();
            transaction.deactivate();
            assertThat(tracer.getActive()).isEqualTo(transaction);
            transaction.deactivate().end();
            assertThat(tracer.getActive()).isNull();
        });
    }

    @Test
    void testSpanAndContextCallableActivation() {
        runTestWithAssertionsDisabled(() -> {
            final Transaction transaction = tracer.startRootTransaction(null).activate();
            Callable<Transaction> callable = () -> tracer.currentTransaction();
            try {
                assertThat(callable.call()).isSameAs(transaction);
            } catch (Exception e) {
                e.printStackTrace();
            }
            transaction.deactivate().end();

            assertThat(tracer.getActive()).isNull();
        });
    }

    @Test
    void testContextAndSpanRunnableActivationInDifferentThread() throws Exception {
        final Transaction transaction = tracer.startRootTransaction(null).activate();
        Executors.newSingleThreadExecutor().submit(() -> {
            assertThat(tracer.getActive()).isSameAs(transaction);
            assertThat(tracer.currentTransaction()).isSameAs(transaction);
        }).get();
        transaction.deactivate().end();

        assertThat(tracer.getActive()).isNull();
    }

    @Test
    void testContextAndSpanCallableActivationInDifferentThread() throws Exception {
        final Transaction transaction = tracer.startRootTransaction(null).activate();
        Future<Transaction> transactionFuture = Executors.newSingleThreadExecutor().submit(() -> {
            assertThat(tracer.getActive()).isSameAs(transaction);
            return tracer.currentTransaction();
        });
        assertThat(transactionFuture.get()).isSameAs(transaction);
        transaction.deactivate().end();

        assertThat(tracer.getActive()).isNull();
    }

    @Test
    void testSpanAndContextRunnableActivationInDifferentThread() throws Exception {
        final Transaction transaction = tracer.startRootTransaction(null).activate();
        Runnable runnable = () -> {
            assertThat(tracer.currentTransaction()).isSameAs(transaction);
            assertThat(tracer.getActive()).isSameAs(transaction);
        };
        Executors.newSingleThreadExecutor().submit(runnable).get();
        transaction.deactivate().end();

        assertThat(tracer.getActive()).isNull();
    }

    @Test
    void testSpanAndContextCallableActivationInDifferentThread() throws Exception {
        final Transaction transaction = tracer.startRootTransaction(null).activate();
        assertThat(Executors.newSingleThreadExecutor().submit(() -> {
            assertThat(tracer.currentTransaction()).isSameAs(transaction);
            return tracer.currentTransaction();
        }).get()).isSameAs(transaction);
        transaction.deactivate().end();

        assertThat(tracer.getActive()).isNull();
    }
}
