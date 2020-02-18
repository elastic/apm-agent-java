package co.elastic.apm.agent.grpc;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.grpc.testapp.GrpcApp;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class GrpcContextHeadersTest extends AbstractInstrumentationTest  {
    // we use a dedicated test class because those features involves both server and client parts
    // and we can't test them in isolation but as a whole

    private GrpcApp app;

    @BeforeEach
    void beforeEach() throws Exception {
        app = new GrpcApp();
        app.start();
    }

    @AfterEach
    void afterEach() throws Exception {
        // make sure we do not leave anything behind
        try {
            reporter.assertRecycledAfterDecrementingReferences();
            reporter.reset();
        } finally {
            app.stop();
        }
    }

    @Test
    void simpleClientContextPropagation() {
        // we have 2 transactions and 1 span
        // transaction 1 (root transaction) will do the gRPC call and create a span
        // transaction 2 will handle the gRPC call and create a transaction

        Transaction transaction1 = createRootTransaction();
        try {

            assertThat(app.sendMessage("oscar", 0)).isEqualTo("hello(oscar)");

        } finally {
            endRootTransaction(transaction1);
        }

        List<Transaction> transactions = reporter.getTransactions();
        assertThat(transactions).hasSize(2);

        assertThat(transactions.get(1)).isSameAs(transaction1);

        Transaction transaction2 = transactions.get(0);

        Span span = reporter.getFirstSpan();

        assertThat(transaction2.isChildOf(span))
            .describedAs("server transaction parent %s should be client span %s",
                transaction2.getTraceContext().getParentId(),
                span.getTraceContext().getId())
            .isTrue();

    }

    private static Transaction createRootTransaction() {
        return tracer.startRootTransaction(GrpcClientInstrumentationTest.class.getClassLoader())
            .withName("root")
            .withType("test")
            .activate();
    }

    private static void endRootTransaction(Transaction transaction) {
        transaction.deactivate().end();
    }


}
