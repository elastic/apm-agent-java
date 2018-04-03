/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.report;

import co.elastic.apm.impl.error.ErrorPayload;
import co.elastic.apm.impl.payload.Agent;
import co.elastic.apm.impl.payload.Payload;
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
    public void sendPayload(final Payload payload) {
        logger.debug("Sending payload with {} elements to APM server {}",
            payload.getPayloadObjects().size(), reporterConfiguration.getServerUrl());
        final String path;
        if (payload instanceof ErrorPayload) {
            path = "/v1/errors";
        } else {
            path = "/v1/transactions";
        }
        final Request.Builder builder = new Request.Builder()
            .url(reporterConfiguration.getServerUrl() + path)
            .header("User-Agent", getUserAgent(payload));
        if (reporterConfiguration.getSecretToken() != null) {
            builder.header("Authorization", "Bearer " + reporterConfiguration.getSecretToken());
        }
        if (useGzip(payload)) {
            builder.header("Content-Encoding", "gzip");
        }
        Request request = builder
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
                    payload.recycle();
                }
            })
            .build();

        try {
            Response response = httpClient.newCall(request).execute();
            int statusCode = response.code();
            logger.debug("APM server responded with status code {}", statusCode);
            if (statusCode >= 400) {
                droppedTransactions += payload.getPayloadObjects().size();
                if (response.body() != null) {
                    logger.debug(response.body().string());
                }
            }
            response.close();
        } catch (IOException e) {
            logger.debug("Sending payload to APM server failed", e);
            droppedTransactions += payload.getPayloadObjects().size();
        }
    }

    private String getUserAgent(Payload payload) {
        Agent agent = payload.getService().getAgent();
        if (agent != null) {
            return "apm-agent-java " + agent.getVersion();
        }
        return "apm-agent-java";
    }

    private boolean useGzip(Payload payload) {
        return payload.getPayloadObjects().size() > 1;
    }

    public long getDroppedTransactions() {
        return droppedTransactions;
    }

}
