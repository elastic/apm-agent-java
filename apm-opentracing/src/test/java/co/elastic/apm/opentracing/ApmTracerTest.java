package co.elastic.apm.opentracing;

import co.elastic.apm.MockReporter;
import co.elastic.apm.configuration.SpyConfiguration;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.transaction.Transaction;
import io.opentracing.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ApmTracerTest {

    private ApmTracer apmTracer;
    private MockReporter reporter;

    @BeforeEach
    void setUp() {
        reporter = new MockReporter();
        final ElasticApmTracer elasticApmTracer = ElasticApmTracer.builder()
            .configurationRegistry(SpyConfiguration.createSpyConfig())
            .reporter(reporter)
            .build();
        apmTracer = new ApmTracer(elasticApmTracer, new ApmScopeManager(elasticApmTracer));
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
}
