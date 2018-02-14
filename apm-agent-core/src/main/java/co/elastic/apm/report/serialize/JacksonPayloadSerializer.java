package co.elastic.apm.report.serialize;

import co.elastic.apm.intake.transactions.Payload;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;

public class JacksonPayloadSerializer implements PayloadSerializer {
    private final ObjectMapper objectMapper;

    public JacksonPayloadSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void serializePayload(OutputStream outputStream, Payload payload) throws IOException {
        objectMapper.writeValue(outputStream, payload);
    }
}
