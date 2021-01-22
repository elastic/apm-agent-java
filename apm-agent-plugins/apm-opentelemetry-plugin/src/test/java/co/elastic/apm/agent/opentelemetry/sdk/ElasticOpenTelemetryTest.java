package co.elastic.apm.agent.opentelemetry.sdk;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.context.TransactionContext;
import co.elastic.apm.agent.opentelemetry.context.ElasticOTelContextStorage;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticOpenTelemetryTest extends AbstractInstrumentationTest {

    private OpenTelemetry openTelemetry;
    private Tracer otelTracer;

    @BeforeEach
    void setUp() {
        this.openTelemetry = GlobalOpenTelemetry.get();
        assertThat(openTelemetry).isSameAs(GlobalOpenTelemetry.get());
        otelTracer = openTelemetry.getTracer(null);
    }

    @Test
    void testTransaction() {
        otelTracer.spanBuilder("transaction")
            .startSpan()
            .end();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("transaction");
    }

    @Test
    void testTransactionStatusCode() {
        otelTracer.spanBuilder("transaction")
            .setAttribute(SemanticAttributes.HTTP_STATUS_CODE, 200L)
            .startSpan()
            .end();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getResult()).isEqualTo("HTTP 2xx");
        assertThat(reporter.getFirstTransaction().getContext().getResponse().getStatusCode()).isEqualTo(200);
    }

    @Test
    void testTransactionWithAttribute() {
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
    void testTransactionWithSpanManualPropagation() {
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
    void testTransactionWithSpanContextStorePropagation() {
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

    /**
     * Demonstrates a missing feature of this bridge: custom context entries are not propagated
     *
     * @see ElasticOTelContextStorage#current()
     */
    @Test
    @Disabled
    void testPropagateCustomContextKey() {
        Span transaction = otelTracer.spanBuilder("transaction")
            .startSpan();
        Context context = Context.current()
            .with(transaction)
            .with(ContextKey.named("foo"), "bar");
        try (Scope scope = context.makeCurrent()) {
            assertThat(tracer.getActive().getTraceContext().getId().toString()).isEqualTo(transaction.getSpanContext().getSpanIdAsHexString());
            // this assertion fails as context keys are not propagated
            assertThat(Context.current().get(ContextKey.<String>named("foo"))).isEqualTo("bar");
        } finally {
            transaction.end();
        }

        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("transaction");
    }

    @Test
    void testTransactionWithRemoteParent() {
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
    void testTransactionInject() {
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

    private static class MapGetter implements TextMapPropagator.Getter<Map<String, String>> {
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
