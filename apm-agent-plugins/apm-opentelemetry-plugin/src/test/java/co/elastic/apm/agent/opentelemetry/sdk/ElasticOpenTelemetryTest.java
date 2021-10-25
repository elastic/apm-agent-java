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
package co.elastic.apm.agent.opentelemetry.sdk;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.context.TransactionContext;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.ElasticContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.Nullable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

public class ElasticOpenTelemetryTest extends AbstractInstrumentationTest {

    private OpenTelemetry openTelemetry;
    private Tracer otelTracer;

    @Before
    public void setUp() {
        this.openTelemetry = GlobalOpenTelemetry.get();
        assertThat(openTelemetry).isSameAs(GlobalOpenTelemetry.get());
        otelTracer = openTelemetry.getTracer(null);

        // otel spans are not recycled for now
        disableRecyclingValidation();
    }

    @Before
    public void before() {
        checkNoActiveContext();
    }

    @After
    public void after() {
        checkNoActiveContext();
    }

    @Test
    public void testTransaction() {
        otelTracer.spanBuilder("transaction")
            .startSpan()
            .end();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("transaction");
    }

    @Test
    public void testTransactionWithAttribute() {
        otelTracer.spanBuilder("transaction")
            .setAttribute("boolean", true)
            .setAttribute("long", 42L)
            .setAttribute("double", 73D)
            .setAttribute("string", "hello")
            .startSpan()
            .end();

        assertThat(reporter.getTransactions()).hasSize(1);
        TransactionContext context = reporter.getFirstTransaction().getContext();
        assertThat(context.getLabel("boolean")).isEqualTo(true);
        assertThat(context.getLabel("long")).isEqualTo(42L);
        assertThat(context.getLabel("double")).isEqualTo(73D);
        assertThat(context.getLabel("string")).isEqualTo("hello");
    }

    @Test
    public void testTransactionWithSpanManualPropagation() {
        Span transaction = otelTracer.spanBuilder("transaction")
            .startSpan();
        otelTracer.spanBuilder("span")
            .setParent(Context.root().with(transaction))
            .startSpan()
            .end();
        transaction.end();

        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(1);
        Transaction reportedTransaction = reporter.getFirstTransaction();
        assertThat(reportedTransaction.getNameAsString()).isEqualTo("transaction");

        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("span");
        assertThat(reporter.getFirstSpan().isChildOf(reporter.getFirstTransaction())).isTrue();
    }

    @Test
    public void testTransactionWithSpanContextStorePropagation() {
        Span transaction = otelTracer.spanBuilder("transaction")
            .startSpan();
        try (Scope scope = transaction.makeCurrent()) {
            otelTracer.spanBuilder("span")
                .startSpan()
                .end();
        } finally {
            transaction.end();
        }

        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("transaction");
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("span");
        assertThat(reporter.getFirstSpan().isChildOf(reporter.getFirstTransaction())).isTrue();
    }

    @Test
    public void testStartChildAfterEnd() {
        Span transaction = otelTracer.spanBuilder("transaction")
            .startSpan();
        transaction.end();

        assertThat(reporter.getTransactions()).hasSize(1);
        // simulate reporting the span
        reporter.getFirstTransaction().decrementReferences();

        try (Scope scope = transaction.makeCurrent()) {
            otelTracer.spanBuilder("span")
                .startSpan()
                .end();
        }
        assertThat(reporter.getFirstSpan().isChildOf(reporter.getFirstTransaction())).isTrue();
    }

    @Test
    public void testTransactionWithRemoteParent() {
        Context context = openTelemetry.getPropagators()
            .getTextMapPropagator()
            .extract(Context.current(),
                Map.of("traceparent", "00-cafebabe16cd43dd8448eb211c80319c-deadbeef197918e1-01"),
                new MapGetter());
        otelTracer.spanBuilder("transaction")
            .setParent(context)
            .startSpan()
            .end();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("transaction");
        assertThat(reporter.getFirstTransaction().getTraceContext().getTraceId().toString()).isEqualTo("cafebabe16cd43dd8448eb211c80319c");
        assertThat(reporter.getFirstTransaction().getTraceContext().getParentId().toString()).isEqualTo("deadbeef197918e1");
    }

