package co.elastic.apm.report;

import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.payload.ProcessInfo;
import co.elastic.apm.impl.payload.Service;
import co.elastic.apm.impl.payload.SystemInfo;
import co.elastic.apm.impl.transaction.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApmServerReporterTest {

    private ApmServerReporter reporter;
    private PayloadSender payloadSender;

    @BeforeEach
    void setUp() {
        ReporterConfiguration reporterConfiguration = spy(new ReporterConfiguration());
        when(reporterConfiguration.getFlushInterval()).thenReturn(-1);
        when(reporterConfiguration.getMaxQueueSize()).thenReturn(2);
        SystemInfo system = new SystemInfo("x64", "localhost", "platform");
        payloadSender = mock(PayloadSender.class);
        reporter = new ApmServerReporter(new Service(), new ProcessInfo(), system, payloadSender, true, reporterConfiguration);
    }

    @Test
    void testReport_discardTransactions_ifQueueIsFull() {
        Transaction transaction = mock(Transaction.class);
        // try to report lots of transactions with a tiny queue should lead to dropped events
        for (int i = 0; i < 100; i++) {
            reporter.report(transaction);
        }
        assertThat(reporter.getDropped()).isGreaterThan(0);
        verify(payloadSender, atLeastOnce()).sendPayload(any());
        verify(transaction, atLeastOnce()).recycle();
    }

    @Test
    void testReport_discardErrors_ifQueueIsFull() {
        ErrorCapture error = mock(ErrorCapture.class);
        // try to report lots of errors with a tiny queue should lead to dropped events
        for (int i = 0; i < 100; i++) {
            reporter.report(error);
        }
        assertThat(reporter.getDropped()).isGreaterThan(0);
        verify(payloadSender, atLeastOnce()).sendPayload(any());
        verify(error, atLeastOnce()).recycle();
    }
}
