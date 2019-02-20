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

import java.util.concurrent.Executors;

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
            final Transaction transaction = tracer.startTransaction().activate();
            final Span span = transaction.createSpan().activate();
            transaction.deactivate();
            span.deactivate();

            assertThat(tracer.getActive()).isNull();
        });
    }

    @Test
    void testActivateTwice() {
        runTestWithAssertionsDisabled(() -> {
            tracer.startTransaction()
                .activate().activate()
                .deactivate().deactivate();

            assertThat(tracer.getActive()).isNull();
        });
    }

    @Test
    void testMissingDeactivation() {
        runTestWithAssertionsDisabled(() -> {
            final Transaction transaction = tracer.startTransaction().activate();
            transaction.createSpan().activate();
            transaction.deactivate();

            assertThat(tracer.getActive()).isNull();
        });
    }

    @Test
    void testContextAndSpanActivation() {
        runTestWithAssertionsDisabled(() -> {
            final Transaction transaction = tracer.startTransaction().activate();
            transaction.withActiveContext(transaction.withActiveSpan(() ->
                assertThat(tracer.getActive()).isSameAs(transaction))).run();
            transaction.deactivate();

            assertThat(tracer.getActive()).isNull();
        });
    }

    @Test
    void testSpanAndContextActivation() {
        runTestWithAssertionsDisabled(() -> {
            final Transaction transaction = tracer.startTransaction().activate();
            transaction.withActiveSpan(transaction.withActiveContext((Runnable) () ->
                assertThat(tracer.currentTransaction()).isSameAs(transaction))).run();
            transaction.deactivate();

            assertThat(tracer.getActive()).isNull();
        });
    }

    @Test
    void testContextAndSpanActivationInDifferentThread() throws Exception {
        final Transaction transaction = tracer.startTransaction().activate();
        Executors.newSingleThreadExecutor().submit(transaction.withActiveContext(transaction.withActiveSpan(() -> {
            assertThat(tracer.getActive()).isSameAs(transaction);
            assertThat(tracer.currentTransaction()).isSameAs(transaction);
        }))).get();
        transaction.deactivate();

        assertThat(tracer.getActive()).isNull();
    }

    @Test
    void testSpanAndContextActivationInDifferentThread() throws Exception {
        final Transaction transaction = tracer.startTransaction().activate();
        Executors.newSingleThreadExecutor().submit(transaction.withActiveSpan(transaction.withActiveContext(() -> {
            assertThat(tracer.currentTransaction()).isSameAs(transaction);
            assertThat(tracer.getActive()).isInstanceOf(TraceContext.class);
        }))).get();
        transaction.deactivate();

        assertThat(tracer.getActive()).isNull();
    }
}
