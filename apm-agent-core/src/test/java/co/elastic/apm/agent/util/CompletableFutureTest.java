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
package co.elastic.apm.agent.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class CompletableFutureTest {

    public static final int NUM_THREADS = 100;

    private static final ExecutorService executorService = Executors.newFixedThreadPool(NUM_THREADS);
    private static final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(NUM_THREADS);

    private final AtomicInteger isCancelledCounter = new AtomicInteger();
    private final AtomicInteger isDoneCounter = new AtomicInteger();
    private final AtomicInteger valueObtainedCounter = new AtomicInteger();
    private final AtomicInteger timeoutExceptionCounter = new AtomicInteger();
    private final AtomicInteger cancellationExceptionCounter = new AtomicInteger();
    private final AtomicInteger started = new AtomicInteger();
    private final AtomicInteger finished = new AtomicInteger();
    private final AtomicInteger successCounter = new AtomicInteger();
    private final AtomicInteger failureCounter = new AtomicInteger();

    @BeforeEach
    void reset() {
        isCancelledCounter.set(0);
        isDoneCounter.set(0);
        valueObtainedCounter.set(0);
        timeoutExceptionCounter.set(0);
        cancellationExceptionCounter.set(0);
        started.set(0);
        finished.set(0);
        successCounter.set(0);
        failureCounter.set(0);
    }

    @Test
    void testCancelBeforeStart() {
        final CompletableFuture<Object> completableFuture = new CompletableFuture<>();
        for (int i = 0; i < NUM_THREADS; i++) {
            scheduledExecutorService.schedule(new CompletableFutureTester(completableFuture, 5), 100, TimeUnit.MILLISECONDS);
        }
        assertThat(completableFuture.cancel(false)).isTrue();
        assertThat(completableFuture.cancel(false)).isFalse();
        await()
            .timeout(1, TimeUnit.SECONDS)
            .untilAsserted(() -> assertThat(finished.get()).isEqualTo(NUM_THREADS));
        assertThat(cancellationExceptionCounter.get()).isEqualTo(NUM_THREADS);
        assertThat(isCancelledCounter.get()).isEqualTo(NUM_THREADS);
        assertThat(completableFuture.complete(new Object())).isFalse();
        assertThat(isDoneCounter.get()).isEqualTo(NUM_THREADS);
        assertThat(valueObtainedCounter.get()).isEqualTo(0);
    }

    @Test
    void testCancelBeforeEnd() {
        final CompletableFuture<Object> completableFuture = new CompletableFuture<>();
        for (int i = 0; i < NUM_THREADS; i++) {
            executorService.submit(new CompletableFutureTester(completableFuture, 0));
        }
        await()
            .pollDelay(0, TimeUnit.MILLISECONDS)
            .pollInterval(1, TimeUnit.MILLISECONDS)
            .timeout(100, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> assertThat(started.get()).isGreaterThan(0));
        assertThat(finished.get()).isEqualTo(0);
        assertThat(completableFuture.cancel(false)).isTrue();
        assertThat(completableFuture.complete(new Object())).isFalse();
        await()
            .timeout(200, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> assertThat(finished.get()).isEqualTo(NUM_THREADS));
        assertThat(cancellationExceptionCounter.get()).isEqualTo(NUM_THREADS);
        assertThat(isCancelledCounter.get()).isEqualTo(NUM_THREADS);
        assertThat(isDoneCounter.get()).isEqualTo(NUM_THREADS);
        assertThat(valueObtainedCounter.get()).isEqualTo(0);
    }

    @Test
    void testUnlimitedGet() throws TimeoutException, InterruptedException {
        final CompletableFuture<Object> completableFuture = new CompletableFuture<>();
        for (int i = 0; i < NUM_THREADS; i++) {
            executorService.submit(new CompletableFutureTester(completableFuture, 0));
        }
        assertThatThrownBy(() -> completableFuture.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        assertThat(finished.get()).isEqualTo(0);
        assertThat(started.get()).isEqualTo(NUM_THREADS);
        Object value = new Object();
        assertThat(completableFuture.complete(value)).isTrue();
        assertThat(completableFuture.complete(new Object())).isFalse();
        assertThat(completableFuture.cancel(false)).isFalse();
        await()
            .timeout(200, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> assertThat(finished.get()).isEqualTo(NUM_THREADS));

        assertThat(cancellationExceptionCounter.get()).isEqualTo(0);
        assertThat(isCancelledCounter.get()).isEqualTo(0);
        assertThat(isDoneCounter.get()).isEqualTo(NUM_THREADS);
        assertThat(valueObtainedCounter.get()).isEqualTo(NUM_THREADS);
        assertThat(completableFuture.get(0, TimeUnit.MILLISECONDS)).isEqualTo(value);
    }

    @Test
    void testTimeLimitedGet_completion() {
        final CompletableFuture<Object> completableFuture = new CompletableFuture<>();
        for (int i = 0; i < NUM_THREADS; i++) {
            executorService.submit(new CompletableFutureTester(completableFuture, 100));
        }
        assertThat(finished.get()).isEqualTo(0);
        assertThat(completableFuture.complete(new Object())).isTrue();
        assertThat(completableFuture.complete(new Object())).isFalse();
        assertThat(completableFuture.cancel(false)).isFalse();
        await()
            .timeout(500, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> assertThat(finished.get()).isEqualTo(NUM_THREADS));

        assertThat(cancellationExceptionCounter.get()).isEqualTo(0);
        assertThat(isCancelledCounter.get()).isEqualTo(0);
        assertThat(isDoneCounter.get()).isEqualTo(NUM_THREADS);
        assertThat(valueObtainedCounter.get()).isEqualTo(NUM_THREADS);
    }

    @Test
    void testTimeLimitedGet_timeout() {
        final CompletableFuture<Object> completableFuture = new CompletableFuture<>();
        for (int i = 0; i < NUM_THREADS; i++) {
            executorService.submit(new CompletableFutureTester(completableFuture, 100));
        }
        assertThat(finished.get()).isEqualTo(0);
        await()
            .timeout(500, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> assertThat(finished.get()).isEqualTo(NUM_THREADS));
        assertThat(cancellationExceptionCounter.get()).isEqualTo(0);
        assertThat(isCancelledCounter.get()).isEqualTo(0);
        assertThat(isDoneCounter.get()).isEqualTo(0);
        assertThat(valueObtainedCounter.get()).isEqualTo(0);
        assertThat(timeoutExceptionCounter.get()).isEqualTo(NUM_THREADS);
    }

    @Test
    void testNullValueCompletion() throws TimeoutException, InterruptedException {
        final CompletableFuture<Object> completableFuture = new CompletableFuture<>();
        for (int i = 0; i < NUM_THREADS; i++) {
            executorService.submit(new CompletableFutureTester(completableFuture, 0));
        }
        assertThatThrownBy(() -> completableFuture.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        assertThat(finished.get()).isEqualTo(0);
        assertThat(completableFuture.complete(null)).isTrue();
        assertThat(completableFuture.complete(new Object())).isFalse();
        assertThat(completableFuture.isDone()).isTrue();
        assertThat(completableFuture.cancel(false)).isFalse();
        await()
            .timeout(200, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> assertThat(finished.get()).isEqualTo(NUM_THREADS));

        assertThat(cancellationExceptionCounter.get()).isEqualTo(0);
        assertThat(isCancelledCounter.get()).isEqualTo(0);
        assertThat(isDoneCounter.get()).isEqualTo(NUM_THREADS);
        assertThat(valueObtainedCounter.get()).isEqualTo(NUM_THREADS);
        assertThat(completableFuture.get(0, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void testCompleteContention() throws TimeoutException, InterruptedException {
        final CompletableFuture<Object> completableFuture = new CompletableFuture<>();
        for (int i = 0; i < NUM_THREADS; i++) {
            scheduledExecutorService.schedule(
                new ContentionTester(() -> completableFuture.complete(new Object()), successCounter, failureCounter),
                10, TimeUnit.MILLISECONDS
            );
        }
        await()
            .timeout(200, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> assertThat(finished.get()).isEqualTo(NUM_THREADS));
        assertThat(successCounter.get()).isEqualTo(1);
        assertThat(failureCounter.get()).isEqualTo(NUM_THREADS - 1);
        assertThat(completableFuture.isDone()).isTrue();
        assertThat(completableFuture.isCancelled()).isFalse();
        assertThat(completableFuture.get(0, TimeUnit.MILLISECONDS)).isNotNull();
    }

    @Test
    void testCancelContention() {
        final CompletableFuture<Object> completableFuture = new CompletableFuture<>();
        for (int i = 0; i < NUM_THREADS; i++) {
            scheduledExecutorService.schedule(
                new ContentionTester(() -> completableFuture.cancel(false), successCounter, failureCounter),
                10, TimeUnit.MILLISECONDS
            );
        }
        await()
            .timeout(200, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> assertThat(finished.get()).isEqualTo(NUM_THREADS));
        assertThat(successCounter.get()).isEqualTo(1);
        assertThat(failureCounter.get()).isEqualTo(NUM_THREADS - 1);
        assertThat(completableFuture.isDone()).isTrue();
        assertThat(completableFuture.isCancelled()).isTrue();
        assertThatThrownBy(() -> assertThat(completableFuture.get(0, TimeUnit.MILLISECONDS))).isInstanceOf(CancellationException.class);
    }

    private class CompletableFutureTester implements Runnable {

        private final CompletableFuture<Object> completableFuture;
        private final long waitTimeMillis;

        private CompletableFutureTester(CompletableFuture<Object> completableFuture, long waitTimeMillis) {
            this.completableFuture = completableFuture;
            this.waitTimeMillis = waitTimeMillis;
        }

        @Override
        public void run() {
            started.incrementAndGet();

            try {
                if (waitTimeMillis > 0) {
                    completableFuture.get(waitTimeMillis, TimeUnit.MILLISECONDS);
                } else {
                    completableFuture.get();
                }
                valueObtainedCounter.incrementAndGet();
            } catch (CancellationException e) {
                cancellationExceptionCounter.incrementAndGet();
            } catch (TimeoutException e) {
                timeoutExceptionCounter.incrementAndGet();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                throw new IllegalStateException();
            }

            if (completableFuture.isCancelled()) {
                isCancelledCounter.incrementAndGet();
            }

            if (completableFuture.isDone()) {
                isDoneCounter.incrementAndGet();
            }

            finished.incrementAndGet();
        }
    }

    private class ContentionTester implements Runnable {

        private final Callable<Boolean> callable;
        private final AtomicInteger successCounter;
        private final AtomicInteger failureCounter;

        private ContentionTester(Callable<Boolean> callable, AtomicInteger successCounter, AtomicInteger failureCounter) {
            this.callable = callable;
            this.successCounter = successCounter;
            this.failureCounter = failureCounter;
        }

        @Override
        public void run() {
            try {
                Boolean success = callable.call();
                if (success) {
                    successCounter.incrementAndGet();
                } else {
                    failureCounter.incrementAndGet();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            finished.incrementAndGet();
        }
    }
}
