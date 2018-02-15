package co.elastic.apm.report.serialize;

import co.elastic.apm.intake.transactions.Payload;
import okio.BufferedSink;

import java.io.IOException;

public interface PayloadSerializer {
    void serializePayload(BufferedSink outputStream, Payload payload) throws IOException;
}
