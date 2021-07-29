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
package co.elastic.apm.agent.webflux.client;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.context.SpanContext;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.springwebflux.testapp.WebFluxApplication;
import org.junit.Ignore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.JettyWebSocketClient;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.StandardWebSocketClient;
import org.springframework.web.reactive.socket.client.UndertowWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import org.xnio.OptionMap;
import org.xnio.Xnio;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class WebSocketClientInstrumentationTest extends AbstractInstrumentationTest {

    protected static WebFluxApplication.App app;

    @BeforeAll
    static void startApp() {
        app = WebFluxApplication.run(-1, "netty", true);
    }

    @AfterAll
    static void stopApp() {
        app.close();
    }

    @BeforeEach
    public void setupTests() {
        startTestRootTransaction("parent of http span");
    }

    @AfterEach
    public final void after() {
        Transaction transaction = tracer.currentTransaction();
        assertThat(transaction).isNotNull();
        transaction.deactivate().end();
        assertThat(reporter.getTransactions()).hasSize(1);

//        flushGcExpiry(WebfluxClientSubscriber.getContextMap(), 13);
//        flushGcExpiry(WebfluxClientSubscriber.getLogPrefixMap(), 13);
//        flushGcExpiry(WebfluxClientSubscriber.getWebClientMap(), 13);
    }

    private static final Function<Mono, Object> monoFunction1 = mono -> mono.subscribe();
    private static final Function<Mono, Object> monoFunction2 = mono -> mono.block();
    private static final Supplier<WebSocketClient> webSocketClientSupplier1 = () -> new ReactorNettyWebSocketClient();
    private static final Supplier<WebSocketClient> webSocketClientSupplier2 = () -> {
        JettyWebSocketClient c = new JettyWebSocketClient();
        c.start();
        return c;
    };
    private static final Supplier<WebSocketClient> webSocketClientSupplier3 = () -> new StandardWebSocketClient();
    private static final Supplier<WebSocketClient> webSocketClientSupplier4 = () -> {
        UndertowWebSocketClient c = null;
        try {
            c = new UndertowWebSocketClient(Xnio.getInstance().createWorker(OptionMap.EMPTY));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return c;
    };

    public static Stream<Arguments> testSendReceiveSource() {
        return Stream.of(
            Arguments.of(webSocketClientSupplier1, monoFunction1),
            Arguments.of(webSocketClientSupplier1, monoFunction2),
            Arguments.of(webSocketClientSupplier2, monoFunction1),
            Arguments.of(webSocketClientSupplier2, monoFunction2),
            Arguments.of(webSocketClientSupplier3, monoFunction1),
            Arguments.of(webSocketClientSupplier3, monoFunction2),
            Arguments.of(webSocketClientSupplier4, monoFunction1),
            Arguments.of(webSocketClientSupplier4, monoFunction2)
        );
    }

    public static Stream<Arguments> testErrorUriSource() {
        return Stream.of(
            //host
            Arguments.of("ws://localhos:" + app.getPort() + "/ping", webSocketClientSupplier1, monoFunction1),
            Arguments.of("ws://localhos:" + app.getPort() + "/ping", webSocketClientSupplier1, monoFunction2),
            Arguments.of("ws://localhos:" + app.getPort() + "/ping", webSocketClientSupplier2, monoFunction1),
            Arguments.of("ws://localhos:" + app.getPort() + "/ping", webSocketClientSupplier2, monoFunction2),
            Arguments.of("ws://localhos:" + app.getPort() + "/ping", webSocketClientSupplier3, monoFunction1),
            Arguments.of("ws://localhos:" + app.getPort() + "/ping", webSocketClientSupplier3, monoFunction2),
            Arguments.of("ws://localhos:" + app.getPort() + "/ping", webSocketClientSupplier4, monoFunction1),
            Arguments.of("ws://localhos:" + app.getPort() + "/ping", webSocketClientSupplier4, monoFunction2),

            //port
            Arguments.of("ws://localhost:8083/ping", webSocketClientSupplier1, monoFunction1),
            Arguments.of("ws://localhost:8083/ping", webSocketClientSupplier1, monoFunction2),
            Arguments.of("ws://localhost:8083/ping", webSocketClientSupplier2, monoFunction1),
            Arguments.of("ws://localhost:8083/ping", webSocketClientSupplier2, monoFunction2),
            Arguments.of("ws://localhost:8083/ping", webSocketClientSupplier3, monoFunction1),
            Arguments.of("ws://localhost:8083/ping", webSocketClientSupplier3, monoFunction2),
            Arguments.of("ws://localhost:8083/ping", webSocketClientSupplier4, monoFunction1),
            Arguments.of("ws://localhost:8083/ping", webSocketClientSupplier4, monoFunction2),

            //scheme
            Arguments.of("wss://localhost:" + app.getPort() + "/ping", webSocketClientSupplier1, monoFunction1),
            Arguments.of("wss://localhost:" + app.getPort() + "/ping", webSocketClientSupplier1, monoFunction2),
            Arguments.of("wss://localhost:" + app.getPort() + "/ping", webSocketClientSupplier2, monoFunction1),
            Arguments.of("wss://localhost:" + app.getPort() + "/ping", webSocketClientSupplier2, monoFunction2),
            Arguments.of("wss://localhost:" + app.getPort() + "/ping", webSocketClientSupplier3, monoFunction1),
            Arguments.of("wss://localhost:" + app.getPort() + "/ping", webSocketClientSupplier3, monoFunction2),
            Arguments.of("wss://localhost:" + app.getPort() + "/ping", webSocketClientSupplier4, monoFunction1),
            Arguments.of("wss://localhost:" + app.getPort() + "/ping", webSocketClientSupplier4, monoFunction2),

            //uri
            Arguments.of("wss://localhost:" + app.getPort() + "/pingx", webSocketClientSupplier1, monoFunction1),
            Arguments.of("wss://localhost:" + app.getPort() + "/pingx", webSocketClientSupplier1, monoFunction2),
            Arguments.of("wss://localhost:" + app.getPort() + "/pingx", webSocketClientSupplier2, monoFunction1),
            Arguments.of("wss://localhost:" + app.getPort() + "/pingx", webSocketClientSupplier2, monoFunction2),
            Arguments.of("wss://localhost:" + app.getPort() + "/pingx", webSocketClientSupplier3, monoFunction1),
            Arguments.of("wss://localhost:" + app.getPort() + "/pingx", webSocketClientSupplier3, monoFunction2),
            Arguments.of("wss://localhost:" + app.getPort() + "/pingx", webSocketClientSupplier4, monoFunction1),
            Arguments.of("wss://localhost:" + app.getPort() + "/pingx", webSocketClientSupplier4, monoFunction2)
        );
    }

    @ParameterizedTest
    @MethodSource("testSendReceiveSource")
    public void testSendReceive(Supplier<WebSocketClient> client, Function<Mono, Object> monoFunction) {
        //TODO: pass a parametrized function to close Reactor client
        testTemplate(client, monoFunction, "ws://localhost:" + app.getPort() + "/ping", 5);
        verifySpans(1000L, 11);
    }

    @ParameterizedTest
    @MethodSource("testSendReceiveSource")
    public void testSendReceiveMulti(Supplier<WebSocketClient> client, Function<Mono, Object> monoFunction) {
        //FIXME: likely a problem with how the spans are structured
        testTemplate(client, monoFunction, "ws://localhost:" + app.getPort() + "/ping", 5, 3);
        verifySpans(30000L, 31);
    }

    //TODO: check thrown exceptions
    @ParameterizedTest
    @MethodSource("testErrorUriSource")
    public void testErrorUri(String uri, Supplier<WebSocketClient> client, Function<Mono, Object> monoFunction) {
        try {
            testTemplate(client, monoFunction, uri, 5);
        } catch (Exception e) {
        }

        verifySpans(1000L, 1);
    }

    private void testTemplate(Supplier<WebSocketClient> client, Function<Mono, Object> monoFunction,
                              String uri, int count) {
        testTemplate(client, monoFunction, uri, count, 1);
    }

    private void testTemplate(Supplier<WebSocketClient> client, Function<Mono, Object> monoFunction,
                              String uri, int count, int multiSubscribe) {
        //FIXME: parametrize
        Flux<String> input = Flux.range(1, count).map(i -> "ping-" + i);

        WebSocketHandler webSocketHandler = session ->
            session.send(input.map(session::textMessage))
                .thenMany(
                    session.receive()
                        .take(count)
                        .doOnCancel(() -> {
                        })//onCancel function
                        .doOnComplete(() -> {
                        })//onComplete function
                        .doOnError(throwable -> {
                        })//onError function
                        .map(WebSocketMessage::getPayloadAsText))

                .collectList()
                .doOnTerminate(() -> {
                })//doOnterminate function
                .doOnError(throwable -> {
                })//doOnError function
                .then();

        Mono<Void> mono1 = client.get().execute(URI.create(uri), webSocketHandler);
        for (int i = 0; i < multiSubscribe; ++i) {
            monoFunction.apply(mono1);
        }
    }

    //TODO
    @Ignore
    @Test
    public void testHandshakeError() {

    }

    //TODO
    @Ignore
    @Test
    public void testUpgradeError() {

    }

    //TODO: verify span context
    public void verifySpans(long assertTimeout, int expected) {
        reporter.awaitUntilAsserted(assertTimeout, () -> assertThat(
            reporter.getNumReportedSpans())
            .isGreaterThanOrEqualTo(expected));
        List<Span> spanList = reporter.getSpans();
        System.out.println("spanList=" + spanList.size() + " reporter.getTransactions()=" + reporter.getTransactions().size());
        for (Span s : spanList) {
            SpanContext spanContext = s.getContext();
            System.out.println("--" + s + " " + s.getOutcome());
            if (spanContext != null && spanContext.getHttp().getUrl() != null) {
                System.out.println("------" + spanContext.getHttp().getMethod() + " " + spanContext.getHttp().getUrl());
            }
        }
    }
}

