package co.elastic.apm.report;

import co.elastic.apm.impl.error.ErrorPayload;
import co.elastic.apm.impl.payload.Payload;
import co.elastic.apm.impl.payload.Process;
import co.elastic.apm.impl.payload.Service;
import co.elastic.apm.impl.payload.SystemInfo;
import co.elastic.apm.impl.payload.TransactionPayload;
import com.lmax.disruptor.EventHandler;

import static co.elastic.apm.report.ApmServerReporter.ReportingEvent.ReportingEventType.ERROR;
import static co.elastic.apm.report.ApmServerReporter.ReportingEvent.ReportingEventType.FLUSH;
import static co.elastic.apm.report.ApmServerReporter.ReportingEvent.ReportingEventType.TRANSACTION;

class ReportingEventHandler implements EventHandler<ApmServerReporter.ReportingEvent> {
    private final TransactionPayload transactionPayload;
    private final ErrorPayload errorPayload;
    private final PayloadSender payloadSender;
    private final ReporterConfiguration reporterConfiguration;

    public ReportingEventHandler(Service service, Process process, SystemInfo system, PayloadSender payloadSender, ReporterConfiguration reporterConfiguration) {
        this.payloadSender = payloadSender;
        this.reporterConfiguration = reporterConfiguration;
        transactionPayload = new TransactionPayload(process, service, system);
        errorPayload = new ErrorPayload(process, service, system);
    }

    @Override
    public void onEvent(ApmServerReporter.ReportingEvent event, long sequence, boolean endOfBatch) {
        if (event.type == FLUSH) {
            flush(transactionPayload);
            flush(errorPayload);
        }
        if (event.type == TRANSACTION) {
            transactionPayload.getTransactions().add(event.transaction);
            if (transactionPayload.getTransactions().size() >= reporterConfiguration.getMaxQueueSize()) {
                flush(transactionPayload);
            }
        }
        if (event.type == ERROR) {
            errorPayload.getErrors().add(event.error);
            // report errors immediately, except if there are multiple in the queue
            if (endOfBatch) {
                flush(errorPayload);
            }
        }
        event.resetState();
    }

    private void flush(Payload payload) {
        if (payload.getPayloadObjects().isEmpty()) {
            return;
        }

        try {
            payloadSender.sendPayload(payload);
        } finally {
            payload.resetState();
        }

    }

}
