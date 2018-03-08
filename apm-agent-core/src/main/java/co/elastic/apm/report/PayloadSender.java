package co.elastic.apm.report;

import co.elastic.apm.impl.payload.Payload;

public interface PayloadSender {
    void sendPayload(Payload payload);
}
