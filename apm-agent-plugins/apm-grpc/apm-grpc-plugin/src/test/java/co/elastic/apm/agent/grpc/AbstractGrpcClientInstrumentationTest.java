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

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

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

        // make sure we do not leave anything behind
        reporter.assertRecycledAfterDecrementingReferences();

        reporter.reset();

        app.stop();
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
        assertThat(app.sendMessage(name, 0))
            .isEqualTo(String.format("hello(%s)", name));
    }

    @Test
    public void cancelClientCall() throws Exception {

        EpochTickClock clock = new EpochTickClock();
        clock.init();

        CyclicBarrier start = new CyclicBarrier(2);
        CyclicBarrier end = new CyclicBarrier(2);
        app.getServer().useBarriersForProcessing(start, end);

        long sendMessageStart = clock.getEpochMicros();
        Future<String> msg = app.sendMessageAsync("bob", 0);

        // sending the 1st message takes about 100ms on client side
        start.await();

        try {
            long serverProcessingStart = clock.getEpochMicros();

            // span is created somewhere between those two timing events, but we can't exactly when
            // and it varies a lot from one execution to another

            long waitBeforeCancel = 50;

            Thread.sleep(waitBeforeCancel);
            logger.info("cancel call after waiting {} ms", waitBeforeCancel);

            // cancel the future --> should create a span
            assertThat(msg.cancel(true)).isTrue();

            Span span = reporter.getFirstSpan(1000);
            checkSpan(span);

            assertThat(span.getTimestamp())
                .describedAs("span timestamp should be between start sending message and start processing on server")
                .isBetween(sendMessageStart, serverProcessingStart);

            // we don't know exactly when span starts, but we can at least make sure it's consistent with what we expect
            // extra 5ms allowed to make test more reliable as cancellation is not blocking
            long durationError = serverProcessingStart - sendMessageStart + 5000;

            long waitMicro = waitBeforeCancel * 1000;

            assertThat(span.getDuration())
                .describedAs("span duration should be larger than waited time before cancel")
                .isBetween(waitMicro, waitMicro + durationError);

        } finally {
            // server is still waiting and did not sent response yet
            // we need to unblock it to prevent side effects on other tests
            end.await();
        }
    }

}
