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
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractGrpcServerInstrumentationTest extends AbstractInstrumentationTest {

    private GrpcApp app;

    public abstract GrpcAppProvider getAppProvider();

    @BeforeEach
    void beforeEach() throws Exception {
        app = GrpcTest.getApp(getAppProvider());
        app.start();
    }

    @AfterEach
    void afterEach() throws Exception {
        app.stop();
    }

    @Test
    void simpleCall() {
        assertThat(app.sayHello("bob", 0))
            .isEqualTo("hello(bob)");

        checkUnaryTransaction(getFirstTransaction(), "OK");
    }

    @Test
    void nestedCallShouldProduceTwoTransactions() {
        assertThat(app.sayHello("bob", 1))
            .isEqualTo("nested(1)->hello(bob)");

        reporter.awaitTransactionCount(2);

        for (Transaction transaction : reporter.getTransactions()) {
            checkUnaryTransaction(transaction, "OK");
        }
    }

    @Test
    void simpleCallWithInvalidArgumentError() {
        simpleCallWithError(null, "INVALID_ARGUMENT");
    }

    @Test
    void simpleCallWithRuntimeError() {
        simpleCallWithError("boom", "UNKNOWN");
    }

    @Test
    void asyncClientCallShouldWorkLikeRegularCall() throws Exception {

        String msg = app.sayHelloAsync("bob", 0).get();
        assertThat(msg).isEqualTo("hello(bob)");

        Transaction transaction = getFirstTransaction();
        checkUnaryTransaction(transaction, "OK");
    }

    private void simpleCallWithError(String name, String expectedResult) {
        assertThat(app.sayHello(name, 0))
            .isNull();

        Transaction transaction = getFirstTransaction();
        checkUnaryTransaction(transaction, expectedResult);
    }

    @Test
    void clientStreamingCallShouldBeIgnored() {
        String s = app.sayHelloClientStreaming(Arrays.asList("bob", "alice"), 37);
        assertThat(s)
            .describedAs("we should not break expected app behavior")
            .isEqualTo("hello to [bob,alice] 37 times");

        checkNoTransaction();
    }

    @Test
    void serverStreamingShouldBeIgnored() {
        String s = app.sayHelloServerStreaming("joe", 4);
        assertThat(s)
            .describedAs("we should not break expected app behavior")
            .isEqualTo("joe joe joe joe");

        checkNoTransaction();
    }

    @Test
    void bidiStreamingCallShouldBeIgnored() {
        String s = app.sayHelloBidiStreaming(Arrays.asList("bob", "alice"), 2);
        assertThat(s)
            .describedAs("we should not break expected app behavior")
            .isEqualTo("hello(bob) hello(bob) hello(alice) hello(alice)");

        checkNoTransaction();
    }

    @ParameterizedTest
    @ValueSource(strings = {"onMessage", "onHalfClose", "onCancel", "onComplete", "onReady"})
    void serverListenerException(String method) {
        // throwing an exception in any of the server listener methods should mark the transaction in error
        app.getServer().setListenerExceptionMethod(method);

        String s = app.sayHello("any", 0);

        String expectedTransactionStatus;
        int expectedErrorCount = 0;
        if (method.equals("onCancel") || method.equals("onComplete")) {
            // onCancel is not called, thus exception is not thrown
            // onComplete exception is thrown, but result is already sent, thus we still get it client-side
            assertThat(s).isEqualTo("hello(any)");
            expectedTransactionStatus = "OK";
        } else {
            // with all other listener methods, expected result is not available
            assertThat(s).isNull();
            expectedErrorCount = 1;
            expectedTransactionStatus = "UNKNOWN";
        }

        assertThat(app.getClient().getErrorCount())
            .describedAs("server listener exception should be visible on client")
            .isEqualTo(expectedErrorCount);

        checkUnaryTransaction(getFirstTransaction(), expectedTransactionStatus);
    }

    @Test
    void noNestedTransactionsForSingleCall() {
        String s = app.sayHello("bob", 0);
        assertThat(s).isEqualTo("hello(bob)");

        reporter.awaitTransactionCount(1);
    }

    private static void checkUnaryTransaction(Transaction transaction, String expectedResult) {
        assertThat(transaction).isNotNull();
        assertThat(transaction.getNameAsString()).isEqualTo("helloworld.Hello/SayHello");
        assertThat(transaction.getType()).isEqualTo("request");
        assertThat(transaction.getResult()).isEqualTo(expectedResult);
        assertThat(transaction.getFrameworkName()).isEqualTo("gRPC");
    }

    private static void checkNoTransaction() {
        getReporter().assertNoTransaction(100);
    }

    private static Transaction getFirstTransaction() {
        return getReporter().getFirstTransaction(1000);
    }

}
