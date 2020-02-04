package co.elastic.apm.agent.grpc;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.grpc.testapp.GrpcApp;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.Ignore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcClientInstrumentationTest extends AbstractInstrumentationTest {

    private GrpcApp app;

    @BeforeEach
    void beforeEach() throws Exception {
        app = new GrpcApp();
        app.start();

        tracer.startTransaction(TraceContext.asRoot(), null, null)
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

        reporter.reset();

        app.stop();
    }


    @Test
    void simpleCall() {
        doSimpleCall("bob");

        Transaction transaction = reporter.getFirstTransaction();
        assertThat(transaction).isNotNull();

        Span span = reporter.getFirstSpan();
        assertThat(span.getType()).isEqualTo("external");
        assertThat(span.getSubtype()).isEqualTo("grpc");

    }

    @Test
    void simpleCallOutsideTransactionShouldBeIgnored() {

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

    @Ignore // not implemented yet
    @Test
    void simpleCallWithLinkedTransaction() {
        // this test actually tests for client and server instrumentation
        // when this works, it means that tracing header is
        // 1) properly set on gRPC client call, and thus sent to server
        // 2) properly captured by gRPC server instrumentation and propagated to the server-side transaction

    }

}
