package co.elastic.apm.report;

import co.elastic.apm.impl.Transaction;
import co.elastic.apm.impl.TransactionPayload;
import co.elastic.apm.report.serialize.PayloadSerializer;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import okio.GzipSink;
import okio.Okio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ApmServerHttpPayloadSender implements PayloadSender {
    private static final Logger logger = LoggerFactory.getLogger(ApmServerHttpPayloadSender.class);
    private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json");
    private static final int GZIP_COMPRESSION_LEVEL = 3;

    private final OkHttpClient httpClient;
    private final ReporterConfiguration reporterConfiguration;
    private final PayloadSerializer payloadSerializer;
    private long droppedTransactions = 0;

    public ApmServerHttpPayloadSender(OkHttpClient httpClient, PayloadSerializer payloadSerializer,
                                      ReporterConfiguration reporterConfiguration) {
        this.httpClient = httpClient;
        this.reporterConfiguration = reporterConfiguration;
        this.payloadSerializer = payloadSerializer;
    }

    @Override
    public void sendPayload(final TransactionPayload payload) {
        // this is ok as its only executed single threaded
        okhttp3.Request request = new Request.Builder()
            .url(reporterConfiguration.getServerUrl() + "/v1/transactions")
            .header("Content-Encoding", "gzip")
            .header("User-Agent", "apm-agent-java " + payload.getService().getAgent().getVersion())
            .post(new RequestBody() {


            @Override
            public MediaType contentType() {
                return MEDIA_TYPE_JSON;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                if (useGzip(payload)) {
                    GzipSink gzipSink = new GzipSink(sink);
                    gzipSink.deflater().setLevel(GZIP_COMPRESSION_LEVEL);
                    sink = Okio.buffer(gzipSink);
                }
                payloadSerializer.serializePayload(sink, payload);
                sink.close();
                for (Transaction transaction : payload.getTransactions()) {
                    transaction.recycle();
                }
            }
        })
            .build();

        try {
            logger.debug("Sending payload with {} transactions to APM server {}",
                payload.getTransactions().size(), reporterConfiguration.getServerUrl());
            Response response = httpClient.newCall(request).execute();
            int statusCode = response.code();
            logger.debug("APM server responded with status code {}", statusCode);
            if (statusCode >= 400) {
                droppedTransactions += payload.getTransactions().size();
                if (response.body() != null) {
                    logger.debug(response.body().string());
                }
            }
            response.close();
        } catch (IOException e) {
            logger.debug("Sending transactions to APM server failed", e);
            droppedTransactions += payload.getTransactions().size();
        }
    }

    private boolean useGzip(TransactionPayload payload) {
        // TODO determine if turning on gzip is woth it based on the payload size
        return true;
    }

    public long getDroppedTransactions() {
        return droppedTransactions;
    }

}
