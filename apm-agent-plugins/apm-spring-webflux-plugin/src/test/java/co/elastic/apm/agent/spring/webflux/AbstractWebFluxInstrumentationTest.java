package co.elastic.apm.agent.spring.webflux;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import io.undertow.Undertow;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.UndertowHttpHandlerAdapter;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

public class AbstractWebFluxInstrumentationTest extends AbstractInstrumentationTest {

    protected Undertow startServer(RouterFunction<ServerResponse> route) {
        final HttpHandler httpHandler = RouterFunctions.toHttpHandler(route);

        final UndertowHttpHandlerAdapter undertowHttpHandlerAdapter = new UndertowHttpHandlerAdapter(httpHandler);

        //we pass zero so Undertow will find free port
        final Undertow server = Undertow.builder()
            .addHttpListener(0, "127.0.0.1")
            .setHandler(undertowHttpHandlerAdapter)
            .build();

        server.start();

        return server;
    }

    public static int getUsedPort(Undertow undertow) {
        List<Undertow.ListenerInfo> listenerInfo = undertow.getListenerInfo();
        if (listenerInfo.isEmpty()) {
            throw new IllegalArgumentException("No listener info.");
        } else {
            if (listenerInfo.size() > 1) {
                throw new IllegalArgumentException("Should be only 1 listener for tests.");
            } else {
                SocketAddress address = listenerInfo.get(0).getAddress();
                if (address instanceof InetSocketAddress) {
                    return ((InetSocketAddress) listenerInfo.get(0).getAddress()).getPort();
                } else {
                    throw new IllegalArgumentException("Not an instance of InetSocketAddress, can't get the port.");
                }
            }
        }
    }
}
