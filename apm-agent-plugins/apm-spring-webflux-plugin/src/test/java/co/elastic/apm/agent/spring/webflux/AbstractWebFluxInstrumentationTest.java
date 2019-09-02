package co.elastic.apm.agent.spring.webflux;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import io.undertow.Undertow;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.UndertowHttpHandlerAdapter;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

public class AbstractWebFluxInstrumentationTest extends AbstractInstrumentationTest {

    protected static Undertow startServer(RouterFunction<ServerResponse> route) {
        final HttpHandler httpHandler = RouterFunctions.toHttpHandler(route);

        final UndertowHttpHandlerAdapter undertowHttpHandlerAdapter = new UndertowHttpHandlerAdapter(httpHandler);

        final Undertow server = Undertow.builder()
            .addHttpListener(8200, "127.0.0.1")
            .setHandler(undertowHttpHandlerAdapter)
            .build();

        server.start();

        return server;
    }
}
