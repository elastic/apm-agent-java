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

import co.elastic.apm.agent.report.processor.ProcessorEventHandler;
import co.elastic.apm.agent.report.serialize.PayloadSerializer;
import co.elastic.apm.agent.util.ExecutorUtils;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.net.HttpURLConnection;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This reporter supports the nd-json HTTP streaming based intake v2 protocol
 */
public class IntakeV2ReportingEventHandler extends AbstractIntakeApiHandler implements ReportingEventHandler {

    public static final String INTAKE_V2_URL = "/intake/v2/events";
    private final ProcessorEventHandler processorEventHandler;
    private final ScheduledExecutorService timeoutTimer;
    @Nullable
    private Runnable timeoutTask;
    private final AtomicLong processed = new AtomicLong();
    private static final Logger logger = LoggerFactory.getLogger(IntakeV2ReportingEventHandler.class);

    public IntakeV2ReportingEventHandler(ReporterConfiguration reporterConfiguration, ProcessorEventHandler processorEventHandler,
                                         PayloadSerializer payloadSerializer, ApmServerClient apmServerClient) {
        super(reporterConfiguration, payloadSerializer, apmServerClient);
        this.processorEventHandler = processorEventHandler;
        this.timeoutTimer = ExecutorUtils.createSingleThreadSchedulingDaemonPool("request-timeout-timer");
    }

    @Override
    public void init(ApmServerReporter reporter) {
        timeoutTask = new WakeupOnTimeout(reporter);
    }

    @Override
    public void onEvent(ReportingEvent event, long sequence, boolean endOfBatch) {
        if (logger.isDebugEnabled()) {
            logger.debug("Receiving {} event (sequence {})", event.getType(), sequence);
        }
        try {
            if (!shutDown) {
                if (connection != null && isApiRequestTimeExpired()) {
                    logger.debug("Request flush because the request timeout occurred");
                    endRequest();
                }
                dispatchEvent(event, sequence, endOfBatch);
            }
        } finally {
            processed.set(sequence);
            event.end();
            event.resetState();
        }
    }

    @Override
    public boolean isProcessed(long sequence) {
        return processed.get() >= sequence;
    }

    private void dispatchEvent(ReportingEvent event, long sequence, boolean endOfBatch) {
        if (event.getType() == null) {
            return;
        }
        switch (event.getType()) {
            case END_REQUEST:
                endRequest();
                break;
            case SHUTDOWN:
                handleShutdownEvent();
                break;
            case SPAN:
            case ERROR:
            case TRANSACTION:
            case JSON_WRITER:
                handleIntakeEvent(event, sequence, endOfBatch);
                break;
        }
    }

    private void handleShutdownEvent() {
        shutDown = true;
        endRequest();
    }

    private void handleIntakeEvent(ReportingEvent event, long sequence, boolean endOfBatch) {
        processorEventHandler.onEvent(event, sequence, endOfBatch);
        try {
            if (connection == null) {
                connection = startRequest(INTAKE_V2_URL);
            }
            if (connection != null) {
                writeEvent(event);
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("Failed to get APM server connection, dropping event: {}", event);
                }
                dropped++;
            }
        } catch (Exception e) {
            handleConnectionError(event, e);
        }

        if (shouldEndRequest()) {
            endRequest();
        }
    }

    private void handleConnectionError(ReportingEvent event, Exception e) {
        logger.error("Failed to handle event of type {} with this error: {}", event.getType(), e.getMessage());
        logger.debug("Event handling failure", e);
        endRequest();
        onConnectionError(null, currentlyTransmitting + 1, 0);
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
        } else if (event.getSpan() != null) {
            currentlyTransmitting++;
            payloadSerializer.serializeSpanNdJson(event.getSpan());
        } else if (event.getError() != null) {
            currentlyTransmitting++;
            payloadSerializer.serializeErrorNdJson(event.getError());
        } else if (event.getJsonWriter() != null) {
            payloadSerializer.writeBytes(event.getJsonWriter().getByteBuffer(), event.getJsonWriter().size());
        }
    }

    @Override
    @Nullable
    protected HttpURLConnection startRequest(String endpoint) throws Exception {
        HttpURLConnection connection = super.startRequest(endpoint);
        if (connection != null) {
            if (timeoutTask != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Scheduling request timeout in {}", reporterConfiguration.getApiRequestTime());
                }
                timeoutTimer.schedule(timeoutTask, reporterConfiguration.getApiRequestTime().getMillis(), TimeUnit.MILLISECONDS);
            }
        }
        return connection;
    }

    @Override
    public void close() {
        super.close();
        logger.info("Reported events: {}", reported);
        logger.info("Dropped events: {}", dropped);
        timeoutTimer.shutdownNow();
    }

    /**
     * Schedules a wakeup event to the disruptor.
     * This ensures that even in quiet periods, the event handler will be woken up to end the request after api_request_time has expired.
     */
    private static class WakeupOnTimeout implements Runnable {
        private final ApmServerReporter reporter;

        private WakeupOnTimeout(ApmServerReporter reporter) {
            this.reporter = reporter;
        }

        @Override
        public void run() {
            try {
                reporter.scheduleWakeupEvent();
            } catch (Exception e) {
                // should never happen in practice as we're not expecting this method to throw any exception
                logger.warn(e.getMessage(), e);
            }
        }
    }
}
