package co.elastic.apm.impl;

import co.elastic.apm.MockReporter;
import co.elastic.apm.configuration.SpyConfiguration;
import co.elastic.apm.impl.stacktrace.StacktraceConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class ElasticApmTracerTest {

    private ElasticApmTracer tracer;
    private MockReporter reporter;

    @BeforeEach
    void setUp() {
        reporter = new MockReporter();
        tracer = ElasticApmTracer.builder()
            .configurationRegistry(SpyConfiguration.createSpyConfig())
            .reporter(reporter)
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
    void testDisableStacktracesForFastSpans() {
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

    @Test
    void testRecordException() {
        tracer.captureException(new Exception("test"));
        assertThat(reporter.getErrors()).hasSize(1);
        ErrorCapture error = reporter.getFirstError();
        assertThat(error.getException().getStacktrace()).isNotEmpty();
        assertThat(error.getException().getMessage()).isEqualTo("test");
        assertThat(error.getException().getType()).isEqualTo(Exception.class.getName());
        assertThat(error.getTransaction().getId()).isNull();
    }

    @Test
    void testRecordExceptionWithTrace() {
        try (Transaction transaction = tracer.startTransaction()) {
            transaction.getContext().getRequest().addHeader("foo", "bar");
            tracer.captureException(new Exception("test"));
            assertThat(reporter.getErrors()).hasSize(1);
            ErrorCapture error = reporter.getFirstError();
            assertThat(error.getTransaction().getId()).isEqualTo(transaction.getId().toString());
            assertThat(error.getContext().getRequest().getHeaders()).containsEntry("foo", "bar");
        }
    }
}
