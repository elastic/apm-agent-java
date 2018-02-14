package co.elastic.apm.report.serialize;

import co.elastic.apm.intake.transactions.Payload;

import java.io.IOException;
import java.io.OutputStream;

public interface PayloadSerializer {
    void serializePayload(OutputStream outputStream, Payload payload) throws IOException;
}
