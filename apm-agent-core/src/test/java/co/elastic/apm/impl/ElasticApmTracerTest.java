package co.elastic.apm.impl;

import co.elastic.apm.report.Reporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ElasticApmTracerTest {

    private ElasticApmTracer tracer;

    @BeforeEach
    void setUp() {
        tracer = ElasticApmTracer.builder()
            .configurationRegistry(mock(ConfigurationRegistry.class))
            .reporter(mock(Reporter.class))
            .build();
    }

    @Test
    void testThreadLocalStorage() {
        try (Transaction transaction = tracer.startTransaction()) {
            assertThat(tracer.currentTransaction()).isSameAs(transaction);
            try (Span span = tracer.startSpan()) {
                assertThat(tracer.currentSpan()).isSameAs(span);
                assertThat(transaction.getSpans()).containsExactly(span);
            }
            assertThat(tracer.currentSpan()).isNull();
        }
        assertThat(tracer.currentTransaction()).isNull();
    }
}
