package co.elastic.apm.report;

import co.elastic.apm.intake.transactions.Payload;

public interface PayloadSender {
    void sendPayload(Payload payload);
}
