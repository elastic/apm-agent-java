package co.elastic.apm.report;

import co.elastic.apm.impl.TransactionPayload;

public interface PayloadSender {
    void sendPayload(TransactionPayload payload);
}
