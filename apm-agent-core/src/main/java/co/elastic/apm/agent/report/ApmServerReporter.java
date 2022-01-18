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

import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.report.disruptor.ExponentionallyIncreasingSleepingWaitStrategy;
import co.elastic.apm.agent.util.MathUtils;
import co.elastic.apm.agent.common.ThreadUtils;
import com.dslplatform.json.JsonWriter;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventTranslator;
import com.lmax.disruptor.EventTranslatorOneArg;
import com.lmax.disruptor.IgnoreExceptionHandler;
import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * This reporter asynchronously reports {@link Transaction}s to the APM server
 * <p>
 * It uses a Disruptor/ring buffer to decouple the {@link Transaction} producing threads from the thread that actually sends the payload
 * </p>
 */
public class ApmServerReporter implements Reporter {

    private static final Logger logger = LoggerFactory.getLogger(ApmServerReporter.class);

    private static final EventTranslatorOneArg<ReportingEvent, Transaction> TRANSACTION_EVENT_TRANSLATOR = new EventTranslatorOneArg<ReportingEvent, Transaction>() {
        @Override
        public void translateTo(ReportingEvent event, long sequence, Transaction t) {
            event.setTransaction(t);
        }
    };
    private static final EventTranslatorOneArg<ReportingEvent, Span> SPAN_EVENT_TRANSLATOR = new EventTranslatorOneArg<ReportingEvent, Span>() {
        @Override
        public void translateTo(ReportingEvent event, long sequence, Span s) {
            event.setSpan(s);
        }
    };
    private static final EventTranslatorOneArg<ReportingEvent, Thread> END_REQUEST_EVENT_TRANSLATOR = new EventTranslatorOneArg<ReportingEvent, Thread>() {
        @Override
        public void translateTo(ReportingEvent event, long sequence, @Nullable Thread unparkAfterProcessed) {
            event.setEndRequestEvent();
            event.unparkAfterProcessed(unparkAfterProcessed);
        }
    };
    private static final EventTranslator<ReportingEvent> WAKEUP_EVENT_TRANSLATOR = new EventTranslator<ReportingEvent>() {
        @Override
        public void translateTo(ReportingEvent event, long sequence) {
            event.setWakeupEvent();
        }
    };
    private static final EventTranslatorOneArg<ReportingEvent, ErrorCapture> ERROR_EVENT_TRANSLATOR = new EventTranslatorOneArg<ReportingEvent, ErrorCapture>() {
        @Override
        public void translateTo(ReportingEvent event, long sequence, ErrorCapture error) {
            event.setError(error);
        }
    };
    private static final EventTranslatorOneArg<ReportingEvent, JsonWriter> JSON_WRITER_EVENT_TRANSLATOR = new EventTranslatorOneArg<ReportingEvent, JsonWriter>() {
        @Override
        public void translateTo(ReportingEvent event, long sequence, JsonWriter jsonWriter) {
            event.setJsonWriter(jsonWriter);
        }
    };
    private static final EventTranslatorOneArg<ReportingEvent, Thread> SHUTDOWN_EVENT_TRANSLATOR = new EventTranslatorOneArg<ReportingEvent, Thread>() {
        @Override
        public void translateTo(ReportingEvent event, long sequence, @Nullable Thread unparkAfterProcessed) {
            event.shutdownEvent();
            event.unparkAfterProcessed(Thread.currentThread());
        }
    };

    private final Disruptor<ReportingEvent> disruptor;
    private final AtomicLong dropped = new AtomicLong();
    private final boolean dropTransactionIfQueueFull;
    private final ReportingEventHandler reportingEventHandler;
    private final boolean syncReport;

