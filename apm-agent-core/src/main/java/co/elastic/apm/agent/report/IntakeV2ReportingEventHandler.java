/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
import co.elastic.apm.agent.report.processor.ProcessorEventHandler;
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
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * This reporter supports the nd-json HTTP streaming based intake v2 protocol
 */
public class IntakeV2ReportingEventHandler implements ReportingEventHandler {

    public static final String INTAKE_V2_URL = "/intake/v2/events";
    private static final Logger logger = LoggerFactory.getLogger(IntakeV2ReportingEventHandler.class);
    private static final int GZIP_COMPRESSION_LEVEL = 1;
    private static final Object WAIT_LOCK = new Object();

    private final ReporterConfiguration reporterConfiguration;
    private final ProcessorEventHandler processorEventHandler;
    private final MetaData metaData;
    private final PayloadSerializer payloadSerializer;
    private final Timer timeoutTimer;
    private final ApmServerClient apmServerClient;
    private Deflater deflater;
    private long currentlyTransmitting = 0;
    private long reported = 0;
    private long dropped = 0;
    @Nullable
    private HttpURLConnection connection;
    @Nullable
    private OutputStream os;
    @Nullable
    private ApmServerReporter reporter;
    @Nullable
    private TimerTask timeoutTask;
    private int errorCount;
    private volatile boolean shutDown;

