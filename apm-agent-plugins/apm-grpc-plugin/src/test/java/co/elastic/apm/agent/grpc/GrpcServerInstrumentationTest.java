package co.elastic.apm.agent.grpc;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.grpc.testapp.GrpcApp;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcServerInstrumentationTest extends AbstractInstrumentationTest {

    private GrpcApp app;

    @BeforeEach
    void beforeEach() throws Exception {
        app = new GrpcApp();
        app.start();
    }

    @AfterEach
    void afterEach() throws Exception {
        app.stop();
    }

    @Test
    void simpleCall() {
        assertThat(app.sendMessage("bob", 0))
            .isEqualTo("hello(bob)");

        Transaction transaction = getReporter().getFirstTransaction();
        checkTransaction(transaction, "OK");
    }

    @Test
    void nestedCallShouldProduceTwoTransactions() {
        assertThat(app.sendMessage("bob", 1))
            .isEqualTo("nested(1)->hello(bob)");

        List<Transaction> transactions = getReporter().getTransactions();
        assertThat(transactions).hasSize(2);

        for (Transaction transaction : transactions) {
            checkTransaction(transaction, "OK");
        }
    }

    @Test
    void simplelCallWithInvalidArgumentError() {
        simpleCallWithError(null, "INVALID_ARGUMENT");
    }

    @Test
    void simplelCallWithRuntimeError() {
        simpleCallWithError("boom", "UNKNOWN");
    }

    private void simpleCallWithError(String name, String expectedResult){
        assertThat(app.sendMessage(name, 0))
            .isNull();

        Transaction transaction = getReporter().getFirstTransaction();
        checkTransaction(transaction, expectedResult);
    }

    private static void checkTransaction(Transaction transaction, String expectedResult) {
        assertThat(transaction.getNameAsString()).isEqualTo("helloworld.Hello/SayHello");
        assertThat(transaction.getType()).isEqualTo("request");
        assertThat(transaction.getResult()).isEqualTo(expectedResult);
    }



}
