package co.elastic.apm.agent.springwebflux;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.springwebflux.testapp.GreetingWebClient;
import co.elastic.apm.agent.springwebflux.testapp.WebFluxApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class WebSocketServerInstrumentationTest extends AbstractInstrumentationTest {

    protected static WebFluxApplication.App app;
    protected GreetingWebClient client;

    @BeforeAll
    static void startApp() {
        app = WebFluxApplication.run(-1, "netty");
    }

    @AfterAll
    static void stopApp() {
        app.close();
    }

    @BeforeEach
    void beforeEach() {
        assertThat(reporter.getTransactions()).isEmpty();
        client = app.getClient(false); // functional/annotated does not matter for websockets
    }

    @Test
    void shouldIgnoreWebsockets() {
        int count = 5;

        List<String> result = client.webSocketPingPong(count);

        List<String> expected = IntStream.range(1, count + 1)
            .mapToObj(i -> String.format("pong-%d", i))
            .collect(Collectors.toList());
        assertThat(result).containsExactlyElementsOf(expected);

        reporter.assertNoTransaction(200);
        reporter.assertNoSpan(200);
    }
}
