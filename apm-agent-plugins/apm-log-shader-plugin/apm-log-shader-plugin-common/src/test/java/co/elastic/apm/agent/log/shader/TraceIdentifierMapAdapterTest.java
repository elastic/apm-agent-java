package co.elastic.apm.agent.log.shader;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.Scope;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


class TraceIdentifierMapAdapterTest {

    private ElasticApmTracer tracer = MockTracer.createRealTracer();

    @BeforeEach
    void setUp() {
        GlobalTracer.init(tracer);
    }

    @AfterEach
    void tearDown() {
        tracer.stop();
        GlobalTracer.setNoop();
    }

    @Test
    void testNoContext() {
        assertThat(TraceIdentifierMapAdapter.get()).isEmpty();
    }

    @Test
    void testTransactionContext() {
        Transaction transaction = tracer.startRootTransaction(null);
        try (Scope scope = transaction.activateInScope()) {
            assertThat(TraceIdentifierMapAdapter.get()).containsOnlyKeys("trace.id", "transaction.id");
        } finally {
            transaction.end();
        }
        assertThat(TraceIdentifierMapAdapter.get()).isEmpty();
    }

    @Test
    void testSpanContext() {
        Transaction transaction = tracer.startRootTransaction(null);
        Span span = transaction.createSpan();
        try (Scope scope = span.activateInScope()) {
            assertThat(TraceIdentifierMapAdapter.get()).containsOnlyKeys("trace.id", "transaction.id", "span.id");
        } finally {
            span.end();
        }
        transaction.end();
        assertThat(TraceIdentifierMapAdapter.get()).isEmpty();
    }
}
