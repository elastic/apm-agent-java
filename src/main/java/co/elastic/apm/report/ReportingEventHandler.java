package co.elastic.apm.report;

import co.elastic.apm.intake.Process;
import co.elastic.apm.intake.Service;
import co.elastic.apm.intake.System;
import co.elastic.apm.intake.transactions.Payload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lmax.disruptor.EventHandler;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okio.BufferedSink;

import java.io.IOException;

import static co.elastic.apm.report.Reporter.ReportingEvent.ReportingEventType.FLUSH;

class ReportingEventHandler implements EventHandler<Reporter.ReportingEvent> {
    private static final int MAX_TRANSACTIONS_PER_REPORT = 250;
    private final Payload payload;
    private final String apmServerUrl;
    private OkHttpClient httpClient;

    public ReportingEventHandler(Service service, Process process, System system, String apmServerUrl, OkHttpClient httpClient) {
        this.apmServerUrl = apmServerUrl;
        payload = new Payload(service, process, system);
        this.httpClient = httpClient;
    }

    @Override
    public void onEvent(Reporter.ReportingEvent event, long sequence, boolean endOfBatch) throws Exception {
        if (event.type == FLUSH || payload.getTransactions().size() >= MAX_TRANSACTIONS_PER_REPORT) {
            flush();
        }
        payload.getTransactions().add(event.transaction);
        event.resetState();
    }

    private void flush() {
        if (payload.getTransactions().isEmpty()) {
            return;
        }

        try {
            sendPayload();
        } finally {
            payload.resetState();
        }

    }

    private void sendPayload() {
        final ObjectMapper objectMapper = new ObjectMapper();

        MediaType mediaTypeJson = MediaType.parse("application/json");
        okhttp3.Request request = new okhttp3.Request.Builder()
            .url(apmServerUrl + "/v1/transactions")
            .post(new RequestBody() {
                @Override
                public MediaType contentType() {
                    return mediaTypeJson;
                }

                @Override
                public void writeTo(BufferedSink sink) throws IOException {
                    objectMapper.writeValue(sink.outputStream(), payload);
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
