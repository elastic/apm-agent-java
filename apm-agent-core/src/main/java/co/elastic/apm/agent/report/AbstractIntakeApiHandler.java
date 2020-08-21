/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * #L%
 */
package co.elastic.apm.agent.report;

import co.elastic.apm.agent.impl.MetaData;
import co.elastic.apm.agent.proxysupport.AuthenticatorInstrumentation;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import co.elastic.apm.agent.report.serialize.PayloadSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.util.IOUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public class AbstractIntakeApiHandler {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected static final int GZIP_COMPRESSION_LEVEL = 1;
    private static final Object WAIT_LOCK = new Object();

    protected final ReporterConfiguration reporterConfiguration;
    protected final PayloadSerializer payloadSerializer;
    protected final ApmServerClient apmServerClient;
    protected final byte[] metaData;
    protected Deflater deflater;
    protected long currentlyTransmitting = 0;
    protected long reported = 0;
    protected long dropped = 0;
    @Nullable
    protected HttpURLConnection connection;
    @Nullable
    protected OutputStream os;
    protected int errorCount;
    protected volatile boolean shutDown;

    public AbstractIntakeApiHandler(ReporterConfiguration reporterConfiguration, MetaData metaData, PayloadSerializer payloadSerializer, ApmServerClient apmServerClient) {
        this.reporterConfiguration = reporterConfiguration;
        this.payloadSerializer = payloadSerializer;
        this.apmServerClient = apmServerClient;
        this.deflater = new Deflater(GZIP_COMPRESSION_LEVEL);
        payloadSerializer.serializeMetaDataNdJson(metaData);
        this.metaData = payloadSerializer.toString().getBytes(StandardCharsets.UTF_8);
        try {
            payloadSerializer.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /*
     * We add ±10% jitter to the calculated grace period in case multiple agents entered the grace period simultaneously.
     * This can happen if the APM server queue is full which leads to sending an error response to all connected agents.
     * The random jitter makes sure the agents will not all try to reconnect at the same time,
     * which would overwhelm the APM server again.
     */
    static long getRandomJitter(long backoffTimeMillis) {
        final long tenPercentOfBackoffTimeMillis = (long) (backoffTimeMillis * 0.1);
        return (long) (tenPercentOfBackoffTimeMillis * 2 * Math.random()) - tenPercentOfBackoffTimeMillis;
    }

    static long getBackoffTimeSeconds(long errorCount) {
        return (long) Math.pow(Math.min(errorCount, 6), 2);
    }

    protected boolean shouldEndRequest() {
        final long written = deflater.getBytesWritten() + DslJsonSerializer.BUFFER_SIZE;
        final boolean endRequest = written >= reporterConfiguration.getApiRequestSize();
        if (endRequest && logger.isDebugEnabled()) {
            logger.debug("Flushing, because request size limit exceeded {}/{}", written, reporterConfiguration.getApiRequestSize());
        }
        return endRequest;
    }

    @Nullable
    protected HttpURLConnection startRequest(String endpoint) throws IOException {
        final HttpURLConnection connection = apmServerClient.startRequest(endpoint);
        if (connection != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Starting new request to {}", connection.getURL());
            }
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setChunkedStreamingMode(DslJsonSerializer.BUFFER_SIZE);
            connection.setRequestProperty("Content-Encoding", "deflate");
            connection.setRequestProperty("Content-Type", "application/x-ndjson");
            connection.setUseCaches(false);
            try {
                AuthenticatorInstrumentation.toggleFallback(true);
                connection.connect();
            } finally {
                AuthenticatorInstrumentation.toggleFallback(false);
            }
            os = new DeflaterOutputStream(connection.getOutputStream(), deflater);
            os.write(metaData);
        }
        return connection;
    }

    public void endRequest() {
        if (connection != null) {
            try {
                payloadSerializer.flush();
                if (os != null) {
                    os.close();
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Flushing {} uncompressed {} compressed bytes", deflater.getBytesRead(), deflater.getBytesWritten());
                }
                InputStream inputStream = connection.getInputStream();
                final int responseCode = connection.getResponseCode();
                if (responseCode >= 400) {
                    onRequestError(responseCode, inputStream, null);
                } else {
                    onRequestSuccess();
                }
            } catch (IOException e) {
                try {
                    onRequestError(connection.getResponseCode(), connection.getErrorStream(), e);
                } catch (IOException e1) {
                    onRequestError(-1, connection.getErrorStream(), e);
                }
            } finally {
                HttpUtils.consumeAndClose(connection);
                connection = null;
                os = null;
                deflater.reset();
                currentlyTransmitting = 0;
            }
        }
    }

    protected void onRequestError(Integer responseCode, InputStream inputStream, @Nullable IOException e) {
        // TODO read accepted, dropped and invalid
        onConnectionError(responseCode, currentlyTransmitting, 0);
        if (e != null) {
            logger.error("Error sending data to APM server: {}, response code is {}", e.getMessage(), responseCode);
            logger.debug("Sending payload to APM server failed", e);
        }
        if (logger.isWarnEnabled()) {
            try {
                logger.warn(IOUtils.toString(inputStream));
            } catch (IOException e1) {
                logger.warn(e1.getMessage(), e);
            }
        }
    }

    protected void onConnectionError(@Nullable Integer responseCode, long droppedEvents, long reportedEvents) {
        dropped += droppedEvents;
        reported += reportedEvents;
        // if the response code is null, the server did not even send a response
        if (responseCode == null || responseCode > 429) {
            // this server seems to have connection or capacity issues, try next
            apmServerClient.onConnectionError();
        } else if (responseCode == 404) {
            logger.warn("It seems like you are using a version of the APM Server which is not compatible with this agent. " +
                "Please use APM Server 6.5.0 or newer.");
        }

        long backoffTimeSeconds = getBackoffTimeSeconds(errorCount++);
        logger.info("Backing off for {} seconds (+/-10%)", backoffTimeSeconds);
        final long backoffTimeMillis = TimeUnit.SECONDS.toMillis(backoffTimeSeconds);
        if (backoffTimeMillis > 0) {
            // back off because there are connection issues with the apm server
            try {
                synchronized (WAIT_LOCK) {
                    WAIT_LOCK.wait(backoffTimeMillis + getRandomJitter(backoffTimeMillis));
                }
            } catch (InterruptedException e) {
                logger.info("APM Agent ReportingEventHandler had been interrupted", e);
            }
        }
    }

    public long getReported() {
        return reported;
    }

    public long getDropped() {
        return dropped;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void close() {
        shutDown = true;
        synchronized (WAIT_LOCK) {
            WAIT_LOCK.notifyAll();
        }
    }

    protected void onRequestSuccess() {
        errorCount = 0;
        reported += currentlyTransmitting;
    }
}
