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
import co.elastic.apm.agent.common.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.HttpURLConnection;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Future;

/**
 * This reporter supports the nd-json HTTP streaming based intake v2 protocol
 */
public class IntakeV2ReportingEventHandler extends AbstractIntakeApiHandler implements ReportingEventHandler {

    public static final String INTAKE_V2_URL = "/intake/v2/events";
    private final ProcessorEventHandler processorEventHandler;
    private final Timer timeoutTimer;
    @Nullable
    private ApmServerReporter reporter;
    @Nullable
    private TimerTask timeoutTask;

    public IntakeV2ReportingEventHandler(ReporterConfiguration reporterConfiguration, ProcessorEventHandler processorEventHandler,
                                         PayloadSerializer payloadSerializer, ApmServerClient apmServerClient) {
        super(reporterConfiguration, payloadSerializer, apmServerClient);
        this.processorEventHandler = processorEventHandler;
        this.timeoutTimer = new Timer(ThreadUtils.addElasticApmThreadPrefix("request-timeout-timer"), true);
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
            endRequest();
            return;
        } else if (event.getType() == ReportingEvent.ReportingEventType.SHUTDOWN) {
            shutDown = true;
            endRequest();
            return;
        }
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
            logger.error("Failed to handle event of type {} with this error: {}", event.getType(), e.getMessage());
            logger.debug("Event handling failure", e);
            endRequest();
            onConnectionError(null, currentlyTransmitting + 1, 0);
        } finally {
            event.end();
        }

        if (shouldEndRequest()) {
            endRequest();
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

    private void cancelTimeout() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
    }

    @Override
    @Nullable
    protected HttpURLConnection startRequest(String endpoint) throws Exception {
        HttpURLConnection connection = super.startRequest(endpoint);
        if (connection != null) {
            if (reporter != null) {
                timeoutTask = new FlushOnTimeoutTimerTask(reporter);
                if (logger.isDebugEnabled()) {
                    logger.debug("Scheduling request timeout in {}", reporterConfiguration.getApiRequestTime());
                }
                timeoutTimer.schedule(timeoutTask, reporterConfiguration.getApiRequestTime().getMillis());
            }
        }
        return connection;
    }

    @Override
    public void endRequest() {
        cancelTimeout();
        super.endRequest();
    }

    @Override
    public void close() {
        super.close();
        logger.info("Reported events: {}", reported);
        logger.info("Dropped events: {}", dropped);
        timeoutTimer.cancel();
    }

    private static class FlushOnTimeoutTimerTask extends TimerTask {
        private static final Logger logger = LoggerFactory.getLogger(FlushOnTimeoutTimerTask.class);
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
