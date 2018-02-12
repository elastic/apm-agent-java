package co.elastic.apm.report;

import co.elastic.apm.intake.Process;
import co.elastic.apm.intake.Service;
import co.elastic.apm.intake.System;
import co.elastic.apm.intake.transactions.Transaction;
import co.elastic.apm.util.ExecutorUtils;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventTranslator;
import com.lmax.disruptor.EventTranslatorOneArg;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;
import okhttp3.OkHttpClient;

import java.io.Closeable;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static co.elastic.apm.report.Reporter.ReportingEvent.ReportingEventType.FLUSH;

public class Reporter implements Closeable {

    private static final int FLUSH_INTERVAL_SECONDS = 10;
    private static final int REPORTER_QUEUE_LENGTH = 1024;
    private static final EventTranslatorOneArg<ReportingEvent, Transaction> TRANSACTION_EVENT_TRANSLATOR = new EventTranslatorOneArg<>() {
        @Override
        public void translateTo(ReportingEvent event, long sequence, Transaction t) {
            event.transaction = t;
        }
    };
    private static final EventTranslator<ReportingEvent> FLUSH_EVENT_TRANSLATOR = new EventTranslator<>() {
        @Override
        public void translateTo(ReportingEvent event, long sequence) {
            event.type = FLUSH;
        }
    };

    private final Disruptor<ReportingEvent> disruptor;
    private final ScheduledThreadPoolExecutor flushScheduler;

    public Reporter(Service server, Process process, System system, String apmServerUrl, OkHttpClient httpClient) {
        disruptor = new Disruptor<>(new TransactionEventFactory(), REPORTER_QUEUE_LENGTH, DaemonThreadFactory.INSTANCE);
        disruptor.handleEventsWith(new ReportingEventHandler(server, process, system, apmServerUrl, httpClient));
        disruptor.start();
        flushScheduler = ExecutorUtils.createSingleThreadSchedulingDeamonPool("elastic-apm-transaction-flusher", 1);
        flushScheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                scheduleFlush();
            }
        }, FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void report(Transaction transaction) {
        disruptor.publishEvent(TRANSACTION_EVENT_TRANSLATOR, transaction);
    }

    public void scheduleFlush() {
        disruptor.publishEvent(FLUSH_EVENT_TRANSLATOR);
    }

    public void close() {
        disruptor.shutdown();
        flushScheduler.shutdown();
    }

    static class ReportingEvent {
        Transaction transaction;
        ReportingEventType type;

        public void setTransaction(Transaction transaction) {
            this.type = ReportingEventType.TRANSACTION;
            this.transaction = transaction;
        }

        public void resetState() {
            this.transaction = null;
            this.type = null;
        }

        enum ReportingEventType {
            FLUSH, TRANSACTION
        }
    }

    static class TransactionEventFactory implements EventFactory<ReportingEvent> {
        @Override
        public ReportingEvent newInstance() {
            return new ReportingEvent();
        }
    }
}
