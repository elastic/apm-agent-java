/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
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
package co.elastic.apm.opentracing;

import co.elastic.apm.MockReporter;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.transaction.Transaction;
import io.opentracing.Scope;
import io.opentracing.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class ApmTracerTest {

    private ApmTracer apmTracer;
    private MockReporter reporter;

    @BeforeEach
    void setUp() {
        reporter = new MockReporter();
        final ElasticApmTracer elasticApmTracer = ElasticApmTracer.builder()
            .withConfig("service_name", "elastic-apm-test")
            .reporter(reporter)
            .build();
        apmTracer = new ApmTracer(elasticApmTracer);
    }

    @Test
    void testCreateNonActiveTransaction() {
        final Span span = apmTracer.buildSpan("test").withStartTimestamp(0).start();

        assertThat(apmTracer.activeSpan()).isNull();
        assertThat(apmTracer.scopeManager().active()).isNull();

        span.finish(TimeUnit.MILLISECONDS.toMicros(1));

        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getDuration()).isEqualTo(1);
        assertThat(reporter.getFirstTransaction().getName().toString()).isEqualTo("test");
    }

    @Test
    void testCreateActiveTransaction() {
        final ApmScope scope = apmTracer.buildSpan("test").withStartTimestamp(0).startActive(false);

        assertThat(apmTracer.activeSpan()).isNotNull();
        assertThat(apmTracer.activeSpan().getTransaction()).isSameAs(scope.span().getTransaction());
        assertThat(apmTracer.scopeManager().active().span().getTransaction()).isSameAs(scope.span().getTransaction());

        // close scope, but not finish span
        scope.close();
        assertThat(apmTracer.activeSpan()).isNull();
        assertThat(reporter.getTransactions()).hasSize(0);

        // manually finish span
        scope.span().finish(TimeUnit.MILLISECONDS.toMicros(1));
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getDuration()).isEqualTo(1);
        assertThat(reporter.getFirstTransaction().getName().toString()).isEqualTo("test");
    }

    @Test
    void testCreateActiveTransactionAndSpans() {
        try (ApmScope transaction = apmTracer.buildSpan("transaction").startActive(true)) {
            try (ApmScope span = apmTracer.buildSpan("span").startActive(true)) {
                try (ApmScope nestedSpan = apmTracer.buildSpan("nestedSpan").startActive(true)) {
                }
            }
        }

        assertThat(reporter.getTransactions()).hasSize(1);
        final Transaction transaction = reporter.getFirstTransaction();
        final co.elastic.apm.impl.transaction.Span span = transaction.getSpans().get(0);
        final co.elastic.apm.impl.transaction.Span nestedSpan = transaction.getSpans().get(1);
        assertThat(transaction.getDuration()).isGreaterThan(0);
        assertThat(transaction.getName().toString()).isEqualTo("transaction");
        assertThat(transaction.getSpans()).hasSize(2);
        assertThat(span.getName()).isEqualTo("span");
        assertThat(span.getParent().asLong()).isEqualTo(0);
        assertThat(nestedSpan.getName()).isEqualTo("nestedSpan");
        assertThat(nestedSpan.getParent()).isEqualTo(span.getId());
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
        try (Scope transaction = apmTracer.buildSpan("transaction").withTag("span.kind", "client").startActive(true)) {
            try (Scope span = apmTracer.buildSpan("span").startActive(true)) {
            }
        }
        assertThat(reporter.getTransactions()).isEmpty();
    }

    Transaction createTransactionFromOtTags(Map<String, String> tags) {
        final ApmSpanBuilder spanBuilder = apmTracer.buildSpan("transaction");
        tags.forEach(spanBuilder::withTag);
        spanBuilder.start().finish();
        assertThat(reporter.getTransactions()).hasSize(1);
        final Transaction transaction = reporter.getFirstTransaction();
        reporter.reset();
        return transaction;
    }

    co.elastic.apm.impl.transaction.Span createSpanFromOtTags(Map<String, String> tags) {
        final ApmSpanBuilder transactionSpanBuilder = apmTracer.buildSpan("transaction");
        try (Scope transaction = transactionSpanBuilder.startActive(true)) {
            final ApmSpanBuilder spanBuilder = apmTracer.buildSpan("transaction");
            tags.forEach(spanBuilder::withTag);
            spanBuilder.start().finish();
        }
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getSpans()).hasSize(1);
        final co.elastic.apm.impl.transaction.Span span = reporter.getFirstTransaction().getSpans().get(0);
        reporter.reset();
        return span;
    }
}
