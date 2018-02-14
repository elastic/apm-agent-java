package co.elastic.apm.report;

import co.elastic.apm.intake.Process;
import co.elastic.apm.intake.Service;
import co.elastic.apm.intake.System;
import co.elastic.apm.intake.transactions.Payload;
import com.lmax.disruptor.EventHandler;

import static co.elastic.apm.report.Reporter.ReportingEvent.ReportingEventType.FLUSH;
import static co.elastic.apm.report.Reporter.ReportingEvent.ReportingEventType.TRANSACTION;

class ReportingEventHandler implements EventHandler<Reporter.ReportingEvent> {
    private static final int MAX_TRANSACTIONS_PER_REPORT = 250;
    private final Payload payload;
    private final PayloadSender payloadSender;

    public ReportingEventHandler(Service service, Process process, System system, PayloadSender payloadSender) {
        this.payloadSender = payloadSender;
        payload = new Payload(service, process, system);
    }

    @Override
    public void onEvent(Reporter.ReportingEvent event, long sequence, boolean endOfBatch) throws Exception {
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
