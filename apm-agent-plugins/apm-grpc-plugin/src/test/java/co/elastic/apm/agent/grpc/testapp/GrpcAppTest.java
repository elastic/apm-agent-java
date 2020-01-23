package co.elastic.apm.agent.grpc.testapp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

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
    void sendSimpleMessage() throws Exception {
        checkMsg("joe", 0, "hello(joe)");
        checkMsg("bob", 1, "nested(1)->hello(bob)");
        checkMsg("rob", 2, "nested(2)->nested(1)->hello(rob)");
    }

    private void checkMsg(String name, int depth, String expectedMsg){
        String msg = app.sendMessage(name, depth);
        assertThat(msg).isEqualTo(expectedMsg);
    }

}