    @Test
    public void testTransactionInject() {
        Span transaction = otelTracer.spanBuilder("transaction")
            .startSpan();
        HashMap<String, String> otelHeaders = new HashMap<>();
        HashMap<String, String> elasticApmHeaders = new HashMap<>();
        try (Scope scope = transaction.makeCurrent()) {
            openTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), otelHeaders, HashMap::put);
            tracer.getActive().propagateTraceContext(elasticApmHeaders, (k, v, m) -> m.put(k, v));
        } finally {
            transaction.end();
        }
        assertThat(elasticApmHeaders).containsAllEntriesOf(otelHeaders);
    }

    @Test
    public void setStartTimestamp() {

        Instant transactionStart = Instant.now();

        otelTracer.spanBuilder("transaction")
            .setStartTimestamp(transactionStart)
            .startSpan()
            .end();

        long transactionStartMicros = ChronoUnit.MICROS.between(Instant.EPOCH, transactionStart);

        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getTimestamp()).isEqualTo(transactionStartMicros);
    }

    @Test
    public void otelBridgedRootContext() {
        checkBridgedContext(Context.root());

        assertThat(Context.root())
            .describedAs("wrapped root context should be current")
            .isSameAs(Context.current());
    }

    public ElasticContext<?> checkBridgedContext(Context context) {
        assertThat(context).isInstanceOf(ElasticContext.class);

        // we have to check class name as the wrapper class is loaded in the plugin CL and it is also loadable from
        // the current CL, thus making class equality not work as expected
        assertThat(context.getClass().getName())
            .describedAs("root context should be wrapped")
            .doesNotStartWith("io.opentelemetry");

        return (ElasticContext<?>) context;
    }

    @Test
    public void otelContextStoreAndRetrieve() {
        Span span = otelTracer.spanBuilder("span").startSpan();

        checkNoActiveContext();

        ContextKey<String> key1 = ContextKey.named("key1");
        Context context1 = Context.current().with(key1, "value1");
        assertThat(context1.get(key1)).isEqualTo("value1");

        ContextKey<String> key2 = ContextKey.named("key2");

        try (Scope scope1 = context1.makeCurrent()) {
            checkCurrentContext(context1, "first context should be active");
            checkCurrentContextKey(key1, "value1");

            Context context2 = context1.with(key2, "value2");
            try (Scope scope2 = context2.makeCurrent()) {
                checkCurrentContext(context2, "second context should be active");
                checkCurrentContextKey(key1, "value1");
                checkCurrentContextKey(key2, "value2");

                try (Scope spanScope = span.makeCurrent()) {
                    assertThat(Context.current())
                        .describedAs("span context should have its own context")
                        .isNotSameAs(context2)
                        .isNotSameAs(context1);
                    checkCurrentContextKey(key1, "value1");
                    checkCurrentContextKey(key2, "value2");
                } finally {
                    span.end();
                }
                checkCurrentContext(context2, "second context should be restored");
            }

            assertThat(context1.get(key2))
                .describedAs("context should be immutable")
                .isNull();

            checkCurrentContext(context1, "context should be restored");
        }

        checkNoActiveContext();

    }

    private static void checkCurrentContext(Context expected, String assertMsg) {
        assertThat(Context.current())
            .describedAs(assertMsg)
            .isSameAs(expected);

        assertThat(expected)
            .describedAs("otel context should also be an elastic context")
            .isInstanceOf(ElasticContext.class);

        assertThat(tracer.currentContext())
            .describedAs(assertMsg)
            .isSameAs(expected);
    }

    @Test
    public void otelContextRetrieveByKeyReferenceOnly() {
        ContextKey<String> key = ContextKey.named("key");
        Context contextWithKey = Context.current().with(key, "value");
        assertThat(contextWithKey.get(key)).isEqualTo("value");

        String valueWithSameNameKey = contextWithKey.get(ContextKey.named("key"));
        assertThat(valueWithSameNameKey)
            .describedAs("only key reference should allow to get values in context")
            .isNull();
    }

    @Test
    public void otelContextRootIdempotent() {
        assertThat(Context.root())
            .describedAs("multiple calls to Context.root() should return the same value")
            .isSameAs(Context.root());
    }

    @Test
    public void otelContextCurrentIdempotent() {

        // create a non-root context by adding a value
        ContextKey<String> key = ContextKey.named("key");
        Context context = Context.current().with(key, "value");

        try (Scope scope = context.makeCurrent()) {
            assertThat(context).isNotSameAs(Context.root());
            checkCurrentContext(context, "multiple calls to Context.current() should return the same value");
            checkCurrentContextKey(key, "value");
        }

        assertThat(Context.current().get(key)).isNull();
    }

    @Test
    public void otelContextMakeCurrentMoreThanOnce() {
        ContextKey<String> key = ContextKey.named("key");
        Context context = Context.current().with(key, "value");

        try (Scope scope1 = context.makeCurrent()) {
            assertThat(scope1).isNotSameAs(Scope.noop());
            checkCurrentContext(context, "first activation should activate context");

            // here the context is expected to remain the same as it is not modified (we don't add any value to it)
            try (Scope scope2 = context.makeCurrent()) {
                checkCurrentContext(context, "double activation should keep the same context");
                assertThat(scope2)
                    .describedAs("nested scope should be noop as context remains the same")
                    .isSameAs(Scope.noop());
            }
        }
    }

    @Test
    public void contextActivationFromElastic() {
        ContextKey<String> key = ContextKey.named("key");

        Context context = Context.root().with(key, "value");
        ElasticContext<?> bridgedContext = checkBridgedContext(context);

        // activate context from elastic API using a bridged context
        try (co.elastic.apm.agent.impl.Scope scope = bridgedContext.activateInScope()) {

            checkCurrentContext(context, "elastic and otel contexts should be the same");

            checkCurrentContextKey(key, "value");

        }
    }


    @Test
    public void contextActivationFromOtel() {

        ContextKey<String> key = ContextKey.named("key");
        Context context = Context.root().with(key, "value");

        checkBridgedContext(context);

        // activate context from Otel API
        try (Scope scope = context.makeCurrent()) {

            checkCurrentContext(context, "elastic and otel contexts should be the same");

            checkCurrentContextKey(key, "value");
        }

    }

    private void checkCurrentContextKey(ContextKey<String> key, String expectedValue) {
        Context current = Context.current();
        assertThat(current.get(key))
            .describedAs("context %s should contain %s=%s", current, key, expectedValue)
            .isEqualTo(expectedValue);
    }

    private void checkNoActiveContext() {
        assertThat(tracer.currentContext())
            .describedAs("no active elastic context is expected")
            .isNull();
        assertThat(Context.current())
            .describedAs("no active otel context is expected")
            .isSameAs(Context.root())
            .isNotNull();
    }

    @Test
    public void otelStateWithActiveElasticTransaction() {

        Transaction transaction = startTestRootTransaction();

        try {
            assertThat(tracer.currentContext()).isSameAs(transaction);

            assertThat(Context.current())
                .describedAs("current otel context should have elastic span as active")
                .isNotSameAs(Context.root());

            assertThat(Span.current())
                .describedAs("elastic span should appear visible in current context")
                .isNotNull();

            assertThat(tracer.currentContext())
                .describedAs("current context should have been upgraded to otel context")
                .isNotNull()
                .isNotSameAs(transaction);

            assertThat(tracer.currentTransaction())
                .isSameAs(tracer.currentContext().getSpan())
                .isSameAs(transaction);
        } finally {
            // this must transparently deactivate the upgraded context
            transaction.deactivate().end();
        }
    }

    @Test
    public void otelSpanOverActiveElasticTransaction() {
        Transaction transaction = startTestRootTransaction();

        String spanId;
        try {

            Span otelSpan = openTelemetry.getTracer("test")
                .spanBuilder("otel span")
                .startSpan();

            try (Scope scope = otelSpan.makeCurrent()) {
                spanId = Span.current().getSpanContext().getSpanId();
            }

            otelSpan.end();

        } finally {
            transaction.deactivate().end();
        }

        assertThat(reporter.getNumReportedTransactions()).isEqualTo(1);
        assertThat(reporter.getFirstTransaction()).isSameAs(transaction);

        assertThat(reporter.getNumReportedSpans()).isEqualTo(1);
        AbstractSpan<?> reportedSpan = reporter.getFirstSpan().getSpan();
        assertThat(reportedSpan).isNotNull();
        assertThat(reportedSpan.getNameAsString()).isEqualTo("otel span");
        assertThat(reportedSpan.getTraceContext().getId().toString()).isEqualTo(spanId);
        assertThat(reportedSpan.getTraceContext().isChildOf(transaction.getTraceContext())).isTrue();
    }

    @Test
    public void elasticSpanOverOtelSpan() {
        // create and activate an otel span which should create a transaction
        // create and activate an elastic span

        Span otelSpan = openTelemetry.getTracer("test")
            .spanBuilder("otel transaction")
            .startSpan();

        Transaction transaction;
        try (Scope scope = otelSpan.makeCurrent()) {

            transaction = tracer.currentTransaction();
            assertThat(transaction).isNotNull();

            co.elastic.apm.agent.impl.transaction.Span elasticSpan = transaction.createSpan();
            try (co.elastic.apm.agent.impl.Scope elasticScope = elasticSpan.activateInScope()) {
                assertThat(tracer.getActiveSpan()).isNotNull();
                tracer.getActiveSpan().withName("elastic span");
            } finally {
                elasticSpan.end();
            }
        } finally {
            otelSpan.end();
        }

        assertThat(reporter.getNumReportedTransactions()).isEqualTo(1);
        assertThat(reporter.getFirstTransaction()).isSameAs(transaction);
        assertThat(transaction.getNameAsString()).isEqualTo("otel transaction");

        assertThat(reporter.getNumReportedSpans()).isEqualTo(1);
        AbstractSpan<?> reportedSpan = reporter.getFirstSpan().getSpan();
        assertThat(reportedSpan).isNotNull();
        assertThat(reportedSpan.getNameAsString()).isEqualTo("elastic span");
        assertThat(reportedSpan.getTraceContext().isChildOf(transaction.getTraceContext())).isTrue();
    }

    @Test
    public void testTransactionSemanticConventionMappingHttpHost() {
        otelTracer.spanBuilder("transaction")
            .startSpan()
            .setAttribute(SemanticAttributes.HTTP_METHOD, "GET")
            .setAttribute(SemanticAttributes.HTTP_SCHEME, "http")
            .setAttribute(SemanticAttributes.HTTP_HOST, "www.example.com:8080")
            .setAttribute(SemanticAttributes.HTTP_TARGET, "/foo?bar")
            .setAttribute(SemanticAttributes.HTTP_STATUS_CODE, 200L)
            .end();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getResult()).isEqualTo("HTTP 2xx");
        assertThat(reporter.getFirstTransaction().getContext().getResponse().getStatusCode()).isEqualTo(200);
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getMethod()).isEqualTo("GET");
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getUrl().getProtocol()).isEqualTo("http");
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getUrl().getHostname()).isEqualTo("www.example.com");
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getUrl().getPort()).isEqualTo(8080);
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getUrl().getFull().toString()).isEqualTo("http://www.example.com:8080/foo?bar");
    }

    @Test
    public void testTransactionSemanticConventionMappingHttpNetHostName() {
        otelTracer.spanBuilder("transaction")
            .startSpan()
            .setAttribute(SemanticAttributes.HTTP_METHOD, "GET")
            .setAttribute(SemanticAttributes.HTTP_SCHEME, "http")
            .setAttribute(SemanticAttributes.NET_HOST_NAME, "example.com")
            .setAttribute(SemanticAttributes.NET_HOST_PORT, 8080)
            .setAttribute(SemanticAttributes.NET_PEER_IP, "192.168.178.1")
            .setAttribute(SemanticAttributes.NET_PEER_PORT, 123456)
            .setAttribute(SemanticAttributes.HTTP_TARGET, "/foo?bar")
            .setAttribute(SemanticAttributes.HTTP_STATUS_CODE, 200L)
            .end();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getResult()).isEqualTo("HTTP 2xx");
        assertThat(reporter.getFirstTransaction().getContext().getResponse().getStatusCode()).isEqualTo(200);
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getMethod()).isEqualTo("GET");
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getUrl().getProtocol()).isEqualTo("http");
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getUrl().getHostname()).isEqualTo("example.com");
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getUrl().getPort()).isEqualTo(8080);
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getSocket().getRemoteAddress()).isEqualTo("192.168.178.1:123456");
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getUrl().getFull().toString()).isEqualTo("http://example.com:8080/foo?bar");
    }

    @Test
    public void testTransactionSemanticConventionMappingHttpNetHostIP() {
        otelTracer.spanBuilder("transaction")
            .startSpan()
            .setAttribute(SemanticAttributes.HTTP_METHOD, "GET")
            .setAttribute(SemanticAttributes.HTTP_SCHEME, "http")
            .setAttribute(SemanticAttributes.NET_HOST_NAME, "127.0.0.1")
            .setAttribute(SemanticAttributes.NET_HOST_PORT, 8080)
            .setAttribute(SemanticAttributes.NET_PEER_PORT, 123456)
            .setAttribute(SemanticAttributes.NET_PEER_IP, "192.168.178.1")
            .setAttribute(SemanticAttributes.HTTP_TARGET, "/foo?bar")
            .setAttribute(SemanticAttributes.HTTP_STATUS_CODE, 200L)
            .end();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getResult()).isEqualTo("HTTP 2xx");
        assertThat(reporter.getFirstTransaction().getContext().getResponse().getStatusCode()).isEqualTo(200);
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getMethod()).isEqualTo("GET");
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getUrl().getProtocol()).isEqualTo("http");
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getUrl().getHostname()).isEqualTo("127.0.0.1");
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getUrl().getPort()).isEqualTo(8080);
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getSocket().getRemoteAddress()).isEqualTo("192.168.178.1:123456");
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getUrl().getFull().toString()).isEqualTo("http://127.0.0.1:8080/foo?bar");
    }

    @Test
    public void testTransactionSemanticConventionMappingHttpUrl() {
        otelTracer.spanBuilder("transaction")
            .startSpan()
            .setAttribute(SemanticAttributes.HTTP_METHOD, "GET")
            .setAttribute(SemanticAttributes.HTTP_URL, "http://example.com:8080/foo?bar")
            .setAttribute(SemanticAttributes.HTTP_STATUS_CODE, 200L)
            .setAttribute(SemanticAttributes.NET_PEER_PORT, 123456)
            .setAttribute(SemanticAttributes.NET_PEER_IP, "192.168.178.1")
            .end();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getResult()).isEqualTo("HTTP 2xx");
        assertThat(reporter.getFirstTransaction().getContext().getResponse().getStatusCode()).isEqualTo(200);
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getMethod()).isEqualTo("GET");
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getUrl().getProtocol()).isEqualTo("http");
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getUrl().getHostname()).isEqualTo("example.com");
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getUrl().getPort()).isEqualTo(8080);
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getSocket().getRemoteAddress()).isEqualTo("192.168.178.1:123456");
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getUrl().getFull().toString()).isEqualTo("http://example.com:8080/foo?bar");
    }

    @Test
    public void testSpanSemanticConventionMappingHttpUrl() {
        testSpanSemanticConventionMappingHttpHelper(span -> span.setAttribute(SemanticAttributes.HTTP_URL, "http://example.com/foo?bar"));
    }

    @Test
    public void testSpanSemanticConventionMappingHttpHost() {
        testSpanSemanticConventionMappingHttpHelper(span -> {
            span.setAttribute(SemanticAttributes.HTTP_SCHEME, "http");
            span.setAttribute(SemanticAttributes.HTTP_HOST, "example.com");
            span.setAttribute(SemanticAttributes.HTTP_TARGET, "/foo?bar");
        });
    }

    @Test
    public void testSpanSemanticConventionMappingHttpPeerName() {
        testSpanSemanticConventionMappingHttpHelper(span -> {
            span.setAttribute(SemanticAttributes.HTTP_SCHEME, "http");
            span.setAttribute(SemanticAttributes.NET_PEER_IP, "192.0.2.5");
            span.setAttribute(SemanticAttributes.NET_PEER_NAME, "example.com");
            span.setAttribute(SemanticAttributes.NET_PEER_PORT, 80);
            span.setAttribute(SemanticAttributes.HTTP_TARGET, "/foo?bar");
        });
    }

    @Test
    public void testSpanSemanticConventionMappingHttpPeerIp() {
        testSpanSemanticConventionMappingHttpHelper(span -> {
            span.setAttribute(SemanticAttributes.HTTP_SCHEME, "http");
            span.setAttribute(SemanticAttributes.NET_PEER_IP, "example.com");
            span.setAttribute(SemanticAttributes.NET_PEER_PORT, 80);
            span.setAttribute(SemanticAttributes.HTTP_TARGET, "/foo?bar");
        });
    }

    public void testSpanSemanticConventionMappingHttpHelper(Consumer<Span> spanConsumer) {
        Span transaction = otelTracer.spanBuilder("transaction")
            .startSpan();
        try (Scope scope = transaction.makeCurrent()) {
            Span span = otelTracer.spanBuilder("span")
                .startSpan();
            spanConsumer.accept(span);
            span.end();
        } finally {
            transaction.end();
        }

        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getFirstSpan().getContext().getDestination().getPort()).isEqualTo(80);
        assertThat(reporter.getFirstSpan().getContext().getHttp().getUrl().toString()).isIn("http://example.com/foo?bar", "http://example.com:80/foo?bar");
        assertThat(reporter.getFirstSpan().getContext().getDestination().getAddress().toString()).isEqualTo("example.com");
        assertThat(reporter.getFirstSpan().getContext().getDestination().getService().getName().toString()).isEqualTo("http://example.com");
        assertThat(reporter.getFirstSpan().getContext().getDestination().getService().getResource().toString()).isEqualTo("example.com:80");
        reporter.resetWithoutRecycling();
    }

    public static class MapGetter implements TextMapGetter<Map<String, String>> {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Nullable
        @Override
        public String get(@Nullable Map<String, String> carrier, String key) {
            return carrier.get(key);
        }
    }
}
