/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.opentracing;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Id;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.log.Fields;
import io.opentracing.propagation.*;
import io.opentracing.tag.Tags;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.assertj.core.data.Offset.offset;

class OpenTracingBridgeTest extends AbstractInstrumentationTest {

    private ElasticApmTracer apmTracer;

    @BeforeEach
    void setUp() {
        apmTracer = new ElasticApmTracer();
        reporter.reset();
    }

    @AfterEach
    void clearStack() {
        ApmScope active;
        while ((active = apmTracer.scopeManager().active()) != null) {
            active.close();
        }
    }

    @Test
    void testCreateNonActiveTransaction() {
        final Span span = apmTracer.buildSpan("test").withStartTimestamp(0).start();

        assertThat(apmTracer.activeSpan()).isNull();
        assertThat(apmTracer.scopeManager().active()).isNull();

        span.finish(TimeUnit.MILLISECONDS.toMicros(1));

        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getDuration()).isEqualTo(1000);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("test");
    }

    @Test
    void sanityCheckRealTimestamps() {
        final Span transactionSpan = apmTracer.buildSpan("transactionSpan").start();
        apmTracer.buildSpan("nestedSpan").asChildOf(transactionSpan).start().finish();
        transactionSpan.finish();
        final long epochMicros = System.currentTimeMillis() * 1000;

        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getDuration()).isLessThan(MINUTES.toMicros(1));
        assertThat(reporter.getFirstTransaction().getTimestamp()).isCloseTo(epochMicros, offset(MINUTES.toMicros(1)));

        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getFirstSpan().getDuration()).isLessThan(MINUTES.toMicros(1));
        assertThat(reporter.getFirstSpan().getTimestamp()).isCloseTo(epochMicros, offset(MINUTES.toMicros(1)));
    }

    @Test
    void testFinishTwice() {
        final Span span = apmTracer.buildSpan("test").withStartTimestamp(0).start();

        span.finish();

        try {
            span.finish();
            fail("Expected an assertion error. Make sure to enable assertions (-ea) when executing the tests.");
        } catch (AssertionError ignore) {
        }

        assertThat(reporter.getTransactions()).hasSize(1);
    }

    @Test
    void testOperationsAfterFinish() {
        final Span span = apmTracer.buildSpan("test").start();

        span.finish();

        assertThat(reporter.getTransactions()).hasSize(1);

        // subsequent calls have undefined behavior but should not throw exceptions
        span.setOperationName("");
        span.setTag("foo", "bar");
        span.setBaggageItem("foo", "bar");
        span.getBaggageItem("foo");
        span.log("foo");
    }

    @Test
    void testContextAvailableAfterFinish() {
        final Span span = apmTracer.buildSpan("transaction").start();
        span.finish();
        assertThat(reporter.getTransactions()).hasSize(1);
        final Transaction transaction = reporter.getFirstTransaction();
        String transactionId = transaction.getTraceContext().getId().toString();
        transaction.resetState();

        final Span childSpan = apmTracer.buildSpan("span")
            .asChildOf(span.context())
            .start();
        childSpan.finish();

        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getFirstSpan().getTraceContext().getParentId().toString()).isEqualTo(transactionId);
    }

    @Test
    void testScopeAfterFinish() {
        final Span span = apmTracer.buildSpan("transaction").start();
        span.finish();
        assertThat(reporter.getTransactions()).hasSize(1);
        final Transaction transaction = reporter.getFirstTransaction();
        String transactionId = transaction.getTraceContext().getId().toString();
        transaction.resetState();

        try (Scope scope = apmTracer.activateSpan(span)) {
            final Span childSpan = apmTracer.buildSpan("span")
                .start();
            childSpan.finish();
        }

        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getFirstSpan().getTraceContext().getParentId().toString()).isEqualTo(transactionId);
    }

    @Test
    void testCreateNonActiveTransactionNestedTransaction() {
        final Span transaction = apmTracer.buildSpan("transaction").start();
        final Span nested = apmTracer.buildSpan("nestedTransaction").start();
        nested.finish();
        transaction.finish();
        assertThat(reporter.getTransactions()).hasSize(2);
    }

    @Test
    void testCreateNonActiveTransactionAsChildOf() {
        final Span transaction = apmTracer.buildSpan("transaction").start();
        Span span = apmTracer.buildSpan("nestedSpan").asChildOf(transaction).start();
        apmTracer.activateSpan(span).close();
        span.finish();
        transaction.finish();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getFirstSpan().getTraceContext().getTransactionId().isEmpty()).isFalse();
    }

    @Test
    void testCreateActiveTransaction() {
        Span span = apmTracer.buildSpan("test").withStartTimestamp(0).start();
        final ApmScope scope = (ApmScope) apmTracer.activateSpan(span);

        assertThat(apmTracer.activeSpan()).isNotNull();
        assertThat(apmTracer.activeSpan()).isEqualTo(span);
        assertThat(apmTracer.scopeManager().activeSpan()).isEqualTo(span);

        // close scope, but not finish span
        scope.close();
        assertThat(apmTracer.activeSpan()).isNull();
        assertThat(reporter.getTransactions()).hasSize(0);

        // manually finish span
        scope.span().finish(1);
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getDuration()).isEqualTo(1);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("test");
    }

    @Test
    void testLegacyCreateActiveTransactionAndSpans() {
        try (Scope transaction = ((ApmSpanBuilder)apmTracer.buildSpan("transaction")).startActive(true)) {
            try (Scope span = ((ApmSpanBuilder)apmTracer.buildSpan("span")).startActive(true)) {
                try (Scope nestedSpan = ((ApmSpanBuilder)apmTracer.buildSpan("nestedSpan")).startActive(true)) {
                }
            }
        }

        assertThat(reporter.getTransactions()).hasSize(1);
        final Transaction transaction = reporter.getFirstTransaction();
        final co.elastic.apm.agent.impl.transaction.Span span = reporter.getSpans().get(1);
        final co.elastic.apm.agent.impl.transaction.Span nestedSpan = reporter.getSpans().get(0);
        assertThat(transaction.getDuration()).isGreaterThan(0);
        assertThat(transaction.getNameAsString()).isEqualTo("transaction");
        assertThat(reporter.getSpans()).hasSize(2);
        assertThat(span.getNameAsString()).isEqualTo("span");
        assertThat(span.isChildOf(transaction)).isTrue();
        assertThat(nestedSpan.getNameAsString()).isEqualTo("nestedSpan");
        assertThat(nestedSpan.isChildOf(span)).isTrue();
    }

    @Test
    void testCreateActiveTransactionAndSpans() {
        Span otTransaction = apmTracer.buildSpan("transaction").start();
        try (Scope transactionScope = apmTracer.activateSpan(otTransaction)) {
            Span otSpan = apmTracer.buildSpan("span").start();
            try (Scope spanScope = apmTracer.activateSpan(otSpan)) {
                Span otNestedSpan = apmTracer.buildSpan("nestedSpan").start();
                try (Scope nestedSpanScope = apmTracer.activateSpan(otNestedSpan)) {
                }
                otNestedSpan.finish();
            }
            otSpan.finish();
        }
        otTransaction.finish();

        assertThat(reporter.getTransactions()).hasSize(1);
        final Transaction transaction = reporter.getFirstTransaction();
        final co.elastic.apm.agent.impl.transaction.Span span = reporter.getSpans().get(1);
        final co.elastic.apm.agent.impl.transaction.Span nestedSpan = reporter.getSpans().get(0);
        assertThat(transaction.getDuration()).isGreaterThan(0);
        assertThat(transaction.getNameAsString()).isEqualTo("transaction");
        assertThat(reporter.getSpans()).hasSize(2);
        assertThat(span.getNameAsString()).isEqualTo("span");
        assertThat(span.isChildOf(transaction)).isTrue();
        assertThat(nestedSpan.getNameAsString()).isEqualTo("nestedSpan");
        assertThat(nestedSpan.isChildOf(span)).isTrue();
    }

    @Test
    void testResolveClientType() {
        assertSoftly(softly -> {
            softly.assertThat(createSpanFromOtTags(Map.of("span.kind", "client")).getType()).isEqualTo("ext");
            softly.assertThat(createSpanFromOtTags(Map.of("span.kind", "producer")).getType()).isEqualTo("ext");
            softly.assertThat(createSpanFromOtTags(Map.of("span.kind", "client", "db.type", "mysql")).getType()).isEqualTo("db");
            softly.assertThat(createSpanFromOtTags(Map.of("span.kind", "client", "db.type", "foo")).getType()).isEqualTo("db");
            softly.assertThat(createSpanFromOtTags(Map.of("span.kind", "client", "db.type", "redis")).getType()).isEqualTo("cache");
        });
    }

    @Test
    void testTypeTags() {
        // New span model with type, subtype and action
        co.elastic.apm.agent.impl.transaction.Span span = createSpanFromOtTags(Map.of(
            "type", "template",
            "subtype", "jsf",
            "action", "render"));
        assertThat(span.getType()).isEqualTo("template");
        assertThat(span.getSubtype()).isEqualTo("jsf");
        assertThat(span.getAction()).isEqualTo("render");

        // legacy hierarchical typing with new span model
        span = createSpanFromOtTags(Map.of("type", "db.oracle.query"));
        assertThat(span.getType()).isEqualTo("db");
        assertThat(span.getSubtype()).isEqualTo("oracle");
        assertThat(span.getAction()).isEqualTo("query");
    }

    @Test
    void testResolveServerType() {
        assertSoftly(softly -> {
            softly.assertThat(createTransactionFromOtTags(Map.of("span.kind", "server")).getType()).isEqualTo("unknown");
            softly.assertThat(createTransactionFromOtTags(Map.of("span.kind", "server",
                "http.url", "http://localhost:8080",
                "http.method", "GET")).getType()).isEqualTo("request");
        });
    }

    @Test
    void testCreatingClientTransactionCreatesNoopSpan() {
        assertThat(apmTracer.scopeManager().activeSpan()).isNull();
        Span transaction = apmTracer.buildSpan("transaction").withTag("span.kind", "client").start();
        try (ApmScope transactionScope = (ApmScope) apmTracer.activateSpan(transaction)) {
            Span span = apmTracer.buildSpan("span").start();
            try (ApmScope spanScope = (ApmScope) apmTracer.activateSpan(span)) {
                assertThat(apmTracer.scopeManager().activeSpan()).isEqualTo(span);
                Span nestedSpan = apmTracer.buildSpan("nestedSpan").start();
                try (ApmScope nestedSpanScope = (ApmScope) apmTracer.activateSpan(nestedSpan)) {
                    assertThat(apmTracer.scopeManager().activeSpan()).isEqualTo(nestedSpan);
                }
                assertThat(apmTracer.scopeManager().activeSpan()).isEqualTo(span);
            }
            assertThat(apmTracer.scopeManager().activeSpan()).isEqualTo(transaction);
        }
        assertThat(apmTracer.scopeManager().activeSpan()).isNull();
        assertThat(reporter.getSpans()).isEmpty();
        assertThat(reporter.getTransactions()).isEmpty();
    }

    @Test
    void testErrorLogging() {
        Span span = apmTracer.buildSpan("someWork").start();
        try (Scope scope = apmTracer.activateSpan(span)) {
            throw new RuntimeException("Catch me if you can");
        } catch (Exception ex) {
            Tags.ERROR.set(span, true);
            span.log(Map.of(Fields.EVENT, "error", Fields.ERROR_OBJECT, ex, Fields.MESSAGE, ex.getMessage()));
        } finally {
            span.finish();
        }
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getErrors()).hasSize(1);
        assertThat(reporter.getFirstError().getException()).isNotNull();
        assertThat(reporter.getFirstError().getException().getMessage()).isEqualTo("Catch me if you can");
        assertThat(reporter.getFirstError().getTraceContext().getParentId()).isEqualTo(reporter.getFirstTransaction().getTraceContext().getId());
    }

    @Test
    void testErrorLoggingWithoutScope() {
        Span span = apmTracer.buildSpan("someWork").start();
        try {
            throw new RuntimeException("Catch me if you can");
        } catch (Exception ex) {
            Tags.ERROR.set(span, true);
            span.log(Map.of(Fields.EVENT, "error", Fields.ERROR_OBJECT, ex, Fields.MESSAGE, ex.getMessage()));
        } finally {
            span.finish();
        }
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getErrors()).hasSize(1);
        assertThat(reporter.getFirstError().getException()).isNotNull();
        assertThat(reporter.getFirstError().getException().getMessage()).isEqualTo("Catch me if you can");
        assertThat(reporter.getFirstError().getTraceContext().getParentId()).isEqualTo(reporter.getFirstTransaction().getTraceContext().getId());
    }

    @Test
    void testNonStringTags() {
        Span transaction = apmTracer.buildSpan("transaction")
            .withTag("number", 1)
            .withTag("boolean", true)
            .start();
        try (Scope transactionScope = apmTracer.activateSpan(transaction)) {
        }
        transaction.finish();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getContext().getLabel("number")).isEqualTo(1);
        assertThat(reporter.getFirstTransaction().getContext().getLabel("boolean")).isEqualTo(true);

    }

    @Test
    void testManualSampling() {
        Span transaction = apmTracer.buildSpan("transaction")
            .withTag("sampling.priority", 0)
            .withTag("foo", "bar")
            .start();
        try (Scope transactionScope = apmTracer.activateSpan(transaction)) {
            Span span = apmTracer.buildSpan("span").start();
            try (Scope spanScope = apmTracer.activateSpan(span)) {
            }
            span.finish();
        }
        transaction.finish();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getContext().getLabel("foo")).isNull();
        assertThat(reporter.getSpans()).isEmpty();
    }

    @Test
    void testToId() {
        Span otTransaction = apmTracer.buildSpan("transaction").start();
        Span otSpan;
        try (ApmScope transactionScope = (ApmScope) apmTracer.activateSpan(otTransaction)) {
            otSpan = apmTracer.buildSpan("span").start();
            otSpan.finish();
        }
        otTransaction.finish();
        Transaction transaction = reporter.getFirstTransaction();
        assertThat(transaction).isNotNull();
        assertThat(otTransaction.context().toSpanId()).isEqualTo(transaction.getTraceContext().getId().toString());
        assertThat(otTransaction.context().toTraceId()).isEqualTo(transaction.getTraceContext().getTraceId().toString());
        co.elastic.apm.agent.impl.transaction.Span span = reporter.getFirstSpan();
        assertThat(span).isNotNull();
        assertThat(otSpan.context().toSpanId()).isEqualTo(span.getTraceContext().getId().toString());
        assertThat(otSpan.context().toTraceId()).isEqualTo(span.getTraceContext().getTraceId().toString());
    }

    @Test
    void testToIdOfExtractedContext() {
        final String traceIdString = "0af7651916cd43dd8448eb211c80319c";
        final String parentIdString = "b9c7c989f97918e1";

        // --------------------------------------------------------
        final Id traceId = Id.new128BitId();
        traceId.fromHexString(traceIdString, 0);
        assertThat(traceId.toString()).isEqualTo(traceIdString);
        // --------------------------------------------------------
        final Id spanId = Id.new64BitId();
        spanId.fromHexString(parentIdString, 0);
        assertThat(spanId.toString()).isEqualTo(parentIdString);
        // --------------------------------------------------------

        TextMap textMapExtractAdapter = new TextMapAdapter(Map.of(
            TraceContext.TRACE_PARENT_HEADER,
            "00-" + traceIdString + "-" + parentIdString + "-01",
            "User-Agent", "curl"));
        //ExternalProcessSpanContext
        SpanContext spanContext = apmTracer.extract(Format.Builtin.TEXT_MAP, textMapExtractAdapter);

        assertThat(spanContext).isNotNull();
        assertThat(spanContext.toTraceId()).isEqualTo(traceIdString);
        assertThat(spanContext.toSpanId()).isEqualTo(parentIdString);
    }

    @Test
    void testInjectExtract() {
        final String traceId = "0af7651916cd43dd8448eb211c80319c";
        final String parentId = "b9c7c989f97918e1";

        Span otSpan = apmTracer.buildSpan("span")
            .asChildOf(apmTracer.extract(Format.Builtin.TEXT_MAP,
                new TextMapAdapter(Map.of(
                    TraceContext.TRACE_PARENT_HEADER, "00-" + traceId + "-" + parentId + "-01",
                    "User-Agent", "curl"))))
            .start();
        final Scope scope = apmTracer.activateSpan(otSpan);
        Transaction transaction = tracer.currentTransaction();
        assertThat(transaction).isNotNull();
        assertThat(transaction.isSampled()).isTrue();
        assertThat(transaction.getTraceContext().getTraceId().toString()).isEqualTo(traceId);
        assertThat(transaction.getTraceContext().getParentId().toString()).isEqualTo(parentId);
        Span span = apmTracer.activeSpan();
        assertThat(span).isNotNull();
        assertThat(span.getBaggageItem("User-Agent")).isNull();

        final HashMap<String, String> map = new HashMap<>();
        apmTracer.inject(otSpan.context(), Format.Builtin.TEXT_MAP, new TextMapAdapter(map));
        final TraceContext injectedContext = TraceContext.with64BitId(tracer);
        assertThat(injectedContext.asChildOf(map.get(TraceContext.TRACE_PARENT_HEADER))).isTrue();
        assertThat(injectedContext.getTraceId().toString()).isEqualTo(traceId);
        assertThat(injectedContext.getParentId()).isEqualTo(transaction.getTraceContext().getId());
        assertThat(injectedContext.isSampled()).isTrue();
        assertThat(map.get("User-Agent")).isNull();

        scope.close();
        otSpan.finish();
        assertThat(reporter.getTransactions()).hasSize(1);
    }

    @Test
    void testAsChildOfSpanContextNull() {
        apmTracer.buildSpan("span")
            .asChildOf((SpanContext) null)
            .start().finish();
        assertThat(reporter.getTransactions()).hasSize(1);
    }

    @Test
    void testAsChildOfSpanNull() {
        apmTracer.buildSpan("span")
            .asChildOf((Span) null)
            .start().finish();
        assertThat(reporter.getTransactions()).hasSize(1);
    }

    @Test
    void testGetBaggageItem() {
        final Span span = apmTracer.buildSpan("span")
            .start();

        // baggage is not supported yet
        span.setBaggageItem("foo", "bar");
        assertThat(span.getBaggageItem("foo")).isNull();
        span.finish();
    }

    @Test
    void testSpanTags() {
        Span transaction = apmTracer.buildSpan("transaction").start();
        try (Scope transactionScope = apmTracer.activateSpan(transaction)) {
            Span span = apmTracer.buildSpan("span").start();
            try (Scope spanScope = apmTracer.activateSpan(span)) {
                transaction.setTag("foo", "bar");
                span.setTag("bar", "baz");
            }
            span.finish();
        }
        transaction.finish();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getContext().getLabel("foo")).isEqualTo("bar");
        assertThat(reporter.getFirstSpan().getContext().getLabel("bar")).isEqualTo("baz");
    }

    private Transaction createTransactionFromOtTags(Map<String, String> tags) {
        final Tracer.SpanBuilder spanBuilder = apmTracer.buildSpan("transaction");
        tags.forEach(spanBuilder::withTag);
        spanBuilder.start().finish();
        assertThat(reporter.getTransactions()).hasSize(1);
        final Transaction transaction = reporter.getFirstTransaction();
        reporter.reset();
        return transaction;
    }

    private co.elastic.apm.agent.impl.transaction.Span createSpanFromOtTags(Map<String, String> tags) {
        final Span transaction = apmTracer.buildSpan("transaction").start();
        try (Scope transactionScope = apmTracer.activateSpan(transaction)) {
            final Tracer.SpanBuilder spanBuilder = apmTracer.buildSpan("transaction");
            tags.forEach(spanBuilder::withTag);
            spanBuilder.start().finish();
        }
        transaction.finish();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(1);
        final co.elastic.apm.agent.impl.transaction.Span span = reporter.getFirstSpan();
        reporter.reset();
        return span;
    }
}
