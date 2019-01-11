/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018-2019 Elastic and contributors
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
package co.elastic.apm.api;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.Scope;
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticApmApiInstrumentationTest extends AbstractInstrumentationTest {

    @Test
    void testCreateTransaction() {
        assertThat(ElasticApm.startTransaction()).isNotSameAs(NoopTransaction.INSTANCE);
        assertThat(ElasticApm.currentTransaction()).isSameAs(NoopTransaction.INSTANCE);
    }

    @Test
    void testNoCurrentTransaction() {
        assertThat(ElasticApm.currentTransaction()).isSameAs(NoopTransaction.INSTANCE);
    }

    @Test
    void testCreateSpan() {
        assertThat(ElasticApm.startTransaction().createSpan()).isNotSameAs(NoopSpan.INSTANCE);
        assertThat(ElasticApm.currentSpan()).isSameAs(NoopSpan.INSTANCE);
    }

    @Test
    void testNoCurrentSpan() {
        assertThat(ElasticApm.currentSpan()).isSameAs(NoopSpan.INSTANCE);
    }

    @Test
    void testCaptureException() {
        ElasticApm.captureException(new RuntimeException("Bazinga"));
        assertThat(reporter.getErrors()).hasSize(1);
    }

    @Test
    void testCaptureExceptionNoopSpan() {
        ElasticApm.currentSpan().captureException(new RuntimeException("Bazinga"));
        assertThat(reporter.getErrors()).hasSize(1);
    }

    @Test
    void testTransactionWithError() {
        final Transaction transaction = ElasticApm.startTransaction();
        transaction.setType("request");
        transaction.setName("transaction");
        transaction.captureException(new RuntimeException("Bazinga"));
        transaction.end();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getErrors()).hasSize(1);
    }

    @Test
    void testCreateChildSpanOfCurrentTransaction() {
        final co.elastic.apm.agent.impl.transaction.Transaction transaction = tracer.startTransaction().withType("request").withName("transaction").activate();
        final Span span = ElasticApm.currentSpan().createSpan();
        span.setName("span");
        span.setType("db");
        span.end();
        transaction.deactivate().end();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getFirstSpan().getTraceContext().getParentId()).isEqualTo(reporter.getFirstTransaction().getTraceContext().getId());
    }

    // https://github.com/elastic/apm-agent-java/issues/132
    @Test
    void testAutomaticAndManualTransactions() {
        final co.elastic.apm.agent.impl.transaction.Transaction transaction = tracer.startTransaction().withType("request").withName("transaction").activate();
        final Transaction manualTransaction = ElasticApm.startTransaction();
        manualTransaction.setName("manual transaction");
        manualTransaction.setType("request");
        manualTransaction.end();
        transaction.deactivate().end();
        assertThat(reporter.getTransactions()).hasSize(2);
    }

    @Test
    void testGetId_distributedTracingEnabled() {
        co.elastic.apm.agent.impl.transaction.Transaction transaction = tracer.startTransaction().withType(Transaction.TYPE_REQUEST);
        try (Scope scope = transaction.activateInScope()) {
            assertThat(ElasticApm.currentTransaction().getId()).isEqualTo(transaction.getTraceContext().getId().toString());
            assertThat(ElasticApm.currentTransaction().getTraceId()).isEqualTo(transaction.getTraceContext().getTraceId().toString());
            assertThat(ElasticApm.currentSpan().getId()).isEqualTo(transaction.getTraceContext().getId().toString());
            assertThat(ElasticApm.currentSpan().getTraceId()).isEqualTo(transaction.getTraceContext().getTraceId().toString());
            co.elastic.apm.agent.impl.transaction.Span span = transaction.createSpan().withType("db").withName("SELECT");
            try (Scope spanScope = span.activateInScope()) {
                assertThat(ElasticApm.currentSpan().getId()).isEqualTo(span.getTraceContext().getId().toString());
                assertThat(ElasticApm.currentSpan().getTraceId()).isEqualTo(span.getTraceContext().getTraceId().toString());
            } finally {
                span.end();
            }
        } finally {
            transaction.end();
        }
    }

    @Test
    void testGetId_noop() {
        assertThat(ElasticApm.currentTransaction().getId()).isEmpty();
        assertThat(ElasticApm.currentSpan().getId()).isEmpty();
    }

    @Test
    void testAddTag() {
        Transaction transaction = ElasticApm.startTransaction();
        transaction.setName("foo");
        transaction.setType("bar");
        transaction.addTag("foo", "bar");
        Span span = transaction.createSpan();
        span.setName("foo");
        span.setType("bar");
        span.addTag("bar", "baz");
        span.end();
        transaction.end();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getContext().getTags()).containsEntry("foo", "bar");
        assertThat(reporter.getFirstSpan().getContext().getTags()).containsEntry("bar", "baz");
    }

    @Test
    void testScopes() {
        Transaction transaction = ElasticApm.startTransaction();
        try (co.elastic.apm.api.Scope scope = transaction.activate()) {
            assertThat(ElasticApm.currentTransaction().getId()).isEqualTo(transaction.getId());
            Span span = transaction.createSpan();
            try (co.elastic.apm.api.Scope spanScope = span.activate()) {
                assertThat(ElasticApm.currentSpan().getId()).isEqualTo(span.getId());
            } finally {
                span.end();
            }
            assertThat(ElasticApm.currentSpan().getId()).isEqualTo(transaction.getId());
        } finally {
            transaction.end();
        }
        assertThat(ElasticApm.currentTransaction()).isSameAs(NoopTransaction.INSTANCE);

    }

    @Test
    void testEnsureParentId() {
        final Transaction transaction = ElasticApm.startTransaction();
        try (co.elastic.apm.api.Scope scope = transaction.activate()) {
            assertThat(tracer.currentTransaction()).isNotNull();
            assertThat(tracer.currentTransaction().getTraceContext().getParentId().isEmpty()).isTrue();
            String rumTransactionId = transaction.ensureParentId();
            assertThat(tracer.currentTransaction().getTraceContext().getParentId().toString()).isEqualTo(rumTransactionId);
            assertThat(transaction.ensureParentId()).isEqualTo(rumTransactionId);
        }
    }

    @Test
    void testTransactionWithRemoteParentFunction() {
        final TraceContext parent = TraceContext.with64BitId(tracer);
        parent.asRootSpan(ConstantSampler.of(true));
        ElasticApm.startTransactionWithRemoteParent(key -> parent.getOutgoingTraceParentHeader().toString()).end();
        assertThat(reporter.getFirstTransaction().getTraceContext().isChildOf(parent)).isTrue();
    }

    @Test
    void testTransactionWithRemoteParentFunctions() {
        final TraceContext parent = TraceContext.with64BitId(tracer);
        parent.asRootSpan(ConstantSampler.of(true));
        final Map<String, String> map = Map.of(TraceContext.TRACE_PARENT_HEADER, parent.getOutgoingTraceParentHeader().toString());
        ElasticApm.startTransactionWithRemoteParent(map::get, key -> Collections.singletonList(map.get(key))).end();
        assertThat(reporter.getFirstTransaction().getTraceContext().isChildOf(parent)).isTrue();
    }

    @Test
    void testTransactionWithRemoteParentHeaders() {
        final TraceContext parent = TraceContext.with64BitId(tracer);
        parent.asRootSpan(ConstantSampler.of(true));
        final Map<String, String> map = Map.of(TraceContext.TRACE_PARENT_HEADER, parent.getOutgoingTraceParentHeader().toString());
        ElasticApm.startTransactionWithRemoteParent(null, key -> Collections.singletonList(map.get(key))).end();
        assertThat(reporter.getFirstTransaction().getTraceContext().isChildOf(parent)).isTrue();
    }

    @Test
    void testTransactionWithRemoteParentNullFunction() {
        ElasticApm.startTransactionWithRemoteParent(null).end();
        assertThat(reporter.getFirstTransaction().getTraceContext().isRoot()).isTrue();
    }

    @Test
    void testTransactionWithRemoteParentNullFunctions() {
        ElasticApm.startTransactionWithRemoteParent(null, null).end();
        assertThat(reporter.getFirstTransaction().getTraceContext().isRoot()).isTrue();
    }

}
