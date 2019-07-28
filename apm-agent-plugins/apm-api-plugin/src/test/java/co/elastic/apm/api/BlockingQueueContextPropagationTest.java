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
package co.elastic.apm.api;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class BlockingQueueContextPropagationTest extends AbstractInstrumentationTest {

    private static BlockingQueue<ElasticApmQueueElementWrapper<CompletableFuture<String>>> blockingQueue;
    private static ExecutorService executorService;
    private static final long nanoTimeOffsetToEpoch = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis()) - System.nanoTime();

    @BeforeClass
    public static void setup() {
        blockingQueue = new ArrayBlockingQueue<>(5);
        executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            while (true) {
                try {
                    ElasticApmQueueElementWrapper<CompletableFuture<String>> element = blockingQueue.take();
                    Thread.sleep(100);
                    final Span span = element.getSpan();
                    if (!(span instanceof Transaction)) {
                        span.setStartTimestamp(TimeUnit.NANOSECONDS.toMicros(nanoTimeOffsetToEpoch + System.nanoTime()));
                    }
                    final CompletableFuture<String> result = element.getWrappedObject();
                    try (Scope scope = span.activate()) {
                        String spanId = ElasticApm.currentSpan().getId();
                        Thread.sleep(10);
                        span.end();
                        result.complete(spanId);
                    }
                } catch (InterruptedException e) {
                    // do nothing
                }
            }
        });
    }

    @AfterClass
    public static void tearDown() {
        blockingQueue.clear();
        executorService.shutdownNow();
    }

    @Test
    public void testAsyncTransactionDelegation() throws ExecutionException, InterruptedException {
        Transaction transaction = ElasticApm.startTransaction();
        long startTime = TimeUnit.NANOSECONDS.toMicros(nanoTimeOffsetToEpoch + System.nanoTime());
        transaction.setStartTimestamp(startTime);
        final CompletableFuture<String> result = new CompletableFuture<>();
        try (Scope scope = transaction.activate()) {
            blockingQueue.offer(new ElasticApmQueueElementWrapper<>(result, transaction));
        } catch (Exception e) {
            e.printStackTrace();
        }

        // important for waiting on the queue reader thread to finish the span
        assertThat(result.get()).isEqualTo(transaction.getId());

        co.elastic.apm.agent.impl.transaction.Transaction reportedTransaction = reporter.getFirstTransaction();
        assertThat(reportedTransaction).isNotNull();
        assertThat(reportedTransaction.getTraceContext().getId().toString()).isEqualTo(transaction.getId());
        assertThat(reportedTransaction.getTimestamp()).isEqualTo(startTime);
        assertThat(reportedTransaction.getDuration()).isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toMicros(110));
        assertThat(reporter.getSpans()).isEmpty();
    }

    @Test
    public void testAsyncSpanDelegation() throws ExecutionException, InterruptedException {
        Transaction transaction = ElasticApm.startTransaction();
        long startTime = TimeUnit.NANOSECONDS.toMicros(nanoTimeOffsetToEpoch + System.nanoTime());
        transaction.setStartTimestamp(startTime);
        final CompletableFuture<String> result = new CompletableFuture<>();
        String asyncSpanId = null;
        try (Scope scope = transaction.activate()) {
            final Span asyncSpan = ElasticApm.currentSpan().startSpan("async", "blocking-queue", null);
            asyncSpanId = asyncSpan.getId();
            blockingQueue.offer(new ElasticApmQueueElementWrapper<>(result, asyncSpan));
        } catch (Exception e) {
            e.printStackTrace();
        }
        transaction.end();

        // important for waiting on the queue reader thread to finish the span
        assertThat(result.get()).isEqualTo(asyncSpanId);

        co.elastic.apm.agent.impl.transaction.Transaction reportedTransaction = reporter.getFirstTransaction();
        assertThat(reportedTransaction).isNotNull();
        long transactionTimestamp = reportedTransaction.getTimestamp();
        assertThat(transactionTimestamp).isEqualTo(startTime);
        assertThat(reportedTransaction.getDuration()).isLessThan(TimeUnit.MILLISECONDS.toMicros(110));

        co.elastic.apm.agent.impl.transaction.Span reportedSpan = reporter.getFirstSpan();
        assertThat(reportedSpan.getTraceContext().getTraceId()).isEqualTo(reportedTransaction.getTraceContext().getTraceId());
        assertThat(reportedSpan).isNotNull();
        assertThat(reportedSpan.getType()).isEqualTo("async");
        assertThat(reportedSpan.getTimestamp() - transactionTimestamp).isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toMicros(100));
        assertThat(reportedSpan.getDuration()).isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toMicros(10));
    }

    public static class ElasticApmQueueElementWrapper<T> {
        private final T wrappedObject;
        private final Span span;

        private ElasticApmQueueElementWrapper(T wrappedObject, Span span) {
            this.wrappedObject = wrappedObject;
            this.span = span;
        }

        public T getWrappedObject() {
            return wrappedObject;
        }

        public Span getSpan() {
            return span;
        }
    }
}
