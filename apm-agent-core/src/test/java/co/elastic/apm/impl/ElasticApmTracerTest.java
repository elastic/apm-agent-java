package co.elastic.apm.impl;

import co.elastic.apm.MockReporter;
import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Tracer;
import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.configuration.SpyConfiguration;
import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class ElasticApmTracerTest {

    private ElasticApmTracer tracerImpl;
    private MockReporter reporter;

    @BeforeEach
    void setUp() {
        reporter = new MockReporter();
        tracerImpl = ElasticApmTracer.builder()
            .configurationRegistry(SpyConfiguration.createSpyConfig())
            .reporter(reporter)
            .build();
        assertThat(ElasticApmTracer.get()).isNull();
    }

    @AfterEach
    void tearDown() {
        ElasticApmTracer.unregister();
        assertThat(ElasticApmTracer.get()).isNull();
    }

    @Test
    void testNotRegistered() {
        Tracer tracer = ElasticApm.get();
        try (co.elastic.apm.api.Transaction transaction = tracer.startTransaction()) {
            assertThat(transaction).isNotInstanceOf(Transaction.class);
        }
        assertThat(reporter.getTransactions()).isEmpty();
    }

    @Test
    void testRegister() {
        Tracer tracer = ElasticApm.get();
        tracerImpl.register();
        assertThat(ElasticApmTracer.get()).isSameAs(tracerImpl);
        try (co.elastic.apm.api.Transaction transaction = tracer.startTransaction()) {
            assertThat(transaction).isInstanceOf(Transaction.class);
        }
        assertThat(reporter.getTransactions()).hasSize(1);
    }

    @Test
    void testRegisterTwiceTriggersAssertionError() {
        tracerImpl.register();
        assertThatThrownBy(() -> tracerImpl.register())
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void testThreadLocalStorage() {
        try (Transaction transaction = tracerImpl.startTransaction()) {
            assertThat(tracerImpl.currentTransaction()).isSameAs(transaction);
            try (Span span = tracerImpl.startSpan()) {
                assertThat(tracerImpl.currentSpan()).isSameAs(span);
                assertThat(transaction.getSpans()).containsExactly(span);
            }
            assertThat(tracerImpl.currentSpan()).isNull();
        }
        assertThat(tracerImpl.currentTransaction()).isNull();
    }

    @Test
    void testDisableStacktraces() {
        when(tracerImpl.getPlugin(StacktraceConfiguration.class).getSpanFramesMinDurationMs()).thenReturn(0);
        try (Transaction transaction = tracerImpl.startTransaction()) {
            try (Span span = tracerImpl.startSpan()) {
            }
            assertThat(transaction.getSpans().get(0).getStacktrace()).isEmpty();
        }
    }

    @Test
    void testEnableStacktraces() throws InterruptedException {
        when(tracerImpl.getPlugin(StacktraceConfiguration.class).getSpanFramesMinDurationMs()).thenReturn(-1);
        try (Transaction transaction = tracerImpl.startTransaction()) {
            try (Span span = tracerImpl.startSpan()) {
                Thread.sleep(10);
            }
            assertThat(transaction.getSpans().get(0).getStacktrace()).isNotEmpty();
        }
    }

    @Test
    void testDisableStacktracesForFastSpans() {
        when(tracerImpl.getPlugin(StacktraceConfiguration.class).getSpanFramesMinDurationMs()).thenReturn(100);
        try (Transaction transaction = tracerImpl.startTransaction()) {
            try (Span span = tracerImpl.startSpan()) {
            }
            assertThat(transaction.getSpans().get(0).getStacktrace()).isEmpty();
        }
    }

    @Test
    void testEnableStacktracesForSlowSpans() throws InterruptedException {
        when(tracerImpl.getPlugin(StacktraceConfiguration.class).getSpanFramesMinDurationMs()).thenReturn(1);
        try (Transaction transaction = tracerImpl.startTransaction()) {
            try (Span span = tracerImpl.startSpan()) {
                Thread.sleep(10);
            }
            assertThat(transaction.getSpans().get(0).getStacktrace()).isNotEmpty();
        }
    }

    @Test
    void testRecordException() {
        tracerImpl.captureException(new Exception("test"));
        assertThat(reporter.getErrors()).hasSize(1);
        ErrorCapture error = reporter.getFirstError();
        assertThat(error.getException().getStacktrace()).isNotEmpty();
        assertThat(error.getException().getMessage()).isEqualTo("test");
        assertThat(error.getException().getType()).isEqualTo(Exception.class.getName());
        assertThat(error.getTransaction().getId()).isNull();
    }

    @Test
    void testRecordExceptionWithTrace() {
        try (Transaction transaction = tracerImpl.startTransaction()) {
            transaction.getContext().getRequest().addHeader("foo", "bar");
            tracerImpl.captureException(new Exception("test"));
            assertThat(reporter.getErrors()).hasSize(1);
            ErrorCapture error = reporter.getFirstError();
            assertThat(error.getTransaction().getId()).isEqualTo(transaction.getId().toString());
            assertThat(error.getContext().getRequest().getHeaders()).containsEntry("foo", "bar");
        }
    }

    @Test
    void testEnableDropSpans() {
        when(tracer.getPlugin(CoreConfiguration.class).getTransactionMaxSpans()).thenReturn(1);
        try (Transaction transaction = tracer.startTransaction()) {
            try (Span span = tracer.startSpan()) {
                assertThat(span.isSampled()).isTrue();
            }
            try (Span span = tracer.startSpan()) {
                assertThat(span.isSampled()).isFalse();
            }
            assertThat(transaction.isSampled()).isTrue();
            assertThat(transaction.getSpans()).hasSize(1);
            assertThat(transaction.getSpanCount().getDropped().getTotal()).isEqualTo(1);
        }
    }
}
