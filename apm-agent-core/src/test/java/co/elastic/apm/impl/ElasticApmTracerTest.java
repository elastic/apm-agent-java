package co.elastic.apm.impl;

import co.elastic.apm.configuration.SpyConfiguration;
import co.elastic.apm.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.report.Reporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ElasticApmTracerTest {

    private ElasticApmTracer tracer;

    @BeforeEach
    void setUp() {
        tracer = ElasticApmTracer.builder()
            .configurationRegistry(SpyConfiguration.createSpyConfig())
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

    @Test
    void testDisableStacktraces() {
        when(tracer.getPlugin(StacktraceConfiguration.class).getSpanFramesMinDurationMs()).thenReturn(0);
        try (Transaction transaction = tracer.startTransaction()) {
            try (Span span = tracer.startSpan()) {
            }
            assertThat(transaction.getSpans().get(0).getStacktrace()).isEmpty();
        }
    }

    @Test
    void testEnableStacktraces() throws InterruptedException {
        when(tracer.getPlugin(StacktraceConfiguration.class).getSpanFramesMinDurationMs()).thenReturn(-1);
        try (Transaction transaction = tracer.startTransaction()) {
            try (Span span = tracer.startSpan()) {
                Thread.sleep(10);
            }
            assertThat(transaction.getSpans().get(0).getStacktrace()).isNotEmpty();
        }
    }

    @Test
    void testDisableEnableStacktracesForFastSpans() {
        when(tracer.getPlugin(StacktraceConfiguration.class).getSpanFramesMinDurationMs()).thenReturn(100);
        try (Transaction transaction = tracer.startTransaction()) {
            try (Span span = tracer.startSpan()) {
            }
            assertThat(transaction.getSpans().get(0).getStacktrace()).isEmpty();
        }
    }

    @Test
    void testEnableStacktracesForSlowSpans() throws InterruptedException {
        when(tracer.getPlugin(StacktraceConfiguration.class).getSpanFramesMinDurationMs()).thenReturn(1);
        try (Transaction transaction = tracer.startTransaction()) {
            try (Span span = tracer.startSpan()) {
                Thread.sleep(10);
            }
            assertThat(transaction.getSpans().get(0).getStacktrace()).isNotEmpty();
        }
    }
}
