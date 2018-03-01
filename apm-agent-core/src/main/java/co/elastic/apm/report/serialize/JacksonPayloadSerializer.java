package co.elastic.apm.report.serialize;

import co.elastic.apm.impl.payload.TransactionPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import okio.BufferedSink;

import java.io.IOException;

public class JacksonPayloadSerializer implements PayloadSerializer {
    private final ObjectMapper objectMapper;

    public JacksonPayloadSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void serializePayload(BufferedSink sink, TransactionPayload payload) throws IOException {
        objectMapper.writeValue(sink.outputStream(), payload);
    }
}
