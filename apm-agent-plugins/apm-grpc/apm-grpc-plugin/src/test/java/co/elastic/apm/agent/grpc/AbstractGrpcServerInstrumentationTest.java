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

import java.util.Arrays;
import java.util.List;

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
        try {
            // make sure we do not leave anything behind
            reporter.assertRecycledAfterDecrementingReferences();

            // use a try/finally block here to make sure that if the assertion above fails
            // we do not have a side effect on other tests execution by leaving app running.
        } finally {
            app.stop();
            reporter.reset();
        }
    }

    @Test
    void simpleCall() {
        assertThat(app.sayHello("bob", 0))
            .isEqualTo("hello(bob)");

        Transaction transaction = getReporter().getFirstTransaction(100);
        checkUnaryTransaction(transaction, "OK");
    }

    @Test
    void nestedCallShouldProduceTwoTransactions() {
        assertThat(app.sayHello("bob", 1))
            .isEqualTo("nested(1)->hello(bob)");

        List<Transaction> transactions = getReporter().getTransactions();
        assertThat(transactions).hasSize(2);

        for (Transaction transaction : transactions) {
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

        Transaction transaction = getReporter().getFirstTransaction();
        checkUnaryTransaction(transaction, "OK");
    }

    private void simpleCallWithError(String name, String expectedResult) {
        assertThat(app.sayHello(name, 0))
            .isNull();

        Transaction transaction = getReporter().getFirstTransaction();
        checkUnaryTransaction(transaction, expectedResult);
    }

    @Test
    void clientStreamingCallShouldBeIgnored() {
        String s = app.sayHelloClientStreaming(Arrays.asList("bob", "alice"), 37);
        assertThat(s)
            .describedAs("we should not break expected app behavior")
            .isEqualTo("hello to [bob,alice] 37 times");

        checkNoTransaction("client streaming");
    }

    @Test
    void serverStreamingBasicSupport() {
        String s = app.sayHelloServerStreaming("joe", 4);
        assertThat(s)
            .describedAs("we should not break expected app behavior")
            .isEqualTo("joe joe joe joe");

        Transaction transaction = getReporter().getFirstTransaction();
        checkTransaction(transaction, "OK", "SayHelloMany");
    }

    @Test
    void bidiStreamingCallShouldBeIgnored() {
        String s = app.sayHelloBidiStreaming(Arrays.asList("bob", "alice"), 2);
        assertThat(s)
            .describedAs("we should not break expected app behavior")
            .isEqualTo("hello(bob) hello(bob) hello(alice) hello(alice)");

        checkNoTransaction("bidi streaming");
    }

    private static void checkUnaryTransaction(Transaction transaction, String expectedResult){
        checkTransaction(transaction, expectedResult, "SayHello");
    }

    private static void checkTransaction(Transaction transaction, String expectedResult, String rpcName) {
        assertThat(transaction.getNameAsString()).isEqualTo(String.format("helloworld.Hello/%s", rpcName));
        assertThat(transaction.getType()).isEqualTo("request");
        assertThat(transaction.getResult()).isEqualTo(expectedResult);
    }

    private void checkNoTransaction(String usage) {
        List<Transaction> transactions = getReporter().getTransactions();
        assertThat(transactions)
            .describedAs("no transaction is expected for %s calls", usage)
            .isEmpty();
    }


}
