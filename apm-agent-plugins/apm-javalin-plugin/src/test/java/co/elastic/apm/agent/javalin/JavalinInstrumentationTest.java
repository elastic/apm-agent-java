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
package co.elastic.apm.agent.javalin;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Span;
import io.javalin.Javalin;
import io.javalin.http.HandlerType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class JavalinInstrumentationTest extends AbstractInstrumentationTest {

    private static final Javalin app = Javalin.create();
    private static String baseUrl;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeAll
    public static void startJavalin() {
        app.start(0);
        baseUrl = "http://localhost:" + app.port();
    }

    @AfterAll
    public static void stopJavalin() {
        app.stop();
    }

    @Test
    public void testJavalinSimpleGet() throws Exception {
        app.get("/", ctx -> ctx.status(200));

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/")).build();
        final HttpResponse<String> mainUrlResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(mainUrlResponse.statusCode()).isEqualTo(200);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("GET /");
    }

    @Test
    public void testJavalinParametrizedInput() throws Exception {
        app.post("/hello/:id", ctx -> ctx.status(200).result("hello " + ctx.pathParam("id")));

        HttpRequest request = HttpRequest.newBuilder().POST(BodyPublishers.noBody()).uri(URI.create("http://localhost:" + app.port() + "/hello/foo")).build();
        final HttpResponse<String> mainUrlResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(mainUrlResponse.statusCode()).isEqualTo(200);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("POST /hello/:id");
    }

    @Test
    public void testAddLambdaAddHandler() throws Exception {
        app.addHandler(HandlerType.GET, "/test", ctx -> ctx.status(201));

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/test")).build();
        final HttpResponse<String> mainUrlResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(mainUrlResponse.statusCode()).isEqualTo(201);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("GET /test");
    }

    @Test
    public void testBefore() throws Exception {
        app.before("/before", ctx -> ctx.status(404));
        app.get("/before", ctx -> ctx.result("before"));

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/before")).build();
        final HttpResponse<String> mainUrlResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(mainUrlResponse.statusCode()).isEqualTo(404);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("GET /before");
    }

    @Test
    public void testAfter() throws Exception {
        app.after("/after", ctx -> ctx.header("foo", "bar"));
        app.after("/after", ctx -> ctx.status(302));
        app.get("/after", ctx -> ctx.result("after"));

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/after")).build();
        final HttpResponse<String> mainUrlResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(mainUrlResponse.statusCode()).isEqualTo(302);
        assertThat(mainUrlResponse.headers().firstValue("foo").get()).isEqualTo("bar");
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("GET /after");
        assertThat(reporter.getFirstTransaction().getSpanCount().getTotal().get()).isEqualTo(3);
        final List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(3);
        final List<String> names = spans.stream().map(Span::getNameAsString).collect(Collectors.toList());
        assertThat(names).containsExactly("GET /after", "AFTER /after", "AFTER /after");
    }

    @Test
    public void testNonAnonymousHandlerClass() throws Exception {
        app.get("/my-handler", new MyHandler());

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/my-handler")).build();
        final HttpResponse<String> mainUrlResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(mainUrlResponse.statusCode()).isEqualTo(400);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("GET /my-handler co.elastic.apm.agent.javalin.MyHandler");
    }

    @Test
    public void testClassMethodReturningLambda() throws Exception {
        final String endpoint = "/classmethod-returning-lambda";
        app.get(endpoint, new MyController().handler());

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(baseUrl + endpoint)).build();
        final HttpResponse<String> mainUrlResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(mainUrlResponse.statusCode()).isEqualTo(400);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("GET " + endpoint);
    }

    @Test
    public void testFuturesGetInstrumented() throws Exception {
        final String endpoint = "/test-future-instrumentation";
        app.get(endpoint, ctx -> ctx.result(CompletableFuture.runAsync(() -> ctx.status(404))));

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(baseUrl + endpoint)).build();
        final HttpResponse<String> mainUrlResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(mainUrlResponse.statusCode()).isEqualTo(404);
        assertThat(reporter.getFirstTransaction(500).getNameAsString()).isEqualTo("GET " + endpoint);
        final Span span = reporter.getFirstSpan(500);
        assertThat(span.getNameAsString()).isEqualTo("GET " + endpoint);
    }
}
