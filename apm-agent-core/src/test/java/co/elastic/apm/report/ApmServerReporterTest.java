package co.elastic.apm.report;

import co.elastic.apm.configuration.SpyConfiguration;
import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.payload.Payload;
import co.elastic.apm.impl.payload.ProcessInfo;
import co.elastic.apm.impl.payload.Service;
import co.elastic.apm.impl.payload.SystemInfo;
import co.elastic.apm.impl.transaction.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApmServerReporterTest {

    private ApmServerReporter reporter;

    @BeforeEach
    void setUp() {
        final ConfigurationRegistry configurationRegistry = SpyConfiguration.createSpyConfig();
        ReporterConfiguration reporterConfiguration = configurationRegistry.getConfig(ReporterConfiguration.class);
        when(reporterConfiguration.getFlushInterval()).thenReturn(-1);
        when(reporterConfiguration.getMaxQueueSize()).thenReturn(0);
        SystemInfo system = new SystemInfo("x64", "localhost", "platform");
        reporter = new ApmServerReporter(configurationRegistry, new Service(), new ProcessInfo("title"), system,
            mock(PayloadSender.class), true, reporterConfiguration);
    }

    @Test
    void testTransactionProcessor() throws ExecutionException, InterruptedException {
        final int transactionCount = TestProcessor.getTransactionCount();
        reporter.report(mock(Transaction.class));
        reporter.flush().get();

        assertThat(reporter.getDropped()).isEqualTo(0);
        assertThat(TestProcessor.getTransactionCount()).isEqualTo(transactionCount + 1);
    }

    @Test
    void testErrorProcessor() throws ExecutionException, InterruptedException {
        final int errorCount = TestProcessor.getErrorCount();
        reporter.report(mock(ErrorCapture.class));
        reporter.flush().get();

        assertThat(reporter.getDropped()).isEqualTo(0);
        assertThat(TestProcessor.getErrorCount()).isEqualTo(errorCount + 1);
    }
}
