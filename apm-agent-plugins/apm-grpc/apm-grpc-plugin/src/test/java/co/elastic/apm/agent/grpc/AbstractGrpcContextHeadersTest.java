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
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


public abstract class AbstractGrpcContextHeadersTest extends AbstractInstrumentationTest {
    // we use a dedicated test class because those features involves both server and client parts
    // and we can't test them in isolation but as a whole

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

        // as weak maps are used, proper object recycling required GC
        reporter.enableGcWhenAssertingObjectRecycling();
    }

    @Test
    void simpleClientContextPropagation() {
        // we have 2 transactions and 1 span
        // transaction 1 (root transaction) will do the gRPC call and create a span
        // transaction 2 will handle the gRPC call and create a transaction

        Transaction transaction1 = createRootTransaction();
        try {

            assertThat(app.sayHello("oscar", 0)).isEqualTo("hello(oscar)");

        } finally {
            endRootTransaction(transaction1);
        }

        reporter.awaitUntilAsserted(100, () -> {
            assertThat(reporter.getTransactions())
                .describedAs("should have 2 transactions: client (root) transaction, and gRPC server transaction")
                .hasSize(2);
        });

        List<Transaction> transactions = reporter.getTransactions();
        assertThat(transactions).hasSize(2);

        assertThat(transactions).contains(transaction1);

        Transaction transaction2 = transactions.stream()
            .filter((t) -> !t.equals(transaction1))
            .findFirst()
            .orElseThrow(() -> null);

        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(1);

        Span span = spans.get(0);

        assertThat(transaction2.isChildOf(span))
            .describedAs("server transaction parent %s should be client span %s",
                transaction2.getTraceContext().getParentId(),
                span.getTraceContext().getId())
            .isTrue();

    }

    private static Transaction createRootTransaction() {
        return tracer.startRootTransaction(AbstractGrpcClientInstrumentationTest.class.getClassLoader())
            .withName("root")
            .withType("test")
            .activate();
    }

    private static void endRootTransaction(Transaction transaction) {
        transaction
            .withOutcome(Outcome.SUCCESS)
            .deactivate()
            .end();
    }


}
