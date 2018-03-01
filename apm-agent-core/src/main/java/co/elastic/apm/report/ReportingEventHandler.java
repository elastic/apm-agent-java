package co.elastic.apm.report;

import co.elastic.apm.impl.payload.Process;
import co.elastic.apm.impl.payload.Service;
import co.elastic.apm.impl.payload.SystemInfo;
import co.elastic.apm.impl.payload.TransactionPayload;
import com.lmax.disruptor.EventHandler;

import static co.elastic.apm.report.ApmServerReporter.ReportingEvent.ReportingEventType.FLUSH;
import static co.elastic.apm.report.ApmServerReporter.ReportingEvent.ReportingEventType.TRANSACTION;

class ReportingEventHandler implements EventHandler<ApmServerReporter.ReportingEvent> {
    private static final int MAX_TRANSACTIONS_PER_REPORT = 250;
    private final TransactionPayload payload;
    private final PayloadSender payloadSender;

    public ReportingEventHandler(Service service, Process process, SystemInfo system, PayloadSender payloadSender) {
        this.payloadSender = payloadSender;
        payload = new TransactionPayload(service, process, system);
    }

    @Override
    public void onEvent(ApmServerReporter.ReportingEvent event, long sequence, boolean endOfBatch) throws Exception {
        if (event.type == FLUSH) {
            flush();
        }
        if (event.type == TRANSACTION) {
            payload.getTransactions().add(event.transaction);
            if (payload.getTransactions().size() >= MAX_TRANSACTIONS_PER_REPORT) {
                flush();
            }
        }
        event.resetState();
    }

    private void flush() {
        if (payload.getTransactions().isEmpty()) {
            return;
        }

        try {
            payloadSender.sendPayload(payload);
        } finally {
            payload.resetState();
        }

    }

}
