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
package co.elastic.apm.agent.opentelemetry.sdk;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.context.TransactionContext;
import co.elastic.apm.agent.opentelemetry.context.OTelContextStorage;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import javax.annotation.Nullable;
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
        disableRecyclingValidation();
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
            .setAttribute("string", "hello")
            .startSpan()
            .end();

        assertThat(reporter.getTransactions()).hasSize(1);
        TransactionContext context = reporter.getFirstTransaction().getContext();
        assertThat(context.getLabel("boolean")).isEqualTo(true);
        assertThat(context.getLabel("long")).isEqualTo(42L);
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
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("transaction");
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

    /**
     * Demonstrates a missing feature of this bridge: custom context entries are not propagated
     *
     * @see OTelContextStorage#current()
     */
    @Test
    @Ignore
    public void testPropagateCustomContextKey() {
        Span transaction = otelTracer.spanBuilder("transaction")
            .startSpan();
        Context context = Context.current()
            .with(transaction)
            .with(ContextKey.named("foo"), "bar");
        try (Scope scope = context.makeCurrent()) {
            assertThat(tracer.getActive().getTraceContext().getId().toString()).isEqualTo(transaction.getSpanContext().getSpanId());
            // this assertion fails as context keys are not propagated
            assertThat(Context.current().get(ContextKey.<String>named("foo"))).isEqualTo("bar");
            Span.current().setAttribute("foo", "bar");
        } finally {
            transaction.end();
        }

        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("transaction");
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
        assertThat(reporter.getFirstSpan().getContext().getHttp().getFullUrl()).isIn("http://example.com/foo?bar", "http://example.com:80/foo?bar");
        assertThat(reporter.getFirstSpan().getContext().getDestination().getAddress().toString()).isEqualTo("example.com");
        assertThat(reporter.getFirstSpan().getContext().getDestination().getService().getName().toString()).isEqualTo("http://example.com");
        assertThat(reporter.getFirstSpan().getContext().getDestination().getService().getResource().toString()).isEqualTo("example.com:80");
        assertThat(reporter.getFirstSpan().getContext().getDestination().getService().getType()).isEqualTo("external");
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
