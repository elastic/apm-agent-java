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
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.util.ExecutorUtils;
import co.elastic.apm.agent.util.LoggerUtils;
import com.dslplatform.json.DslJson;

import javax.annotation.Nullable;
import java.net.HttpURLConnection;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This reporter supports the nd-json HTTP streaming based intake v2 protocol
 */
public class IntakeV2ReportingEventHandler extends AbstractIntakeApiHandler implements ReportingEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(IntakeV2ReportingEventHandler.class);

    public static final String INTAKE_V2_URL = "/intake/v2/events";
    public static final String INTAKE_V2_FLUSH_URL = INTAKE_V2_URL + "?flushed=true";

    private static final Logger logsSupportLogger = LoggerUtils.logOnce(LoggerFactory.getLogger(IntakeV2ReportingEventHandler.class));

    private final ProcessorEventHandler processorEventHandler;
    private final ScheduledExecutorService timeoutTimer;
    @Nullable
    private Runnable timeoutTask;

    @Nullable
    private ApmServerReporter reporter;
    private final AtomicLong processed = new AtomicLong();
    private final ReportingEventCounter inflightEvents = new ReportingEventCounter();

    private final DslJson<Object> dslJson;

    private long reported;
    private long dropped;

    public IntakeV2ReportingEventHandler(ReporterConfiguration reporterConfiguration, ProcessorEventHandler processorEventHandler,
                                         DslJsonSerializer payloadSerializer, ApmServerClient apmServerClient) {
        super(reporterConfiguration, payloadSerializer, apmServerClient);
        this.processorEventHandler = processorEventHandler;
        this.dslJson = new DslJson<>(new DslJson.Settings<>());
        this.timeoutTimer = ExecutorUtils.createSingleThreadSchedulingDaemonPool("request-timeout-timer");
    }

    @Override
    public void init(ApmServerReporter reporter) {
        this.reporter = reporter;
        timeoutTask = new WakeupOnTimeout(reporter);
    }

    @Override
    public void onEvent(ReportingEvent event, long sequence, boolean endOfBatch) throws Exception {
        try {
            if (reporter != null) {
                ReporterMonitor monitor = reporter.getReporterMonitor();
                monitor.eventDequeued(event.getType(), reporter.getQueueCapacity(), reporter.getQueueElementCount());
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Receiving {} event (sequence {})", event.getType(), sequence);
            }
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

    private void dispatchEvent(ReportingEvent event, long sequence, boolean endOfBatch) throws Exception {
        switch (event.getType()) {
            case WAKEUP:
                // wakeup silently ignored
                break;
            case MAKE_FLUSH_REQUEST:
                endRequest();
                connection = startRequest(INTAKE_V2_FLUSH_URL);
                // continuing to behave as END_REQUEST
            case END_REQUEST:
                endRequest();
                break;
            case SHUTDOWN:
                handleShutdownEvent();
                break;
            case SPAN:
            case ERROR:
            case TRANSACTION:
            case BYTES_LOG:
            case STRING_LOG:
            case METRICSET_JSON_WRITER:
                handleIntakeEvent(event, sequence, endOfBatch);
                break;
            default:
                throw new IllegalArgumentException("unsupported event type " + event.getType());
        }
    }

    private void handleShutdownEvent() {
        shutDown = true;
        endRequest();
    }

    private void handleIntakeEvent(ReportingEvent event, long sequence, boolean endOfBatch) {
        processorEventHandler.onEvent(event, sequence, endOfBatch);
        try {
            inflightEvents.increment(event.getType());
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
                if (reporter != null) {
                    inflightEvents.reset(); //we never actually created a request when connection is null
                    reporter.getReporterMonitor().eventDroppedAfterDequeue(event.getType());
                }
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
        endRequestExceptionally();
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
            payloadSerializer.serializeTransactionNdJson(event.getTransaction());
        } else if (event.getSpan() != null) {
            payloadSerializer.serializeSpanNdJson(event.getSpan());
        } else if (event.getError() != null) {
            payloadSerializer.serializeErrorNdJson(event.getError());
        } else if (event.getJsonWriter() != null) {
            payloadSerializer.writeBytes(event.getJsonWriter().getByteBuffer(), event.getJsonWriter().size());
        } else if (event.getBytesLog() != null && logsSupported()) {
            payloadSerializer.serializeLogNdJson(event.getBytesLog());
        } else if (event.getStringLog() != null && logsSupported()) {
            payloadSerializer.serializeLogNdJson(event.getStringLog());
        }
    }

    private boolean logsSupported() {
        if (apmServerClient.supportsLogsEndpoint()) {
            return true;
        }

        logsSupportLogger.warn("sending logs to apm server is not supported, upgrading to a more recent version is required");
        return false;
    }

    @Override
    @Nullable
    protected HttpURLConnection startRequest(String endpoint) throws Exception {
        HttpURLConnection connection = super.startRequest(endpoint);
        if (connection != null) {
            if (timeoutTask != null) {
                long requestTimeoutMillis = reporterConfiguration.getApiRequestTime().getMillis();
                if (logger.isDebugEnabled()) {
                    logger.debug("Scheduling request timeout in {} seconds", TimeUnit.MILLISECONDS.toSeconds(requestTimeoutMillis));
                }
                timeoutTimer.schedule(timeoutTask, requestTimeoutMillis, TimeUnit.MILLISECONDS);
            }
        }
        return connection;
    }

    @Override
    protected void onRequestSuccess(long bytesWritten) {
        long totalCount = inflightEvents.getTotalCount();
        reported += totalCount;
        if (reporter != null) {
            reporter.getReporterMonitor().requestFinished(new ReportingEventCounter(inflightEvents), totalCount, bytesWritten, true);
        }
        inflightEvents.reset();
        super.onRequestSuccess(bytesWritten);
    }

    @Override
    protected void onConnectionError(@Nullable Integer responseCode, @Nullable String responseBody, long bytesWritten) {
        long accepted = readAccepted(responseBody);
        dropped += inflightEvents.getTotalCount() - accepted;
        if (reporter != null) {
            reporter.getReporterMonitor().requestFinished(new ReportingEventCounter(inflightEvents), accepted, bytesWritten, false);
        }
        inflightEvents.reset();
        super.onConnectionError(responseCode, responseBody, bytesWritten);
    }

    private long readAccepted(@Nullable String responseBody) {
        if (responseBody != null) {
            byte[] data = responseBody.getBytes();
            try {
                Map<String, ?> response = dslJson.deserialize(Map.class, data, data.length);
                if (response != null && response.get("accepted") instanceof Number) {
                    return ((Number) response.get("accepted")).longValue();
                }
            } catch (Exception e) {
                logger.warn("failed to deserialize APM server response", e);
            }
        }
        return 0;
    }

    public long getReported() {
        return reported;
    }

    public long getDropped() {
        return dropped;
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
                logger.warn("Error trying to schedule a WAKEUP event: " + e.getMessage(), e);
            }
        }
    }

}
