/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.springwebflux.testapp;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.Logger;
import reactor.util.Loggers;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public class GreetingWebClient {

    private static final Logger logger = Loggers.getLogger(GreetingWebClient.class);

    private final WebClient client;
    private final String pathPrefix;
    private final boolean useFunctionalEndpoint;
    private final int port;
    private final MultiValueMap<String, String> headers;
    private final MultiValueMap<String, String> cookies;
    private final Scheduler clientScheduler;
    private final WebSocketClient wsClient;
    private final String wsBaseUri;
    private final boolean logEnabled;


    // this client also applies a few basic checks to ensure that application behaves
    // as expected within unit tests and in packaged application without duplicating
    // all the testing logic.

    public GreetingWebClient(String host, int port, boolean useFunctionalEndpoint, boolean logEnabled) {
        this.pathPrefix = useFunctionalEndpoint ? "/functional" : "/annotated";
        String baseUri = String.format("http://%s:%d%s", host, port, pathPrefix);
        this.wsBaseUri = String.format("ws://%s:%d", host, port);
        this.port = port;
        this.client = WebClient.builder()
            .baseUrl(baseUri)
            .clientConnector(new ReactorClientHttpConnector()) // allows to use either netty/reactor or jetty client
            .build();
        this.useFunctionalEndpoint = useFunctionalEndpoint;
        this.headers = new HttpHeaders();
        this.cookies = new HttpHeaders();
        this.clientScheduler = Schedulers.newElastic("webflux-client");
        this.wsClient = new ReactorNettyWebSocketClient();
        this.logEnabled = logEnabled;
    }

    public Mono<String> getHelloMono() {
        return requestMono("GET", "/hello", 200);
    }

    public Mono<String> getMappingError404() {
        return requestMono("GET", "/error-404", 404);
    }

    public Mono<String> getHandlerError() {
        return requestMono("GET", "/error-handler", 500);
    }

    public Mono<String> getMonoError() {
        return requestMono("GET", "/error-mono", 500);
    }

    public Mono<String> getMonoEmpty() {
        return requestMono("GET", "/empty-mono", 200);
    }

    public Mono<String> methodMapping(String method) {
        return requestMono(method, "/hello-mapping", 200);
    }

    public Mono<String> withPathParameter(String param) {
        return requestMono("GET", "/with-parameters/" + param, 200);
    }

    // nested routes, only relevant for functional routing
    public Mono<String> nested(String method) {
        return requestMono(method, "/nested", 200);
    }

    public Mono<String> duration(long durationMillis) {
        return requestMono("GET", "/duration?duration=" + durationMillis, 200);
    }

    // returned as flux, but will only produce one element
    public Flux<String> childSpans(int count, long durationMillis, long delay) {
        return requestFlux(String.format("/child-flux?duration=%d&count=%d&delay=%d", durationMillis, count, delay));
    }

    public List<String> webSocketPingPong(int count) {

        // taken from https://github.com/spring-projects/spring-framework/blob/master/spring-webflux/src/test/java/org/springframework/web/reactive/socket/WebSocketIntegrationTests.java
        Flux<String> input = Flux.range(1, count).map(i -> "ping-" + i);

        AtomicReference<List<String>> actualRef = new AtomicReference<>();
        this.wsClient.execute(URI.create(wsBaseUri + "/ping"), session ->
            session.send(input.map(session::textMessage))
                .thenMany(session.receive()
                    .take(count)
                    .map(WebSocketMessage::getPayloadAsText))
                .collectList()
                .doOnNext(actualRef::set)
                .then())
            .block(Duration.ofMillis(1000));

        Objects.requireNonNull(actualRef.get());
        return actualRef.get();
    }

    // returned as a stream of elements
    public Flux<ServerSentEvent<String>> childSpansSSE(int count, long durationMillis, long delay) {
        return requestFluxSSE(String.format("/child-flux/sse?duration=%d&count=%d&delay=%d", durationMillis, count, delay));
    }

    // only relevant for annotated controller
    public Mono<String> customTransactionName() {
        return requestMono("GET", "/custom-transaction-name", 200);
    }

    public Mono<String> requestMono(String method, String path, int expectedStatus) {
        Mono<String> request = request(method, path, expectedStatus)
            .bodyToMono(String.class)
            .publishOn(clientScheduler);
        return logEnabled ? request.log(logger) : request;
    }

    public void sampleRequests() {

        Duration timeout = Duration.ofMillis(1000);

        getHelloMono().block(timeout);
        getMappingError404().onErrorResume(e -> Mono.empty()).block(timeout);
        getHandlerError().onErrorResume(e -> Mono.empty()).block(timeout);
        getMonoError().onErrorResume(e -> Mono.empty()).block(timeout);

        Stream.of("GET", "POST", "PUT", "DELETE").forEach(method -> methodMapping(method).block(timeout));

        withPathParameter("12345").block(timeout);

        childSpans(5, 3, 1)
            .blockLast(timeout);

        childSpansSSE(5, 3, 1)
            .blockLast(timeout);

        webSocketPingPong(5);
    }

    private Flux<String> requestFlux(String path) {
        Flux<String> request = request("GET", path, 200)
            .bodyToFlux(String.class)
            .publishOn(clientScheduler);
        return logEnabled ? request.log(logger) : request;
    }

    private Flux<ServerSentEvent<String>> requestFluxSSE(String path) {

        // required to get proper return generic type
        ParameterizedTypeReference<ServerSentEvent<String>> type = new ParameterizedTypeReference<>() {
        };

        Flux<ServerSentEvent<String>> request = request("GET", path, 200)
            .bodyToFlux(type)
            .publishOn(clientScheduler);

        return logEnabled ? request.log(logger) : request;
    }


    private WebClient.ResponseSpec request(String method, String path, int expectedStatus) {
        return client.method(HttpMethod.valueOf(method))
            .uri(path)
            .accept(MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON)
            .headers(httpHeaders -> httpHeaders.addAll(headers))
            .cookies(httpCookies -> httpCookies.addAll(cookies))
            .retrieve()
            .onRawStatus(status -> status != expectedStatus, r -> Mono.error(new IllegalStateException(String.format("unexpected response status %d", r.rawStatusCode()))));
    }

    @Override
    public String toString() {
        return String.format("GreetingWebClient [%s]", pathPrefix);
    }

    public String getPathPrefix() {
        return pathPrefix;
    }

    public int getPort() {
        return port;
    }

    public boolean useFunctionalEndpoint() {
        return useFunctionalEndpoint;
    }

    public void setHeader(String name, String value) {
        headers.add(name, value);
    }

    public void setCookie(String name, String value){
        cookies.add(name, value);
    }
}
