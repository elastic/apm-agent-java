/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.agent.reactor;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import javax.annotation.Nullable;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class TracedSubscriberTest extends AbstractInstrumentationTest {

    private static final Scheduler SUBSCRIBE_SCHEDULER = Schedulers.newElastic("subscribe");
    private static final Scheduler PUBLISH_SCHEDULER = Schedulers.newElastic("publish");

    @Nullable
    private Transaction transaction;

    private static long mainThreadId;

    @BeforeAll
    static void testAutomaticHook() {
        // will trigger the 1st call to onAssembly
        Mono.just(1);

        assertThat(TracedSubscriber.isHookRegistered())
            .describedAs("hook should be registered automatically")
            .isTrue();
    }

    @BeforeEach
    void before() {
        mainThreadId = currentThreadId();

        // make debugging slightly more human friendly
        Hooks.onOperatorDebug();
    }

    @AfterEach
    void after() {
        if (transaction != null) {
            transaction.deactivate().end();
        }

        // ensure clean as new hooks setup for all tests (some might have removed it)
        TracedSubscriber.unregisterHooks();
        TracedSubscriber.registerHooks(tracer);

        flushGcExpiry(TracedSubscriber.getContextMap(), 2);
    }

    @Test
    void contextPropagation_SameThread_WorksImplicitly() {
        // when running within same thread, the context propagation works implicitly without hooks
        TracedSubscriber.unregisterHooks();

        transaction = startTestRootTransaction("root");

        Flux<TestObservation> flux = Flux.just(1, 2, 3)
            .publishOn(Schedulers.immediate())
            .map(TestObservation::capture);

        StepVerifier.create(flux)
            .expectNextMatches(inMainThread(transaction, 1))
            .expectNextMatches(inMainThread(transaction, 2))
            .expectNextMatches(inMainThread(transaction, 3))
            .verifyComplete();
    }

    @Test
    void noContext_without_root_transaction() {
        Flux<TestObservation> flux = Flux.just(1, 2, 3)
            .publishOn(Schedulers.elastic()) // publish events on another thread
            //
            .map(TestObservation::capture);

        StepVerifier.create(flux)
            .expectNextMatches(noActiveContext(1))
            .expectNextMatches(noActiveContext(2))
            .expectNextMatches(noActiveContext(3))
            .verifyComplete();
    }

    @Test
    void contextPropagation_DifferentThreads() {

        // we have a transaction active in current thread
        transaction = startTestRootTransaction("root");

        Flux<TestObservation> flux = Flux.just(1, 2, 3).log("input")
            // subscribe & publish on separate threads
            .subscribeOn(SUBSCRIBE_SCHEDULER)
            .publishOn(PUBLISH_SCHEDULER)
            //
            .map(TestObservation::capture);

        StepVerifier.create(flux.log("output"))
            .expectNextMatches(inOtherThread(transaction, 1))
            .expectNextMatches(inOtherThread(transaction, 2))
            .expectNextMatches(inOtherThread(transaction, 3))
            .verifyComplete();
    }

    @Test
    void contextPropagation_Flux_Map_Zip() {
        transaction = startTestRootTransaction("root");

        Flux<TestObservation> flux = Flux.just(1, 2, 3).log("input")
            // publish & subscribe on separate threads
            .subscribeOn(SUBSCRIBE_SCHEDULER)
            .publishOn(PUBLISH_SCHEDULER)
            //
            .zipWith(Flux.range(1, Integer.MAX_VALUE)
                    // explicitly set scheduler is required as range might use main thread otherwise
                    .subscribeOn(PUBLISH_SCHEDULER)
                    .publishOn(SUBSCRIBE_SCHEDULER),
                //
                (a, b) -> TestObservation.capture(a + b)
                    // perform checks inline because we only keep test observation from last map operation
                    .checkActiveContext(transaction)
                    .checkThread(false))
            .log("zip")
            //
            .map((i) -> TestObservation.capture(i.value));

        StepVerifier.create(flux.log())
            .expectNextMatches(inOtherThread(transaction, 2))
            .expectNextMatches(inOtherThread(transaction, 4))
            .expectNextMatches(inOtherThread(transaction, 6))
            .verifyComplete();

    }

    @Test
    void contextPropagation_Flux_error() {
        Throwable error = new RuntimeException("hello");

        transaction = startTestRootTransaction("root");

        Flux.error(error)
            .subscribeOn(PUBLISH_SCHEDULER)
            .publishOn(SUBSCRIBE_SCHEDULER)
            .subscribe(new BaseSubscriber<>() {

                @Override
                protected void hookOnError(Throwable throwable) {
                    assertThat(throwable).isSameAs(error);
                    checkActiveContext(transaction);
                }
            });
    }

    @Test
    void ignoreNoActiveContext() {
        assertThat(tracer.getActive()).isNull();

        // will throw an NPE due to trying to activate/deactivate a null context
        Flux.just(1, 2, 3)
            .subscribe();
    }

    private static long currentThreadId() {
        return Thread.currentThread().getId();
    }

    static Predicate<TestObservation> inMainThread(@Nullable AbstractSpan<?> expectedContext, int expectedValue) {
        return observation -> {
            observation.checkThread(true)
                .checkActiveContext(expectedContext)
                .checkValue(expectedValue);
            return true;
        };
    }

    static Predicate<TestObservation> inOtherThread(@Nullable AbstractSpan<?> expectedContext, int expectedValue) {
        return observation -> {
            observation
                .checkActiveContext(expectedContext)
                .checkValue(expectedValue)
                .checkThread(false);
            return true;
        };
    }

    static Predicate<TestObservation> noActiveContext(int expectedValue) {
        return observation -> {
            // when there is no active context, we don't have to ensure we are in another thread
            observation.checkActiveContext(null)
                .checkValue(expectedValue);
            return true;
        };
    }

    /**
     * Capture an 'observation' of current reactor state for testing:
     *
     * <ul>
     *     <li>current thread ID</li>
     *     <li>active context (if any)</li>
     *     <li>current value</li>
     * </ul>
     */
    private static class TestObservation {
        @Nullable
        private final AbstractSpan<?> activeContext;
        private final Long threadId;
        private final int value;

        private TestObservation(int value) {
            this.activeContext = tracer.getActive();
            this.threadId = currentThreadId();
            this.value = value;
        }

        public static TestObservation capture(int value) {
            return new TestObservation(value);
        }

        @Override
        public String toString() {
            return "TestObservation{" +
                "threadId=" + threadId +
                ", value=" + value +
                ", activeContext=" + activeContext +
                '}';
        }

        TestObservation checkThread(boolean mainThread) {
            if (mainThread) {
                assertThat(threadId)
                    .describedAs("should execute in main thread")
                    .isEqualTo(mainThreadId);
            } else {
                assertThat(threadId)
                    .describedAs("should not execute in main thread")
                    .isNotEqualTo(mainThreadId);
            }
            return this;
        }

        TestObservation checkActiveContext(@Nullable AbstractSpan<?> expectedActiveContext) {
            assertThat(activeContext)
                .describedAs("%s active context in thread %d", activeContext == null ? "missing" : "unexpected", threadId)
                .isEqualTo(expectedActiveContext);
            return this;
        }

        TestObservation checkValue(int expectedValue) {
            assertThat(value).isEqualTo(expectedValue);
            return this;
        }

    }

    private static void checkActiveContext(@Nullable AbstractSpan<?> expectedActive) {
        assertThat(tracer.getActive())
            .describedAs("active context not available")
            .isNotNull()
            .describedAs("active context is not the one we expect")
            .isSameAs(expectedActive);
    }

}
