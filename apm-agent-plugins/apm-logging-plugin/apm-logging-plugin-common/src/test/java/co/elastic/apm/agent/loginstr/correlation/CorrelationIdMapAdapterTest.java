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
package co.elastic.apm.agent.loginstr.correlation;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.tracer.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


public class CorrelationIdMapAdapterTest {

    private final ElasticApmTracer tracer = MockTracer.createRealTracer();

    @BeforeEach
    void setUp() {
        GlobalTracer.init(tracer);
    }

    @AfterEach
    void tearDown() {
        tracer.stop();
        GlobalTracer.setNoop();
    }

    @Test
    void testNoContext() {
        assertThat(CorrelationIdMapAdapter.get()).isEmpty();
    }

    @Test
    void testTransactionContext() {
        Transaction transaction = tracer.startRootTransaction(null);
        try (Scope scope = transaction.activateInScope()) {
            assertThat(CorrelationIdMapAdapter.get()).containsOnlyKeys("trace.id", "transaction.id");
        } finally {
            transaction.end();
        }
        assertThat(CorrelationIdMapAdapter.get()).isEmpty();
    }

    @Test
    void testSpanContext() {
        Transaction transaction = tracer.startRootTransaction(null);
        Span span = transaction.createSpan();
        try (Scope scope = span.activateInScope()) {
            assertThat(CorrelationIdMapAdapter.get()).containsOnlyKeys("trace.id", "transaction.id");
        } finally {
            span.end();
        }
        transaction.end();
        assertThat(CorrelationIdMapAdapter.get()).isEmpty();
    }

    @Test
    void testSingleInstance() {
        assertThat(CorrelationIdMapAdapter.get()).isSameAs(CorrelationIdMapAdapter.get());
        assertThat(CorrelationIdMapAdapter.get().entrySet()).isSameAs(CorrelationIdMapAdapter.get().entrySet());
        assertThat(CorrelationIdMapAdapter.allKeys()).isSameAs(CorrelationIdMapAdapter.allKeys());
    }
}
