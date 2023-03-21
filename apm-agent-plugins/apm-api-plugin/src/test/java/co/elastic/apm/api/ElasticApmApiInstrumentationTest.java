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
package co.elastic.apm.api;

import co.elastic.apm.AbstractApiTest;
import co.elastic.apm.agent.configuration.ServiceInfo;
import co.elastic.apm.agent.impl.TextHeaderMapAccessor;
import co.elastic.apm.agent.impl.TracerInternalApiUtils;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticApmApiInstrumentationTest extends AbstractApiTest {

    @Test
    void testCreateTransaction() {
        Transaction transaction = ElasticApm.startTransaction();
        assertThat(transaction).isNotSameAs(NoopTransaction.INSTANCE);
        assertThat(ElasticApm.currentTransaction()).isSameAs(NoopTransaction.INSTANCE);
        transaction.end();
    }

    @Test
    void testNoCurrentTransaction() {
        assertThat(ElasticApm.currentTransaction()).isSameAs(NoopTransaction.INSTANCE);
    }

    @Test
    void testLegacyTransactionCreateSpan() {
        Transaction transaction = ElasticApm.startTransaction();
        Span span = transaction.createSpan();
        assertThat(span).isNotSameAs(NoopSpan.INSTANCE);
        assertThat(ElasticApm.currentSpan()).isSameAs(NoopSpan.INSTANCE);
        span.end();
        transaction.end();
    }

    @Test
    void testStartSpan() {
        Transaction transaction = ElasticApm.startTransaction();
        Span span = transaction.startSpan();
        assertThat(span).isNotSameAs(NoopSpan.INSTANCE);
        assertThat(ElasticApm.currentSpan()).isSameAs(NoopSpan.INSTANCE);
        span.end();
        transaction.end();
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
        reporter.disableCheckUnknownOutcome();

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
        final co.elastic.apm.agent.impl.transaction.Transaction transaction = startTestRootTransaction();
        final Span span = ElasticApm.currentSpan().startSpan("db", "mysql", "query");
        span.setName("span");
        span.end();
        transaction.deactivate().end();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(1);
        co.elastic.apm.agent.impl.transaction.Span internalSpan = reporter.getFirstSpan();
        assertThat(internalSpan.getTraceContext().getParentId()).isEqualTo(reporter.getFirstTransaction().getTraceContext().getId());
        assertThat(internalSpan.getType()).isEqualTo("db");
        assertThat(internalSpan.getSubtype()).isEqualTo("mysql");
        assertThat(internalSpan.getAction()).isEqualTo("query");
    }

    @Test
    void testLegacySpanCreationAndTyping() {
        final co.elastic.apm.agent.impl.transaction.Transaction transaction = startTestRootTransaction();
        final Span span = ElasticApm.currentSpan().createSpan();
        span.setName("span");
        span.setType("db.mysql.query.etc");
        span.end();
        transaction.deactivate().end();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(1);
        co.elastic.apm.agent.impl.transaction.Span internalSpan = reporter.getFirstSpan();
        assertThat(internalSpan.getTraceContext().getParentId()).isEqualTo(reporter.getFirstTransaction().getTraceContext().getId());
        assertThat(internalSpan.getType()).isEqualTo("db");
        assertThat(internalSpan.getSubtype()).isEqualTo("mysql");
        assertThat(internalSpan.getAction()).isEqualTo("query.etc");
    }

    // https://github.com/elastic/apm-agent-java/issues/132
    @Test
    void testAutomaticAndManualTransactions() {

        final co.elastic.apm.agent.impl.transaction.Transaction transaction = startTestRootTransaction();
        final Transaction manualTransaction = ElasticApm.startTransaction();
        manualTransaction.setName("manual transaction");
        manualTransaction.setType("request");
        manualTransaction.end();
        transaction.deactivate().end();
        assertThat(reporter.getTransactions()).hasSize(2);
    }

    @Test
    void testGetId_distributedTracingEnabled() {

        co.elastic.apm.agent.impl.transaction.Transaction transaction = tracer.startRootTransaction(null).withType(Transaction.TYPE_REQUEST);
        try (co.elastic.apm.agent.tracer.Scope scope = transaction.activateInScope()) {
            assertThat(ElasticApm.currentTransaction().getId()).isEqualTo(transaction.getTraceContext().getId().toString());
            assertThat(ElasticApm.currentTransaction().getTraceId()).isEqualTo(transaction.getTraceContext().getTraceId().toString());
            assertThat(ElasticApm.currentSpan().getId()).isEqualTo(transaction.getTraceContext().getId().toString());
            assertThat(ElasticApm.currentSpan().getTraceId()).isEqualTo(transaction.getTraceContext().getTraceId().toString());
            co.elastic.apm.agent.impl.transaction.Span span = transaction.createSpan().withType("db").withSubtype("mysql").withName("SELECT");
            try (co.elastic.apm.agent.tracer.Scope spanScope = span.activateInScope()) {
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
    void testAddLabel() {

        Transaction transaction = ElasticApm.startTransaction();
        transaction.setName("foo");
        transaction.setType("request");
        transaction.addLabel("foo1", "bar1");
        transaction.addLabel("foo", "bar");
        transaction.addLabel("number", 1);
        transaction.addLabel("boolean", true);
        transaction.addLabel("null", (String) null);
        Span span = transaction.startSpan("custom", null, null);
        span.setName("foo");
        span.addLabel("bar1", "baz1");
        span.addLabel("bar", "baz");
        span.addLabel("number", 1);
        span.addLabel("boolean", true);
        span.addLabel("null", (String) null);
        span.setOutcome(Outcome.FAILURE);
        span.end();
        transaction.setOutcome(Outcome.SUCCESS).end();
        assertThat(reporter.getTransactions()).hasSize(1);

        assertThat(reporter.getFirstTransaction().getContext().getLabel("foo1")).isEqualTo("bar1");
        assertThat(reporter.getFirstTransaction().getContext().getLabel("foo")).isEqualTo("bar");
        assertThat(reporter.getFirstTransaction().getContext().getLabel("number")).isEqualTo(1);
        assertThat(reporter.getFirstTransaction().getContext().getLabel("boolean")).isEqualTo(true);
        assertThat(reporter.getFirstTransaction().getContext().getLabel("null")).isNull();
        assertThat(reporter.getFirstTransaction().getOutcome().toString()).isEqualTo("success");

        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getFirstSpan().getContext().getLabel("bar1")).isEqualTo("baz1");
        assertThat(reporter.getFirstSpan().getContext().getLabel("bar")).isEqualTo("baz");
        assertThat(reporter.getFirstSpan().getContext().getLabel("number")).isEqualTo(1);
        assertThat(reporter.getFirstSpan().getContext().getLabel("boolean")).isEqualTo(true);
        assertThat(reporter.getFirstSpan().getContext().getLabel("null")).isNull();
        assertThat(reporter.getFirstSpan().getOutcome().toString()).isEqualTo("failure");
    }

    @Test
    void testAddCustomContext() {
        Transaction transaction = ElasticApm.startTransaction();
        transaction.setName("foo");
        transaction.setType("bar");
        transaction.addCustomContext("foo1", "bar1");
        transaction.addCustomContext("foo", "bar");
        transaction.addCustomContext("number", 1);
        transaction.addCustomContext("boolean", true);
        transaction.addCustomContext("null", (String) null);
        transaction.end();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getContext().getCustom("foo1")).isEqualTo("bar1");
        assertThat(reporter.getFirstTransaction().getContext().getCustom("foo")).isEqualTo("bar");
        assertThat(reporter.getFirstTransaction().getContext().getCustom("number")).isEqualTo(1);
        assertThat(reporter.getFirstTransaction().getContext().getCustom("boolean")).isEqualTo(true);
        assertThat(reporter.getFirstTransaction().getContext().getCustom("null")).isNull();
    }

    @Test
    void testScopes() {
        Transaction transaction = ElasticApm.startTransaction();
        try (co.elastic.apm.api.Scope scope = transaction.activate()) {
            assertThat(ElasticApm.currentTransaction().getId()).isEqualTo(transaction.getId());
            Span span = transaction.startSpan();
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
        transaction.end();
    }

    @Test
    void testTransactionWithRemoteParentFunction() {
        AbstractSpan<?> parent = tracer.startRootTransaction(null);
        assertThat(parent).isNotNull();
        Map<String, String> headerMap = new HashMap<>();
        parent.propagateTraceContext(headerMap, TextHeaderMapAccessor.INSTANCE);
        ElasticApm.startTransactionWithRemoteParent(headerMap::get).end();
        assertThat(reporter.getFirstTransaction().isChildOf(parent)).isTrue();
        parent.end();
    }

    @Test
    void testTransactionWithRemoteParentFunctions() {
        AbstractSpan<?> parent = tracer.startRootTransaction(null);
        assertThat(parent).isNotNull();
        Map<String, String> headerMap = new HashMap<>();
        parent.propagateTraceContext(headerMap, TextHeaderMapAccessor.INSTANCE);
        ElasticApm.startTransactionWithRemoteParent(headerMap::get, key -> Collections.singletonList(headerMap.get(key))).end();
        assertThat(reporter.getFirstTransaction().isChildOf(parent)).isTrue();
        parent.end();
    }

    @Test
    void testTransactionWithRemoteParentHeaders() {
        AbstractSpan<?> parent = tracer.startRootTransaction(null);
        assertThat(parent).isNotNull();
        Map<String, String> headerMap = new HashMap<>();
        parent.propagateTraceContext(headerMap, TextHeaderMapAccessor.INSTANCE);
        ElasticApm.startTransactionWithRemoteParent(null, key -> Collections.singletonList(headerMap.get(key))).end();
        assertThat(reporter.getFirstTransaction().isChildOf(parent)).isTrue();
        parent.end();
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

    @Test
    void testManualTimestamps() {
        final Transaction transaction = ElasticApm.startTransaction().setStartTimestamp(0);
        transaction.startSpan().setStartTimestamp(1000).end(2000);
        transaction.end(3000);

        assertThat(reporter.getFirstTransaction().getDuration()).isEqualTo(3000);
        assertThat(reporter.getFirstSpan().getDuration()).isEqualTo(1000);
    }

    @Test
    void testManualTimestampsDeactivated() {
        TracerInternalApiUtils.pauseTracer(tracer);
        final Transaction transaction = ElasticApm.startTransaction().setStartTimestamp(0);
        transaction.startSpan().setStartTimestamp(1000).end(2000);
        transaction.end(3000);

        assertThat(reporter.getTransactions()).hasSize(0);
        assertThat(reporter.getSpans()).hasSize(0);
    }

    @Test
    void testOverrideServiceNameForClassLoader() {
        ServiceInfo overridden = ServiceInfo.of("overridden");
        tracer.setServiceInfoForClassLoader(Transaction.class.getClassLoader(), overridden);
        ElasticApm.startTransaction().end();
        checkTransactionServiceInfo(overridden);
    }

    @Test
    void testOverrideServiceNameForClassLoaderWithRemoteParent() {
        ServiceInfo overridden = ServiceInfo.of("overridden");
        tracer.setServiceInfoForClassLoader(Transaction.class.getClassLoader(), overridden);
        ElasticApm.startTransactionWithRemoteParent(key -> null).end();
        checkTransactionServiceInfo(overridden);
    }

    @Test
    void testOverrideServiceVersionForClassLoader() {
        ServiceInfo overridden = ServiceInfo.of("overridden_name", "overridden_version");
        tracer.setServiceInfoForClassLoader(Transaction.class.getClassLoader(), overridden);
        ElasticApm.startTransaction().end();
        checkTransactionServiceInfo(overridden);
    }

    @Test
    void testOverrideServiceVersionForClassLoaderWithRemoteParent() {
        ServiceInfo overridden = ServiceInfo.of("overridden_name", "overridden_version");
        tracer.setServiceInfoForClassLoader(Transaction.class.getClassLoader(), overridden);
        ElasticApm.startTransactionWithRemoteParent(key -> null).end();
        checkTransactionServiceInfo(overridden);
    }

    private void checkTransactionServiceInfo(ServiceInfo expected){
        TraceContext traceContext = reporter.getFirstTransaction().getTraceContext();
        assertThat(traceContext.getServiceName()).isEqualTo(expected.getServiceName());
        assertThat(traceContext.getServiceVersion()).isEqualTo(expected.getServiceVersion());
    }

    @Test
    void testFrameworkNameWithStartTransactionWithRemoteParent() {
        ElasticApm.startTransactionWithRemoteParent(null).end();
        assertThat(reporter.getFirstTransaction().getFrameworkName()).isEqualTo("API");
    }

    @Test
    void testSetServiceInfo() {
        try {
            ElasticApm.setServiceInfoForClassLoader(ElasticApmApiInstrumentationTest.class.getClassLoader(), "My Service", "My Version");
            ServiceInfo getServiceInfo = tracer.getServiceInfoForClassLoader(ElasticApmApiInstrumentationTest.class.getClassLoader());
            assertThat(getServiceInfo.getServiceName()).isEqualTo("My Service");
            assertThat(getServiceInfo.getServiceVersion()).isEqualTo("My Version");
        } finally {
            tracer.resetServiceInfoOverrides();
        }
    }
}
