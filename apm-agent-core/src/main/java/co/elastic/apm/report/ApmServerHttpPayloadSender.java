package co.elastic.apm.report;

import co.elastic.apm.intake.transactions.Payload;
import co.elastic.apm.report.serialize.PayloadSerializer;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okio.BufferedSink;

import java.io.IOException;

public class ApmServerHttpPayloadSender implements PayloadSender {
    private final String apmServerUrl;
    private final OkHttpClient httpClient;
    private final PayloadSerializer payloadSerializer;

    public ApmServerHttpPayloadSender(OkHttpClient httpClient, String apmServerUrl, PayloadSerializer payloadSerializer) {
        this.httpClient = httpClient;
        this.apmServerUrl = apmServerUrl;
        this.payloadSerializer = payloadSerializer;
    }

    @Override
    public void sendPayload(Payload payload) {
        final MediaType mediaTypeJson = MediaType.parse("application/json");
        okhttp3.Request request = new okhttp3.Request.Builder()
            .url(apmServerUrl + "/v1/transactions")
            .post(new RequestBody() {
                @Override
                public MediaType contentType() {
                    return mediaTypeJson;
                }

                @Override
                public void writeTo(BufferedSink sink) throws IOException {
                    payloadSerializer.serializePayload(sink, payload);
                    sink.close();
                    payload.recycle();
                }
            })
            .build();

        try {
            httpClient.newCall(request).execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
