package co.elastic.apm.plugin.api;

import co.elastic.apm.AbstractInstrumentationTest;
import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Span;
import co.elastic.apm.api.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


class SpanInstrumentationTest extends AbstractInstrumentationTest {

    private Span span;

    @BeforeEach
    void setUp() {
        final Transaction transaction = ElasticApm.startTransaction();
        transaction.setType("default");
        span = ElasticApm.startSpan();
        span.setType("default");
        span.setName("default");
    }

    @Test
    void testSetName() {
        span.setName("foo");
        endSpan();
        assertThat(reporter.getFirstSpan().getName().toString()).isEqualTo("foo");
    }

    @Test
    void testSetType() {
        span.setType("foo");
        endSpan();
        assertThat(reporter.getFirstSpan().getType()).isEqualTo("foo");
    }

    private void endSpan() {
        ElasticApm.currentTransaction().end();
        span.end();
        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getTransactions()).hasSize(1);
    }
}
