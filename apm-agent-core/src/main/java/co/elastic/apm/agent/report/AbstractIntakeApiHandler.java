/*
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
 */
package co.elastic.apm.agent.report;

import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import co.elastic.apm.agent.report.serialize.SerializationConstants;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import org.stagemonitor.util.IOUtils;

import javax.annotation.Nullable;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public class AbstractIntakeApiHandler {
    private static final Logger logger = LoggerFactory.getLogger(AbstractIntakeApiHandler.class);
    private static final Object WAIT_LOCK = new Object();

    protected final ReporterConfiguration reporterConfiguration;
    protected final DslJsonSerializer.Writer payloadSerializer;
    protected final ApmServerClient apmServerClient;
    protected Deflater deflater;
    @Nullable
    protected HttpURLConnection connection;
    @Nullable
    protected OutputStream os;
    @Nullable
    private CountingOutputStream countingOs;
    protected int errorCount;
    protected volatile boolean shutDown;
    private volatile boolean healthy = true;
    private long requestStartedNanos;

    protected AbstractIntakeApiHandler(ReporterConfiguration reporterConfiguration, DslJsonSerializer payloadSerializer, ApmServerClient apmServerClient) {
        this.reporterConfiguration = reporterConfiguration;
        this.payloadSerializer = payloadSerializer.newWriter();
        this.apmServerClient = apmServerClient;
        this.deflater = new Deflater(Deflater.BEST_SPEED);
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
        if (countingOs == null) {
            return false;
        }
        final long written = countingOs.getCount() + payloadSerializer.getBufferSize();
        final boolean endRequest = written >= reporterConfiguration.getApiRequestSize();
        if (endRequest && logger.isDebugEnabled()) {
            logger.debug("Flushing, because request size limit exceeded {}/{}", written, reporterConfiguration.getApiRequestSize());
        }
        return endRequest;
    }

    @Nullable
    protected HttpURLConnection startRequest(String endpoint) throws Exception {
        payloadSerializer.blockUntilReady();
        final HttpURLConnection connection = apmServerClient.startRequest(endpoint);
        if (connection != null) {
            boolean useCompression = !isLocalhost(connection);
            try {
                if (logger.isDebugEnabled()) {
                    logger.debug("Starting new request to {}", connection.getURL());
                }
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setChunkedStreamingMode(SerializationConstants.BUFFER_SIZE);
                if (useCompression) {
                    connection.setRequestProperty("Content-Encoding", "deflate");
                }
                connection.setRequestProperty("Content-Type", "application/x-ndjson");
                connection.setUseCaches(false);
                connection.connect();
                countingOs = new CountingOutputStream(connection.getOutputStream());
                if (useCompression) {
                    os = new DeflaterOutputStream(countingOs, deflater, true);
                } else {
                    os = countingOs;
                }
                payloadSerializer.setOutputStream(os);
                payloadSerializer.appendMetaDataNdJsonToStream();
                payloadSerializer.flushToOutputStream();
                requestStartedNanos = System.nanoTime();
            } catch (IOException e) {
                try {
                    logger.error("Error trying to connect to APM Server at {}. Although not necessarily related to SSL, some related SSL " +
                        "configurations corresponding the current connection are logged at INFO level.", connection.getURL());
                    if (logger.isInfoEnabled() && connection instanceof HttpsURLConnection) {
                        HttpsURLConnection httpsURLConnection = (HttpsURLConnection) connection;
                        try {
                            logger.info("Cipher suite used for this connection: {}", httpsURLConnection.getCipherSuite());
                        } catch (Exception e1) {
                            SSLSocketFactory sslSocketFactory = httpsURLConnection.getSSLSocketFactory();
                            logger.info("Default cipher suites: {}", Arrays.toString(sslSocketFactory.getDefaultCipherSuites()));
                            logger.info("Supported cipher suites: {}", Arrays.toString(sslSocketFactory.getSupportedCipherSuites()));
                        }
                        try {
                            logger.info("APM Server certificates: {}", Arrays.toString(httpsURLConnection.getServerCertificates()));
                        } catch (Exception e1) {
                            // ignore - invalid
                        }
                        try {
                            logger.info("Local certificates: {}", Arrays.toString(httpsURLConnection.getLocalCertificates()));
                        } catch (Exception e1) {
                            // ignore - invalid
                        }
                    }
                } finally {
                    closeAndSuppressErrors(connection);
                }
                throw e;
            } catch (Throwable t) {
                closeAndSuppressErrors(connection);
                throw t;
            }
        }
        return connection;
    }

    private void closeAndSuppressErrors(HttpURLConnection connection) {
        try {
            connection.disconnect();
        } catch (Throwable t) {
            logger.debug("Suppressed error on attempt to close connection", t);
        }
    }

    private boolean isLocalhost(HttpURLConnection connection) {
        switch (connection.getURL().getHost()) {
            case "localhost":
            case "127.0.0.1":
            case "[::1]":
            case "[0:0:0:0:0:0:0:1]":
                return true;
            default:
                return false;
        }
    }

    protected void endRequest() {
        endRequest(false);
    }

    protected void endRequestExceptionally() {
        if (connection == null) {
            //The HttpUrlConnection could not be established if connection == null
            onConnectionError(null, null, 0L);
        } else {
            endRequest(true);
        }
    }

    private void endRequest(boolean isFailed) {
        if (connection != null) {
            long writtenBytes = countingOs != null ? countingOs.getCount() : 0L;
            try {
                payloadSerializer.fullFlush();
                if (os != null) {
                    os.close();
                }
                writtenBytes = countingOs != null ? countingOs.getCount() : 0L;
                if (logger.isDebugEnabled()) {
                    logger.debug("Flushing {} uncompressed {} compressed bytes", deflater.getBytesRead(), writtenBytes);
                }
                InputStream inputStream = connection.getInputStream();
                final int responseCode = connection.getResponseCode();
                if (isFailed || responseCode >= 400) {
                    onRequestError(responseCode, writtenBytes, inputStream, null);
                } else {
                    onRequestSuccess(writtenBytes);
                }
            } catch (IOException e) {
                try {
                    onRequestError(connection.getResponseCode(), writtenBytes, connection.getErrorStream(), e);
                } catch (IOException e1) {
                    onRequestError(-1, writtenBytes, connection.getErrorStream(), e);
                }
            } finally {
                HttpUtils.consumeAndClose(connection);
                connection = null;
                os = null;
                countingOs = null;
                deflater.reset();
            }
        }
    }

    protected boolean isApiRequestTimeExpired() {
        return System.nanoTime() >= requestStartedNanos + TimeUnit.MILLISECONDS.toNanos(reporterConfiguration.getApiRequestTime().getMillis());
    }

    private void onRequestError(Integer responseCode, long bytesWritten, InputStream inputStream, @Nullable IOException e) {
        String responseBody = null;
        try {
            responseBody = IOUtils.toString(inputStream);
            logger.warn("Response body: {}", responseBody);
        } catch (IOException e1) {
            logger.warn(e1.getMessage(), e1);
        }
        onConnectionError(responseCode, responseBody, bytesWritten);
        if (e != null) {
            logger.error("Error sending data to APM server: {}, response code is {}", e.getMessage(), responseCode);
            logger.debug("Sending payload to APM server failed", e);
        }
    }

    protected void onConnectionError(@Nullable Integer responseCode, @Nullable String responseBody, long bytesWritten) {
        // if the response code is null, the server did not even send a response
        if (responseCode == null || responseCode > 429) {
            // this server seems to have connection or capacity issues, try next
            apmServerClient.onConnectionError();
        } else if (responseCode == 404) {
            logger.warn("It seems like you are using a version of the APM Server which is not compatible with this agent. " +
                "Please use APM Server 6.5.0 or newer.");
        }

        backoff();
    }

    private void backoff() {
        long backoffTimeSeconds = getBackoffTimeSeconds(errorCount++);
        logger.info("Backing off for {} seconds (+/-10%)", backoffTimeSeconds);
        final long backoffTimeMillis = TimeUnit.SECONDS.toMillis(backoffTimeSeconds);
        if (backoffTimeMillis > 0) {
            // back off because there are connection issues with the apm server
            try {
                healthy = false;
                synchronized (WAIT_LOCK) {
                    WAIT_LOCK.wait(backoffTimeMillis + getRandomJitter(backoffTimeMillis));
                }
            } catch (InterruptedException e) {
                logger.info("APM Agent ReportingEventHandler had been interrupted", e);
            } finally {
                healthy = true;
            }
        }
    }

    public boolean isHealthy() {
        return healthy;
    }

    public void close() {
        shutDown = true;
        synchronized (WAIT_LOCK) {
            WAIT_LOCK.notifyAll();
        }
    }

    protected void onRequestSuccess(long bytesWritten) {
        errorCount = 0;
    }
}
