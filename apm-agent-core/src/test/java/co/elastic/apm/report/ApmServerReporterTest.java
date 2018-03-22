package co.elastic.apm.report;

import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.payload.Payload;
import co.elastic.apm.impl.payload.ProcessInfo;
import co.elastic.apm.impl.payload.Service;
import co.elastic.apm.impl.payload.SystemInfo;
import co.elastic.apm.impl.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
    private Lock lock = new ReentrantLock();

    @BeforeEach
    void setUp() {
        lock.lock();
        ReporterConfiguration reporterConfiguration = spy(new ReporterConfiguration());
        when(reporterConfiguration.getFlushInterval()).thenReturn(-1);
        when(reporterConfiguration.getMaxQueueSize()).thenReturn(0);
        SystemInfo system = new SystemInfo("x64", "localhost", "platform");
        payloadSender = new PayloadSender() {
            @Override
            public void sendPayload(Payload payload) {
                // blocks to simulate a non-responding APM server
                lock.lock();
            }
        };
        reporter = new ApmServerReporter(new Service(), new ProcessInfo("title"), system, payloadSender, true, reporterConfiguration);
    }

    @Test
    void testReport_discardTransactions_ifQueueIsFull() {
        Transaction transaction = mock(Transaction.class);
        // the first two events are added to the queue and the last one is supposed to be dropped
        reporter.report(transaction); // processed by blocking payload sender
        reporter.report(transaction); // queued in slot 1/2
        reporter.report(transaction); // queued in slot 2/2
        reporter.report(transaction); // queue full -> discarded
        // although we expect only 1 event to be discarded,
        // there could be two if the processor did pick up the first event from the queue
        assertThat(reporter.getDropped()).isGreaterThanOrEqualTo(1);
        verify(transaction, atLeastOnce()).recycle();
    }

    @Test
    void testReport_discardErrors_ifQueueIsFull() {
        ErrorCapture error = mock(ErrorCapture.class);
        // the first two events are added to the queue and the last one is supposed to be dropped
        reporter.report(error); // processed by blocking payload sender
        reporter.report(error); // queued in slot 1/2
        reporter.report(error); // queued in slot 2/2
        reporter.report(error); // queue full -> discarded
        assertThat(reporter.getDropped()).isGreaterThanOrEqualTo(1);
        verify(error, atLeastOnce()).recycle();
    }
}
