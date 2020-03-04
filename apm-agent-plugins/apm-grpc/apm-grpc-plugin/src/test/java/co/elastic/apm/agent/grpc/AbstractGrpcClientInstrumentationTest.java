/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent.grpc;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.grpc.testapp.GrpcApp;
import co.elastic.apm.agent.grpc.testapp.GrpcAppProvider;
import co.elastic.apm.agent.impl.transaction.EpochTickClock;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.byLessThan;

public abstract class AbstractGrpcClientInstrumentationTest extends AbstractInstrumentationTest {

    private static final Logger logger = LoggerFactory.getLogger(AbstractGrpcClientInstrumentationTest.class);

    private GrpcApp app;

    public abstract GrpcAppProvider getAppProvider();

    @BeforeEach
    void beforeEach() throws Exception {
        app = GrpcTest.getApp(getAppProvider());
        app.start();

        tracer.startRootTransaction(AbstractGrpcClientInstrumentationTest.class.getClassLoader())
            .withName("Test gRPC client")
            .withType("test")
            .activate();
    }

    @AfterEach
    void afterEach() throws Exception {
        Transaction transaction = tracer.currentTransaction();

        if (transaction != null) {
            transaction.deactivate()
                .end();
        }

        try {
            // make sure we do not leave anything behind
            reporter.assertRecycledAfterDecrementingReferences();

            // use a try/finally block here to make sure that if the assertion above fails
            // we do not have a side effect on other tests execution by leaving app running.
        } finally {
            reporter.reset();
            app.stop();
        }

    }


    @Test
    public void simpleCall() {
        doSimpleCall("bob");

        tracer.currentTransaction().deactivate().end();

        Transaction transaction = reporter.getFirstTransaction();
        assertThat(transaction).isNotNull();

        Span span = reporter.getFirstSpan();
        checkSpan(span);

    }

    private void checkSpan(Span span) {
        assertThat(span.getType()).isEqualTo("external");
        assertThat(span.getSubtype()).isEqualTo("grpc");
        assertThat(span.getNameAsString()).isEqualTo("helloworld.Hello/SayHello");
    }

    @Test
    public void simpleCallOutsideTransactionShouldBeIgnored() {

        // terminate transaction early
        tracer.currentTransaction().deactivate().end();

        // no span should be captured as no transaction is active
        doSimpleCall("joe");

        assertThat(reporter.getSpans()).isEmpty();

    }

    private void doSimpleCall(String name) {
        assertThat(app.sayHello(name, 0))
            .isEqualTo(String.format("hello(%s)", name));
    }

    @Test
    public void cancelClientCall() throws Exception {

        EpochTickClock clock = new EpochTickClock();
        clock.init();

        CyclicBarrier startProcessing = new CyclicBarrier(2);
        CyclicBarrier endProcessing = new CyclicBarrier(2);
        app.getServer().useBarriersForProcessing(startProcessing, endProcessing);

        long sendMessageStart = clock.getEpochMicros();
        Future<String> msg = app.sayHelloAsync("bob", 0);

        // sending the 1st message takes about 100ms on client side
        startProcessing.await();

        long serverProcessingStart = clock.getEpochMicros();

        try {

            // span is created somewhere between those two timing events, but we can't exactly when
            // and it varies a lot from one execution to another

            long waitBeforeCancel = 100;

            Thread.sleep(waitBeforeCancel);
            logger.info("cancel call after waiting {} ms", waitBeforeCancel);

            // cancel the future --> should create a span
            assertThat(msg.cancel(true)).isTrue();
            long cancelTime = clock.getEpochMicros();

            Span span = reporter.getFirstSpan(100);
            checkSpan(span);

            assertThat(span.getTimestamp())
                .describedAs("span timestamp should be after starting sending message")
                .isGreaterThanOrEqualTo(sendMessageStart - 50_000);

            assertThat(span.getDuration())
                .describedAs("span duration should be larger than waited time before cancel")
                .isGreaterThan(waitBeforeCancel * 1_000L)
                // we expect an error that is related to the time it took to send the message plus a 5ms margin
                // this is quite approximate, but we just want to ensure that agent provides something close to reality
                .isCloseTo(waitBeforeCancel * 1_000L, byLessThan((serverProcessingStart - sendMessageStart) + 5_000L));

        } finally {
            // server is still waiting and did not sent response yet
            // we need to unblock it to prevent side effects on other tests
            endProcessing.await();
        }
    }

    @Test
    void clientStreamingCallShouldBeIgnored() {
        String s = app.sayHelloClientStreaming(Arrays.asList("bob", "alice"), 37);
        assertThat(s)
            .describedAs("we should not break expected app behavior")
            .isEqualTo("hello to [bob,alice] 37 times");

        assertNoSpan();
    }

    @Test
    void serverStreamingCallShouldBeIgnored() {
        String s = app.sayHelloServerStreaming("alice", 5);
        assertThat(s)
            .describedAs("we should not break expected app behavior")
            .isEqualTo("alice alice alice alice alice");

        assertNoSpan();
    }

    @Test
    void bidiStreamingCallShouldBeIgnored() {
        String result = app.sayHelloBidiStreaming(Arrays.asList("bob", "alice", "oscar"), 2);
        assertThat(result)
            .describedAs("we should not break expected app behavior")
            .isEqualTo("hello(bob) hello(bob) hello(alice) hello(alice) hello(oscar) hello(oscar)");

        assertNoSpan();
    }

    private static void assertNoSpan() {
        reporter.assertNoSpan(100);
    }


}