    public ApmServerReporter(boolean dropTransactionIfQueueFull, ReporterConfiguration reporterConfiguration,
                             ReportingEventHandler reportingEventHandler) {
        this.dropTransactionIfQueueFull = dropTransactionIfQueueFull;
        this.syncReport = reporterConfiguration.isReportSynchronously();
        disruptor = new Disruptor<>(new TransactionEventFactory(), MathUtils.getNextPowerOf2(reporterConfiguration.getMaxQueueSize()), new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                thread.setName(ThreadUtils.addElasticApmThreadPrefix("server-reporter"));
                return thread;
            }
        }, ProducerType.MULTI, new ExponentionallyIncreasingSleepingWaitStrategy(100_000, 10_000_000));
        this.reportingEventHandler = reportingEventHandler;
        disruptor.setDefaultExceptionHandler(new IgnoreExceptionHandler());
        disruptor.handleEventsWith(this.reportingEventHandler);
    }

    @Override
    public void start() {
        disruptor.start();
        reportingEventHandler.init(this);
    }

    @Override
    public void report(Transaction transaction) {
        if (!tryAddEventToRingBuffer(transaction, TRANSACTION_EVENT_TRANSLATOR)) {
            transaction.decrementReferences();
        }
        if (syncReport) {
            flush();
        }
    }

    @Override
    public void report(Span span) {
        if (!tryAddEventToRingBuffer(span, SPAN_EVENT_TRANSLATOR)) {
            span.decrementReferences();
        }
        if (syncReport) {
            flush();
        }
    }

    @Override
    public boolean flush() {
        return flush(-1, TimeUnit.NANOSECONDS);
    }

    @Override
    public long getDropped() {
        return dropped.get() + reportingEventHandler.getDropped();
    }

    @Override
    public long getReported() {
        return reportingEventHandler.getReported();
    }

    public void scheduleWakeupEvent() {
        disruptor.getRingBuffer().tryPublishEvent(WAKEUP_EVENT_TRANSLATOR);
    }

    @Override
    public boolean flush(long timeout, TimeUnit unit) {
        return publishAndWaitForEvent(timeout, unit, END_REQUEST_EVENT_TRANSLATOR);
    }

    private boolean publishAndWaitForEvent(long timeout, TimeUnit unit, EventTranslatorOneArg<ReportingEvent, Thread> eventTranslator) {
        if (!reportingEventHandler.isHealthy()) {
            return false;
        }
        ReportingEventHandler reportingEventHandler = this.reportingEventHandler;
        long startNs = System.nanoTime();
        long thresholdNs;
        if (timeout < 0) {
            thresholdNs = Long.MAX_VALUE;
        } else {
            thresholdNs = unit.toNanos(timeout) + startNs;
        }
        do {
            try {
                long sequence = disruptor.getRingBuffer().tryNext();
                try {
                    eventTranslator.translateTo(disruptor.get(sequence), sequence, Thread.currentThread());
                } finally {
                    disruptor.getRingBuffer().publish(sequence);
                }
                return waitForEventProcessed(sequence, thresholdNs);
            } catch (InsufficientCapacityException e) {
                LockSupport.parkNanos(100_000);
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
        } while (System.nanoTime() < thresholdNs && reportingEventHandler.isHealthy());
        return false;
    }

    private boolean waitForEventProcessed(long sequence, long thresholdNs) {
        ReportingEventHandler reportingEventHandler = this.reportingEventHandler;
        for (long nowNs = System.nanoTime();
             nowNs < thresholdNs && reportingEventHandler.isHealthy() && !reportingEventHandler.isProcessed(sequence);
             nowNs = System.nanoTime()) {

            // periodically waking up to check if the connection turned unhealthy
            // after the event has been published to the ring buffer
            int minPeriodicWakeupNs = 10_000_000;
            // after the event has been processed, this thread will be unparked and we'll return immediately
            LockSupport.parkNanos(Math.min(minPeriodicWakeupNs, thresholdNs - nowNs));
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
        }
        return reportingEventHandler.isProcessed(sequence);
    }

    @Override
    public void close() {
        logger.info("dropped events because of full queue: {}", dropped.get());
        publishAndWaitForEvent(5, TimeUnit.SECONDS, SHUTDOWN_EVENT_TRANSLATOR);
        reportingEventHandler.close();
        try {
            disruptor.shutdown(1, TimeUnit.SECONDS);
        } catch (com.lmax.disruptor.TimeoutException e) {
            logger.warn("Timeout while shutting down disruptor");
        }
    }

    @Override
    public void report(ErrorCapture error) {
        if (!tryAddEventToRingBuffer(error, ERROR_EVENT_TRANSLATOR)) {
            error.recycle();
        }
        if (syncReport) {
            flush();
        }
    }

    @Override
    public void report(JsonWriter jsonWriter) {
        if (jsonWriter.size() == 0) {
            return;
        }
        tryAddEventToRingBuffer(jsonWriter, JSON_WRITER_EVENT_TRANSLATOR);
        if (syncReport) {
            flush();
        }
    }

    private <E> boolean tryAddEventToRingBuffer(E event, EventTranslatorOneArg<ReportingEvent, E> eventTranslator) {
        if (dropTransactionIfQueueFull) {
            boolean queueFull = !disruptor.getRingBuffer().tryPublishEvent(eventTranslator, event);
            if (queueFull) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Could not add {} {} to ring buffer as no slots are available", event.getClass().getSimpleName(), event);
                }
                dropped.incrementAndGet();
                return false;
            }
        } else {
            disruptor.getRingBuffer().publishEvent(eventTranslator, event);
        }
        return true;
    }

    static class TransactionEventFactory implements EventFactory<ReportingEvent> {
        @Override
        public ReportingEvent newInstance() {
            return new ReportingEvent();
        }
    }
}
