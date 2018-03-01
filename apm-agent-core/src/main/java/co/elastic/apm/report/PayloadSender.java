package co.elastic.apm.report;

import co.elastic.apm.impl.payload.TransactionPayload;

public interface PayloadSender {
    void sendPayload(TransactionPayload payload);
}
