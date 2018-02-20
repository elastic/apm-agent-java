package co.elastic.apm.report.serialize;

import co.elastic.apm.impl.TransactionPayload;
import okio.BufferedSink;

import java.io.IOException;

public interface PayloadSerializer {
    void serializePayload(BufferedSink sink, TransactionPayload payload) throws IOException;
}
