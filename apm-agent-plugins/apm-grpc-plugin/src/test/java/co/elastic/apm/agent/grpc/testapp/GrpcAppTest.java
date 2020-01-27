package co.elastic.apm.agent.grpc.testapp;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// this class just tests the sample application normal behavior, not the behavior when it's instrumented
class GrpcAppTest {

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
        checkMsg("joe", 1, "hello(joe)");
    }

    @Test
    void simpleErrorCall() {
        checkMsg(null, 0, null);
    }

    @Test
    void nestedChecks() throws Exception {
        checkMsg("joe", 0, "hello(joe)");
        checkMsg("bob", 1, "nested(1)->hello(bob)");
        checkMsg("rob", 2, "nested(2)->nested(1)->hello(rob)");
    }

    @Test
    void recommendedServerErrorHandling() {
        exceptionOrErrorCheck(null);
    }

    @Test
    void uncaughtExceptionServerErrorHandling() {
        // should be strictly indentical to "recommended way to handle errors" from client perspective
        // but might differ server side
        exceptionOrErrorCheck("boom");
    }

    void exceptionOrErrorCheck(String name) {
        checkMsg(name, 0, null);
        checkMsg(name, 1, "nested(1)->error(0)");
        checkMsg(name, 2, "nested(2)->nested(1)->error(0)");
    }

    private void checkMsg(String name, int depth, String expectedMsg) {
        String msg = app.sendMessage(name, depth);
        assertThat(msg).isEqualTo(expectedMsg);
    }

}
