package co.elastic.apm.report;

import co.elastic.apm.impl.Transaction;
import co.elastic.apm.impl.TransactionPayload;
import co.elastic.apm.report.serialize.PayloadSerializer;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okio.BufferedSink;
import okio.GzipSink;
import okio.Okio;

import java.io.IOException;

public class ApmServerHttpPayloadSender implements PayloadSender {
    private final OkHttpClient httpClient;
    private final ReporterConfiguration reporterConfiguration;
    private final PayloadRequestBody body;
    private long droppedTransactions = 0;

    public ApmServerHttpPayloadSender(OkHttpClient httpClient, PayloadSerializer payloadSerializer,
                                      ReporterConfiguration reporterConfiguration) {
        this.httpClient = httpClient;
        this.reporterConfiguration = reporterConfiguration;
        this.body = new PayloadRequestBody(payloadSerializer);
    }

    @Override
    public void sendPayload(final TransactionPayload payload) {
        // this is ok as its only executed single threaded
        body.payload = payload;
        okhttp3.Request request = new okhttp3.Request.Builder()
            .url(reporterConfiguration.getServerUrl() + "/v1/transactions")
            .header("Content-Encoding", "gzip")
            .header("User-Agent", "apm-agent-java " + payload.getService().getAgent().getVersion())
            .post(body)
            .build();

        try {
            httpClient.newCall(request).execute().close();
        } catch (IOException e) {
            droppedTransactions += payload.getTransactions().size();
        }
    }

    public long getDroppedTransactions() {
        return droppedTransactions;
    }

    private static class PayloadRequestBody extends RequestBody {
        private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json");

        private final PayloadSerializer payloadSerializer;

        TransactionPayload payload;

        PayloadRequestBody(PayloadSerializer payloadSerializer) {
            this.payloadSerializer = payloadSerializer;
        }

        @Override
        public MediaType contentType() {
            return MEDIA_TYPE_JSON;
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            payloadSerializer.serializePayload(Okio.buffer(new GzipSink(sink)), payload);
            sink.close();
            for (Transaction transaction : payload.getTransactions()) {
                transaction.recycle();
            }
        }
    }
}
