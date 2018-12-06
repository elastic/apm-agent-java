/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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

import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.objectpool.Recyclable;
import co.elastic.apm.util.MathUtils;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventTranslator;
import com.lmax.disruptor.EventTranslatorOneArg;
import com.lmax.disruptor.IgnoreExceptionHandler;
import com.lmax.disruptor.PhasedBackoffWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This reporter asynchronously reports {@link Transaction}s to the APM server
 * <p>
 * It uses a Disruptor/ring buffer to decouple the {@link Transaction} producing threads from the thread that actually sends the payload
 * </p>
 */
public class ApmServerReporter implements Reporter {

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
    private static final EventTranslator<ReportingEvent> FLUSH_EVENT_TRANSLATOR = new EventTranslator<ReportingEvent>() {
        @Override
        public void translateTo(ReportingEvent event, long sequence) {
            event.setFlushEvent();
        }
    };
    private static final EventTranslatorOneArg<ReportingEvent, ErrorCapture> ERROR_EVENT_TRANSLATOR = new EventTranslatorOneArg<ReportingEvent, ErrorCapture>() {
        @Override
        public void translateTo(ReportingEvent event, long sequence, ErrorCapture error) {
            event.setError(error);
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
                thread.setName("apm-reporter");
                return thread;
            }
        }, ProducerType.MULTI, PhasedBackoffWaitStrategy.withLiteLock(1, 10, TimeUnit.MILLISECONDS));
        this.reportingEventHandler = reportingEventHandler;
        disruptor.setDefaultExceptionHandler(new IgnoreExceptionHandler());
        disruptor.handleEventsWith(this.reportingEventHandler);
        disruptor.start();
        reportingEventHandler.init(this);
    }

    @Override
    public void report(Transaction transaction) {
        if (!tryAddEventToRingBuffer(transaction, TRANSACTION_EVENT_TRANSLATOR)) {
            transaction.recycle();
        }
        if (syncReport) {
            waitForFlush();
        }
    }

    @Override
    public void report(Span span) {
        if (!tryAddEventToRingBuffer(span, SPAN_EVENT_TRANSLATOR)) {
            span.recycle();
        }
        if (syncReport) {
            waitForFlush();
        }
    }

    private void waitForFlush() {
        try {
            flush().get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public long getDropped() {
        return dropped.get() + reportingEventHandler.getDropped();
    }

    @Override
    public long getReported() {
        return reportingEventHandler.getReported();
    }

    /**
     * Flushes pending {@link ErrorCapture}s and {@link Transaction}s to the APM server.
     * <p>
     * This method may block for a while until a slot in the ring buffer becomes available.
     * </p>
     *
     * @return A {@link Future} which resolves when the flush has been executed.
     */
    @Override
    public Future<Void> flush() {
        disruptor.publishEvent(FLUSH_EVENT_TRANSLATOR);
        final long cursor = disruptor.getCursor();
        return new Future<Void>() {
            private volatile boolean cancelled = false;

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                if (isDone()) {
                    return false;
                }
                disruptor.get(cursor).resetState();
                // the volatile write also ensures visibility of the resetState() in other threads
                cancelled = true;
                return true;
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }

            @Override
            public boolean isDone() {
                return isEventProcessed(cursor);
            }

            @Override
            public Void get() throws InterruptedException {
                while (!isDone()) {
                    Thread.sleep(1);
                }
                return null;
            }

            /*
             * This might not a very elegant or efficient implementation but it is only intended to be used in tests anyway
             */
            @Override
            public Void get(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
                for (; timeout > 0 && !isDone(); timeout--) {
                    Thread.sleep(1);
                }
                if (!isDone()) {
                    throw new TimeoutException();
                }
                return null;
            }
        };
    }

    private boolean isEventProcessed(long sequence) {
        return disruptor.getSequenceValueFor(reportingEventHandler) >= sequence;
    }

    @Override
    public void close() {
        disruptor.shutdown();
        reportingEventHandler.close();
    }

    @Override
    public void report(ErrorCapture error) {
        if (!tryAddEventToRingBuffer(error, ERROR_EVENT_TRANSLATOR)) {
            error.recycle();
        }
        if (syncReport) {
            waitForFlush();
        }
    }

    private <E extends Recyclable> boolean tryAddEventToRingBuffer(E event, EventTranslatorOneArg<ReportingEvent, E> eventTranslator) {
        if (dropTransactionIfQueueFull) {
            boolean queueFull = !disruptor.getRingBuffer().tryPublishEvent(eventTranslator, event);
            if (queueFull) {
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
