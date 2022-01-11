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
package co.elastic.apm.agent.grpc;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.grpc.testapp.GrpcApp;
import co.elastic.apm.agent.grpc.testapp.GrpcAppProvider;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import java.util.Arrays;
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

        startTestRootTransaction("gRPC client transaction");
    }

    @AfterEach
    void afterEach() throws Exception {
        Transaction transaction = tracer.currentTransaction();

        if (transaction != null) {
            transaction.deactivate()
                .end();
        }

        app.stop();
    }

    @Test
    public void simpleCall() {
        doSimpleCall("bob");

        endRootTransaction();

        Transaction transaction = reporter.getFirstTransaction();
        assertThat(transaction).isNotNull();

        Span span = reporter.getFirstSpan();
        checkSpan(span);
        assertThat(span.getOutcome()).isEqualTo(Outcome.SUCCESS);

    }

    private void checkSpan(Span span) {
        assertThat(span.getType()).isEqualTo("external");
        assertThat(span.getSubtype()).isEqualTo("grpc");
        assertThat(span.getNameAsString()).isEqualTo("helloworld.Hello/SayHello");

    }

    @Test
    public void simpleCallOutsideTransactionShouldBeIgnored() {

        // terminate transaction early
        endRootTransaction();

        // no span should be captured as no transaction is active
        doSimpleCall("joe");

        assertNoSpan();

    }

    private void doSimpleCall(String name) {
        assertThat(app.sayHello(name, 0))
            .isEqualTo(String.format("hello(%s)", name));
    }

    @Test
    public void cancelClientCall() throws Exception {

        CyclicBarrier startProcessing = new CyclicBarrier(2);
        CyclicBarrier endProcessing = new CyclicBarrier(2);
        app.getServer().useBarriersForProcessing(startProcessing, endProcessing);

        Future<String> msg = app.sayHelloAsync("bob", 0);

        startProcessing.await();

        try {

            long waitBeforeCancel = 20;

            Thread.sleep(waitBeforeCancel);
            logger.info("cancel call after waiting {} ms", waitBeforeCancel);

            // cancel the future --> should create a span
            assertThat(msg.cancel(true)).isTrue();


            Span span = reporter.getFirstSpan(200);

            // we should have a span created and properly terminated, even if the server
            // thread is still waiting for proper termination.
            //
            // we can't reliably do assertions on the actual duration without introducing flaky tests
            checkSpan(span);
            assertThat(span.getOutcome()).isEqualTo(Outcome.FAILURE);

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
    @Disabled // disabled for now as it's flaky on CI
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

    @ParameterizedTest
    @ValueSource(strings = {"start", "onHeaders", "onMessage", "onReady"})
        // note: we don't test onClose because throwing an exception makes call to block forever
    void clientCallAndListenerExceptionCheck(String method) {
        // any exception thrown within instrumented client methods should not leak any span (active or not)
        // thus we configure the client to trigger arbitrary exceptions to trigger them

        app.getClient().setExceptionMethod(method);

        Throwable thrown = null;
        try {
            app.sayHello("any", 0);
        } catch (Exception e) {
            // silently ignored
            thrown = e;
        }

        int expectedErrors;

        boolean is161 = isVersion161();
        if ("start".equals(method)) {
            // exception is thrown in main thread
            assertThat(thrown).isNotNull();
            expectedErrors = 1;
        } else {
            assertThat(thrown).isNull();
            if (method.equals("onReady")) {
                expectedErrors = is161 ? 1 : 0;
            } else {
                // exceptions are thrown in a separate thread
                // onReady is not called in this workflow, thus we just ignore it
                expectedErrors = 1;
            }

            // in this case error should be visible from client side
            assertThat(app.getClient().getErrorCount()).isEqualTo(expectedErrors);

        }

        // even if there is an exception thrown, we should still have a span created.

        Span span = getFirstSpan();
        checkSpan(span);

        Outcome outcome = span.getOutcome();
        if (expectedErrors == 0) {
            // when no visible error on client, the span should have success outcome
            assertThat(outcome).isEqualTo(Outcome.SUCCESS);
        } else {
            // when error visible on client, span should have failure outcome
            assertThat(outcome).isEqualTo(Outcome.FAILURE);
        }

    }

    protected void endRootTransaction() {
        Transaction transaction = tracer.currentTransaction();
        assertThat(transaction).isNotNull();

        transaction
            .withOutcome(Outcome.SUCCESS)
            .deactivate()
            .end();
    }

    private boolean isVersion161() {
        return getClass().getPackageName().contains(".v1_6_1");
    }

    private static Span getFirstSpan() {
        return reporter.getFirstSpan(1000);
    }

    private static void assertNoSpan() {
        reporter.assertNoSpan(500);
    }


}
