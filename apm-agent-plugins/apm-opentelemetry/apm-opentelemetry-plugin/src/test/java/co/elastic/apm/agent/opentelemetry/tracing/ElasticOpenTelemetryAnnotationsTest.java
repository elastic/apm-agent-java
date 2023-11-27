package co.elastic.apm.agent.opentelemetry.tracing;

import co.elastic.apm.agent.impl.transaction.ElasticContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.testutils.assertions.Assertions;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

public class ElasticOpenTelemetryAnnotationsTest extends AbstractOpenTelemetryTest {

    @Before
    public void before() {
        checkNoActiveContext();
    }

    @After
    public void after() {
        checkNoActiveContext();
    }

    @Test
    public void withSpanAnnotationTestWithMethodSignatureSpanName() {
        executeSpanInTransactionAndAssertTransaction((ignore) -> fooSpan());

        co.elastic.apm.agent.impl.transaction.Span firstSpan = reporter.getFirstSpan();
        assertThat(firstSpan.getNameAsString()).isEqualTo("ElasticOpenTelemetryAnnotationsTest#fooSpan");
        assertThat(reporter.getFirstSpan().isChildOf(reporter.getFirstTransaction())).isTrue();
    }

    @Test
    public void withSpanAnnotationTestWithSpanNameFromAnnotation() {
        executeSpanInTransactionAndAssertTransaction((ignore) -> barSpan());

        co.elastic.apm.agent.impl.transaction.Span firstSpan = reporter.getFirstSpan();
        assertThat(firstSpan.getNameAsString()).isEqualTo("barSpan");
        assertThat(reporter.getFirstSpan().isChildOf(reporter.getFirstTransaction())).isTrue();
    }

    @Test
    public void withSpanAnnotationSpanAttributes() {
        executeSpanInTransactionAndAssertTransaction((ignore) -> fooSpanWithAttrs("foobar", "objectAsString", 2073, 2.69));

        co.elastic.apm.agent.impl.transaction.Span firstSpan = reporter.getFirstSpan();
        assertThat(firstSpan.getNameAsString()).isEqualTo("ElasticOpenTelemetryAnnotationsTest#fooSpanWithAttrs");
        assertThat(firstSpan.isChildOf(reporter.getFirstTransaction())).isTrue();
        assertThat(firstSpan.getOtelAttributes().get("attr1")).isEqualTo("foobar");
        assertThat(firstSpan.getOtelAttributes().get("attr2")).isEqualTo("objectAsString");
        assertThat(firstSpan.getOtelAttributes().get("count")).isNull();
    }


    private void executeSpanInTransactionAndAssertTransaction(Consumer<?> function) {
        Span transaction = otelTracer.spanBuilder("transaction")
            .startSpan();
        try (Scope scope = transaction.makeCurrent()) {
            function.accept(null);
        } finally {
            transaction.end();
        }

        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(1);
        Transaction reportedTransaction = reporter.getFirstTransaction();
        assertThat(reportedTransaction.getNameAsString()).isEqualTo("transaction");
    }

    @WithSpan(kind = SpanKind.CLIENT)
    protected void fooSpan() {

    }

    @WithSpan(kind = SpanKind.INTERNAL, value = "barSpan")
    protected void barSpan() {
    }


    @WithSpan
    protected void fooSpanWithAttrs(@SpanAttribute("attr1") String string,
                                    @TestAnnotation @SpanAttribute("attr2") Object object,
                                    @SpanAttribute Integer count,
                                    Double doubleVal) {
    }

    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface TestAnnotation {

    }

    private void checkNoActiveContext() {
        Assertions.assertThat(tracer.currentContext())
            .describedAs("no active elastic context is expected")
            .satisfies(ElasticContext::isEmpty);
        assertThat(Context.current())
            .describedAs("no active otel context is expected")
            .isSameAs(Context.root())
            .isNotNull();
    }
}
