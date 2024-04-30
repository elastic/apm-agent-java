package co.elastic.apm.agent.universalprofiling;


import co.elastic.apm.agent.impl.transaction.Id;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.report.Reporter;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import com.lmax.disruptor.EventPoller;
import com.lmax.disruptor.EventTranslatorTwoArg;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.YieldingWaitStrategy;
import org.HdrHistogram.WriterReaderPhaser;

import java.util.concurrent.ConcurrentHashMap;

public class SpanProfilingSamplesCorrelator {

    private static final Logger logger = LoggerFactory.getLogger(SpanProfilingSamplesCorrelator.class);

    /**
     * Holds the currently active transactions by their span-ID.
     * Note that theoretically there could be a collision by having two transactions with different trace-IDs but the same span-ID.
     * However, this is highly unlikely (see <a href="https://en.wikipedia.org/wiki/Birthday_problem">birthday problem</a>) and even if
     * it were to happen, the only consequences would be a potentially incorrect correlation for the two colliding transactions.
     */
    private final ConcurrentHashMap<Id, Transaction> transactionsById = new ConcurrentHashMap<>();

    private final Reporter reporter;

    // Clock to use, can be swapped out for testing
    Clock nanoClock = Clock.SYSTEM_NANOTIME;

    // Visible for testing
    final RingBuffer<BufferedTransaction> delayedSpans;
    private final PeekingPoller<BufferedTransaction> delayedSpansPoller;

    private volatile long spanBufferDurationNanos;
    private volatile boolean shuttingDown = false;

    private final WriterReaderPhaser shutdownPhaser = new WriterReaderPhaser();

    public SpanProfilingSamplesCorrelator(
        int bufferCapacity,
        long initialSpanDelayNanos,
        Reporter reporter) {
        this.spanBufferDurationNanos = initialSpanDelayNanos;
        this.reporter = reporter;

        bufferCapacity = nextPowerOf2(bufferCapacity);
        // We use a wait strategy which doesn't involve signaling via condition variables
        // because we never block anyway (we use polling)
        delayedSpans =
            RingBuffer.createMultiProducer(
                BufferedTransaction::new, bufferCapacity, new YieldingWaitStrategy());
        EventPoller<BufferedTransaction> nonPeekingPoller = delayedSpans.newPoller();
        delayedSpans.addGatingSequences(nonPeekingPoller.getSequence());

        delayedSpansPoller = new PeekingPoller<>(nonPeekingPoller, BufferedTransaction::new);
    }

    public void setSpanBufferDurationNanos(long nanos) {
        if (nanos < 0) {
            throw new IllegalArgumentException("nanos must be positive but was " + nanos);
        }
        spanBufferDurationNanos = nanos;
    }

    public void onTransactionStart(Transaction transaction) {
        if (transaction.isSampled()) {
            transactionsById.put(transaction.getTraceContext().getId(), transaction);
        }
    }

    public void stopCorrelating(Transaction transaction) {
        transactionsById.remove(transaction.getTraceContext().getId());
    }

    public void reportOrBufferTransaction(Transaction transaction) {
        if (!transactionsById.containsKey(transaction.getTraceContext().getId())) {
            // transaction is not being correlated, e.g. because it was not sampled
            // therefore no need to buffer it
            reporter.report(transaction);
            return;
        }

        long criticalPhaseVal = shutdownPhaser.writerCriticalSectionEnter();
        try {
            if (spanBufferDurationNanos == 0 || shuttingDown) {
                reporter.report(transaction);
                return;
            }

            boolean couldPublish =
                delayedSpans.tryPublishEvent(BufferedTransaction.TRANSLATOR,
                    transaction,
                    nanoClock.getNanos());

            if (!couldPublish) {
                logger.warn("The following transaction could not be delayed for correlation due to a full buffer, it will be sent immediately, {0}",
                    transaction);
                reporter.report(transaction);
            }
        } finally {
            shutdownPhaser.writerCriticalSectionExit(criticalPhaseVal);
        }
    }

    public synchronized void correlate(
        Id traceId, Id transactionId, Id stackTraceId, int count) {
        Transaction tx = transactionsById.get(transactionId);
        if (tx != null) {
            // this branch should be true practically always unless there was a collision in transactionsById
            // nonetheless for the unlikely case that it happens, we at least prevent wrongly adding data to another transaction
            if (tx.getTraceContext().getTraceId().equals(traceId)) {
                for (int i = 0; i < count; i++) {
                    tx.addProfilerCorrelationStackTrace(stackTraceId);
                }
            }
        }
    }

    public synchronized void flushPendingBufferedSpans() {
        try {
            delayedSpansPoller.poll(
                bufferedSpan -> {
                    long elapsed = nanoClock.getNanos() - bufferedSpan.endNanoTimestamp;
                    if (elapsed >= spanBufferDurationNanos || shuttingDown) {
                        stopCorrelating(bufferedSpan.transaction);
                        reporter.report(bufferedSpan.transaction);
                        bufferedSpan.clear();
                        return true;
                    }
                    return false; // span is not yet ready to be sent
                });
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public synchronized void shutdownAndFlushAll() {
        shutdownPhaser.readerLock();
        try {
            shuttingDown = true; // This will cause new ended spans to not be buffered anymore

            // avoid race condition: we wait until we are
            // sure that no more spans will be added to the ringbuffer
            shutdownPhaser.flipPhase();
        } finally {
            shutdownPhaser.readerUnlock();
        }
        // This will flush all pending spans because shuttingDown=true
        flushPendingBufferedSpans();
    }

    private static class BufferedTransaction implements MoveableEvent<BufferedTransaction> {

        Transaction transaction;
        long endNanoTimestamp;

        @Override
        public void moveInto(BufferedTransaction other) {
            other.transaction = transaction;
            other.endNanoTimestamp = endNanoTimestamp;
            clear();
        }

        @Override
        public void clear() {
            transaction = null;
            endNanoTimestamp = -1;
        }

        public static final EventTranslatorTwoArg<BufferedTransaction, Transaction, Long> TRANSLATOR = new EventTranslatorTwoArg<BufferedTransaction, Transaction, Long>() {
            @Override
            public void translateTo(BufferedTransaction event, long sequence, Transaction transaction, Long timestamp) {
                event.transaction = transaction;
                event.endNanoTimestamp = timestamp;
            }
        };
    }

    private static int nextPowerOf2(int val) {
        int result = 2;
        while (result < val) {
            result *= 2;
        }
        return result;
    }
}