    public IntakeV2ReportingEventHandler(ReporterConfiguration reporterConfiguration, ProcessorEventHandler processorEventHandler,
                                         PayloadSerializer payloadSerializer, MetaData metaData, ApmServerClient apmServerClient) {
        this.reporterConfiguration = reporterConfiguration;
        this.processorEventHandler = processorEventHandler;
        this.payloadSerializer = payloadSerializer;
        this.metaData = metaData;
        this.apmServerClient = apmServerClient;
        this.deflater = new Deflater(GZIP_COMPRESSION_LEVEL);
        this.timeoutTimer = new Timer("apm-request-timeout-timer", true);
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

    @Override
    public void init(ApmServerReporter reporter) {
        this.reporter = reporter;
    }

    @Override
    public void onEvent(ReportingEvent event, long sequence, boolean endOfBatch) {
        if (logger.isDebugEnabled()) {
            logger.debug("Receiving {} event (sequence {})", event.getType(), sequence);
        }
        try {
            if (!shutDown) {
                handleEvent(event, sequence, endOfBatch);
            }
        } finally {
            event.resetState();
        }
    }

    private void handleEvent(ReportingEvent event, long sequence, boolean endOfBatch) {

        if (event.getType() == null) {
            return;
        } else if (event.getType() == ReportingEvent.ReportingEventType.FLUSH) {
            flush();
            return;
        } else if (event.getType() == ReportingEvent.ReportingEventType.SHUTDOWN) {
            shutDown = true;
            flush();
            return;
        }
        processorEventHandler.onEvent(event, sequence, endOfBatch);
        try {
            if (connection == null) {
                connection = startRequest();
                payloadSerializer.serializeMetaDataNdJson(metaData);
            }
            writeEvent(event);
        } catch (Exception e) {
            logger.error("Failed to handle event of type {} with this error: {}", event.getType(), e.getMessage());
            logger.debug("Event handling failure", e);
            flush();
            onConnectionError(null, currentlyTransmitting + 1, 0);
        }
        if (shouldFlush()) {
            flush();
        }
    }

    /**
     * Returns the number of bytes already serialized and waiting in the underlying serializer's buffer.
     *
     * @return number of bytes currently waiting in the underlying serializer's buffer, not yet flushed to the underlying stream
     */
    int getBufferSize() {
        return payloadSerializer.getBufferSize();
    }

    private void writeEvent(ReportingEvent event) {
        if (event.getTransaction() != null) {
            currentlyTransmitting++;
            payloadSerializer.serializeTransactionNdJson(event.getTransaction());
            event.getTransaction().decrementReferences();
        } else if (event.getSpan() != null) {
            currentlyTransmitting++;
            payloadSerializer.serializeSpanNdJson(event.getSpan());
            event.getSpan().decrementReferences();
        } else if (event.getError() != null) {
            currentlyTransmitting++;
            payloadSerializer.serializeErrorNdJson(event.getError());
            event.getError().recycle();
        } else if (event.getMetricRegistry() != null) {
            payloadSerializer.serializeMetrics(event.getMetricRegistry());
        }
    }

    private boolean shouldFlush() {
        final long written = deflater.getBytesWritten() + DslJsonSerializer.BUFFER_SIZE;
        final boolean flush = written >= reporterConfiguration.getApiRequestSize();
        if (flush && logger.isDebugEnabled()) {
            logger.debug("Flushing, because request size limit exceeded {}/{}", written, reporterConfiguration.getApiRequestSize());
        }
        return flush;
    }

    private HttpURLConnection startRequest() throws IOException {
        final HttpURLConnection connection = apmServerClient.startRequest(INTAKE_V2_URL);
        if (logger.isDebugEnabled()) {
            logger.debug("Starting new request to {}", connection.getURL());
        }
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setChunkedStreamingMode(DslJsonSerializer.BUFFER_SIZE);
        connection.setRequestProperty("Content-Encoding", "deflate");
        connection.setRequestProperty("Content-Type", "application/x-ndjson");
        connection.setUseCaches(false);
        connection.connect();
        os = new DeflaterOutputStream(connection.getOutputStream(), deflater);
        payloadSerializer.setOutputStream(os);
        if (reporter != null) {
            timeoutTask = new FlushOnTimeoutTimerTask(reporter);
            if (logger.isDebugEnabled()) {
                logger.debug("Scheduling request timeout in {}", reporterConfiguration.getApiRequestTime());
            }
            timeoutTimer.schedule(timeoutTask, reporterConfiguration.getApiRequestTime().getMillis());
        }
        return connection;
    }

    void flush() {
        cancelTimeout();
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
                    onFlushError(responseCode, inputStream, null);
                } else {
                    onFlushSuccess();
                }
            } catch (IOException e) {
                try {
                    onFlushError(connection.getResponseCode(), connection.getErrorStream(), e);
                } catch (IOException e1) {
                    onFlushError(-1, connection.getErrorStream(), e);
                }
            } finally {
                HttpUtils.consumeAndClose(connection);
                connection = null;
                deflater.reset();
                currentlyTransmitting = 0;
            }
        }
    }

    private void cancelTimeout() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
    }

    private void onFlushSuccess() {
        errorCount = 0;
        reported += currentlyTransmitting;
    }

    private void onFlushError(Integer responseCode, InputStream inputStream, @Nullable IOException e) {
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

    private void onConnectionError(@Nullable Integer responseCode, long droppedEvents, long reportedEvents) {
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

    @Override
    public long getReported() {
        return reported;
    }

    @Override
    public long getDropped() {
        return dropped;
    }

    @Override
    public void close() {
        shutDown = true;
        timeoutTimer.cancel();
        synchronized (WAIT_LOCK) {
            WAIT_LOCK.notifyAll();
        }
    }

    private static class FlushOnTimeoutTimerTask extends TimerTask {
        private final ApmServerReporter reporter;
        @Nullable
        private volatile Future<Void> flush;

        private FlushOnTimeoutTimerTask(ApmServerReporter reporter) {
            this.reporter = reporter;
        }

        @Override
        public void run() {
            logger.debug("Request flush because the request timeout occurred");
            try {
                // If the ring buffer is full this throws an exception.
                // In case it's full due to a traffic spike it means that it will eventually flush anyway because of
                // the max request size, but we need to catch this Exception otherwise the Timer thread dies.
                flush = reporter.flush();
            } catch (Exception e) {
                // This shouldn't reoccur when the queue is full due to lack of communication with the APM server
                // as the TimerTask wouldn't be scheduled unless connection succeeds.
                logger.info("Failed to register a Flush event to the disruptor: {}", e.getMessage());
            }
        }

        @Override
        public boolean cancel() {
            final boolean cancel = super.cancel();
            final Future<Void> flush = this.flush;
            if (flush != null) {
                flush.cancel(false);
            }
            return cancel;
        }
    }

}
