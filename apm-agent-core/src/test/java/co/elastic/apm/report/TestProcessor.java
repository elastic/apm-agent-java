package co.elastic.apm.report;

import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.report.processor.Processor;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.concurrent.atomic.AtomicInteger;

public class TestProcessor implements Processor {

    private static AtomicInteger transactionCounter = new AtomicInteger();
    private static AtomicInteger errorCounter = new AtomicInteger();

    @Override
    public void init(ConfigurationRegistry tracer) {

    }

    @Override
    public void processBeforeReport(Transaction transaction) {
        transactionCounter.incrementAndGet();
    }

    @Override
    public void processBeforeReport(ErrorCapture error) {
        errorCounter.incrementAndGet();
    }

    public static int getTransactionCount() {
        return transactionCounter.get();
    }

    public static int getErrorCount() {
        return errorCounter.get();
    }
}
