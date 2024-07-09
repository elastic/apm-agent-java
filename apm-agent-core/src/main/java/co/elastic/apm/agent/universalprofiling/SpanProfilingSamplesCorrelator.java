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
package co.elastic.apm.agent.universalprofiling;


import co.elastic.apm.agent.impl.transaction.IdImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import co.elastic.apm.agent.report.Reporter;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import com.lmax.disruptor.EventFactory;
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
    private final ConcurrentHashMap<IdImpl, TransactionImpl> transactionsById = new ConcurrentHashMap<>();

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
        EventFactory<BufferedTransaction> eventFactory = new EventFactory<BufferedTransaction>() {
            @Override
            public BufferedTransaction newInstance() {
                return new BufferedTransaction();
            }
        };
        delayedSpans = RingBuffer.createMultiProducer(eventFactory, bufferCapacity, new YieldingWaitStrategy());
        EventPoller<BufferedTransaction> nonPeekingPoller = delayedSpans.newPoller();
        delayedSpans.addGatingSequences(nonPeekingPoller.getSequence());

        delayedSpansPoller = new PeekingPoller<>(nonPeekingPoller, eventFactory);
    }

    public void setSpanBufferDurationNanos(long nanos) {
        if (nanos < 0) {
            throw new IllegalArgumentException("nanos must be positive but was " + nanos);
        }
        spanBufferDurationNanos = nanos;
    }

    public void onTransactionStart(TransactionImpl transaction) {
        if (transaction.isSampled()) {
            transactionsById.put(transaction.getTraceContext().getId(), transaction);
        }
    }

    public void stopCorrelating(TransactionImpl transaction) {
        transactionsById.remove(transaction.getTraceContext().getId());
    }

    public void reportOrBufferTransaction(TransactionImpl transaction) {
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
        IdImpl traceId, IdImpl transactionId, IdImpl stackTraceId, int count) {
        TransactionImpl tx = transactionsById.get(transactionId);
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

    private final PeekingPoller.Handler<BufferedTransaction> BUFFERED_TRANSACTION_HANDLER = new PeekingPoller.Handler<BufferedTransaction>() {
        @Override
        public boolean handleEvent(BufferedTransaction bufferedSpan) {
            long elapsed = nanoClock.getNanos() - bufferedSpan.endNanoTimestamp;
            if (elapsed >= spanBufferDurationNanos || shuttingDown) {
                stopCorrelating(bufferedSpan.transaction);
                reporter.report(bufferedSpan.transaction);
                bufferedSpan.clear();
                return true;
            }
            return false; // span is not yet ready to be sent
        }
    };

    public synchronized void flushPendingBufferedSpans() {
        try {
            delayedSpansPoller.poll(BUFFERED_TRANSACTION_HANDLER);
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

        TransactionImpl transaction;
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

        public static final EventTranslatorTwoArg<BufferedTransaction, TransactionImpl, Long> TRANSLATOR = new EventTranslatorTwoArg<BufferedTransaction, TransactionImpl, Long>() {
            @Override
            public void translateTo(BufferedTransaction event, long sequence, TransactionImpl transaction, Long timestamp) {
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

