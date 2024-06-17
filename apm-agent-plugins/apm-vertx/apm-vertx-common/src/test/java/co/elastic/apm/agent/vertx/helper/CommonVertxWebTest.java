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
package co.elastic.apm.agent.vertx.helper;

import co.elastic.apm.agent.configuration.CoreConfigurationImpl;
import co.elastic.apm.agent.impl.TracerInternalApiUtils;
import co.elastic.apm.agent.impl.context.RequestImpl;
import co.elastic.apm.agent.impl.context.TransactionContextImpl;
import co.elastic.apm.agent.impl.transaction.AbstractSpanImpl;
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.sdk.internal.util.VersionUtils;
import co.elastic.apm.agent.tracer.metadata.PotentiallyMultiValuedMap;
import co.elastic.apm.agent.vertx.AbstractVertxWebHelper;
import io.vertx.core.Handler;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.junit5.VertxExtension;
import okhttp3.MediaType;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.CharBuffer;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

@ExtendWith(VertxExtension.class)
public abstract class CommonVertxWebTest extends AbstractVertxWebTest {

    @Test
    void testBasicVertxWebCall() throws Exception {
        Response response = http().get("/test");
        expectTransaction(response, "/test", DEFAULT_RESPONSE_BODY, "GET /test", 200);
        TransactionImpl transaction = reporter.getFirstTransaction();
        assertThat(transaction.getFrameworkName()).isEqualTo(AbstractVertxWebHelper.FRAMEWORK_NAME);

        String vertxVersion = VersionUtils.getVersion(RoutingContext.class, "io.vertx", "vertx-web");
        assertThat(vertxVersion).startsWith(String.format("%d.", getMajorVersion()));

        assertThat(transaction.getFrameworkVersion()).isEqualTo(vertxVersion);
        assertThat(transaction.getResult()).isEqualTo("HTTP 2xx");
        assertThat(transaction.getType()).isEqualTo("request");
        assertThat(transaction.getContext().getRequest().getUrl().getFull().toString()).isEqualTo(schema() + "://localhost:" + port() + "/test");
        assertThat(reporter.getSpans().size()).isEqualTo(0);
    }

    @Test
    void testWithEmptyPath() throws Exception {
        Response response = http().get("");
        expectTransaction(response, "/", DEFAULT_RESPONSE_BODY, "GET /", 200);
    }

    protected abstract int getMajorVersion();

    @Test
    void testWebCallWithParameter() throws Exception {
        String path = "/test/123";
        Response response = http().get(path);
        expectTransaction(response, path, DEFAULT_RESPONSE_BODY, "GET /test/:param", 200);
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getUrl().getFull().toString()).isEqualTo(schema() + "://localhost:" + port() + "/test/123");
        assertThat(reporter.getSpans().size()).isEqualTo(0);
    }

    @Test
    void testCallWithoutHeaders() throws Exception {
        doReturn(false).when(coreConfiguration).isCaptureHeaders();

        Map<String, String> headers = Map.of("Key1", "Value1", "Key2", "Value2");
        Response response = http().get("/test", headers);
        expectTransaction(response, "/test", DEFAULT_RESPONSE_BODY, "GET /test", 200);

        TransactionContextImpl context = reporter.getFirstTransaction().getContext();
        assertThat(context.getRequest().getHeaders().size()).isEqualTo(0);
        assertThat(context.getResponse().getHeaders().size()).isEqualTo(0);
        assertThat(context.getRequest().getFormUrlEncodedParameters().size()).isEqualTo(0);
    }

    @Test
    void testCallWithCustomHeaders() throws Exception {
        Map<String, String> headers = Map.of("Key1", "Value1", "Key2", "Value2");

        Response response = http().get("/test", headers);
        expectTransaction(response, "/test", DEFAULT_RESPONSE_BODY, "GET /test", 200);

        PotentiallyMultiValuedMap requestHeaders = reporter.getFirstTransaction().getContext().getRequest().getHeaders();
        assertThat(requestHeaders.containsIgnoreCase("Key1")).isEqualTo(true);
        assertThat(requestHeaders.getFirst("Key1")).isEqualTo("Value1");
        assertThat(requestHeaders.containsIgnoreCase("Key2")).isEqualTo(true);
        assertThat(requestHeaders.getFirst("Key2")).isEqualTo("Value2");
    }

    @Test
    void testCallWithPathAsTransactionName() throws Exception {
        doReturn(true).when(webConfiguration).isUsePathAsName();

        Response response = http().get("/test/secondSegment");
        expectTransaction(response, "/test/secondSegment", DEFAULT_RESPONSE_BODY, "GET /test/secondSegment", 200);
    }

    @Test
    void testCallWithPathGroupAsTransactionName() throws Exception {
        doReturn(true).when(webConfiguration).isUsePathAsName();
        doReturn(List.of(WildcardMatcher.valueOf("/test/*/group"))).when(webConfiguration).getUrlGroups();
        Response response = http().get("/test/secondSegment/group");
        expectTransaction(response, "/test/secondSegment/group", DEFAULT_RESPONSE_BODY, "GET /test/*/group", 200);
    }

    @Test
    void testCallWithQueryParameters() throws Exception {
        doReturn(CoreConfigurationImpl.EventType.ALL).when(coreConfiguration).getCaptureBody();
        doReturn(List.of(WildcardMatcher.valueOf("application/x-www-form-urlencoded*"))).when(webConfiguration).getCaptureContentTypes();

        Response response = http().post("/post?par1=abc&par2=xyz", "Some Body", MediaType.get("application/x-www-form-urlencoded"));
        expectTransaction(response, "/post", DEFAULT_RESPONSE_BODY, "POST /post", 200);

        RequestImpl request = reporter.getFirstTransaction().getContext().getRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getFormUrlEncodedParameters().size()).isEqualTo(2);
        assertThat(request.getFormUrlEncodedParameters().containsIgnoreCase("par1")).isEqualTo(true);
        assertThat(request.getFormUrlEncodedParameters().getFirst("par1")).isEqualTo("abc");
        assertThat(request.getFormUrlEncodedParameters().containsIgnoreCase("par2")).isEqualTo(true);
        assertThat(request.getFormUrlEncodedParameters().getFirst("par2")).isEqualTo("xyz");
        assertThat(request.getUrl().getSearch()).isEqualTo("par1=abc&par2=xyz");
        assertThat(request.getBody()).isEqualTo(request.getFormUrlEncodedParameters());
    }

    @Test
    void testCallWithBodyCapturing() throws Exception {
        doReturn(CoreConfigurationImpl.EventType.ALL).when(coreConfiguration).getCaptureBody();
        doReturn(List.of(WildcardMatcher.valueOf("application/json*"))).when(webConfiguration).getCaptureContentTypes();

        String jsonBody = "{\"key\":\"Some JSON\"}";

        Response response = http().post("/post?par1=abc&par2=xyz", jsonBody, MediaType.get("application/json"));
        expectTransaction(response, "/post", DEFAULT_RESPONSE_BODY, "POST /post", 200);

        RequestImpl request = reporter.getFirstTransaction().getContext().getRequest();
        assertThat(request.getFormUrlEncodedParameters().size()).isEqualTo(0);
        assertThat(request.getUrl().getSearch()).isEqualTo("par1=abc&par2=xyz");
        assertThat(request.getBody()).isInstanceOf(CharBuffer.class);
        assertThat(request.getBody().toString()).isEqualTo(jsonBody);
    }

    @Test
    void testNoopInstrumentation() throws Exception {
        TracerInternalApiUtils.pauseTracer(tracer);
        http().get("/test");
        reporter.assertNoTransaction(500);
        assertThat(reporter.getSpans().size()).isEqualTo(0);
    }

    @Test
    void testMultiHandlerCall() throws Exception {
        String path = "/multi-handler";
        Response response = http().get(path);
        expectTransaction(response, path, DEFAULT_RESPONSE_BODY, "GET " + path, 200);

    }

    @Test
    void testUnknownRoute() throws Exception {
        String path = "/unknown";
        Response response = http().get(path);
        expectTransaction(response, path, NOT_FOUND_RESPONSE_BODY, "GET unknown route", 404);
    }

    @ParameterizedTest
    @ValueSource(strings = {CALL_BLOCKING, CALL_ON_CONTEXT, CALL_SCHEDULED})
    void testContextPropagationToVertxEngine(String callType) throws Exception {
        String path = "/" + callType;
        Response response = http().get(path);
        expectTransaction(response, path, DEFAULT_RESPONSE_BODY, "GET " + path, 200);
        reporter.awaitSpanCount(1);
        assertThat(reporter.getSpans()).hasSize(1);
        SpanImpl span = reporter.getFirstSpan();
        assertThat(span.getNameAsString()).isEqualTo(callType + "-child-span");
        assertThat(span.getParent()).isEqualTo(reporter.getFirstTransaction());
    }

    @ParameterizedTest
    @ValueSource(strings = {CALL_BLOCKING, CALL_ON_CONTEXT})
    void testContextPropagationToVertxContext(String callType) throws Exception {
        String path = "/" + callType + "_context";
        Response response = http().get(path);
        expectTransaction(response, path, DEFAULT_RESPONSE_BODY, "GET " + path, 200);
        reporter.awaitSpanCount(1);
        assertThat(reporter.getSpans()).hasSize(1);
        SpanImpl span = reporter.getFirstSpan();
        assertThat(span.getNameAsString()).isEqualTo(callType + "-child-span");
        assertThat(span.getParent()).isEqualTo(reporter.getFirstTransaction());
    }

    @Test
    void testTransactionRecyclingWithAsyncSpan() throws Exception {
        String path = "/" + CALL_SCHEDULED_SHIFTED;
        Response response = http().get(path);
        expectTransaction(response, path, DEFAULT_RESPONSE_BODY, "GET " + path, 200);
        TransactionImpl transaction = reporter.getFirstTransaction();

        // Mock reporter expects the reference count to be at 1 at test end,
        // so we decrement it here to emulate real reporter behaviour which would recycle ended transactions directly.
        reporter.decrementReferences();
        reporter.awaitSpanCount(1);
        assertThat(transaction.getSpanCount().getReported().get()).isEqualTo(1);
        assertThat(reporter.getSpans().size()).isEqualTo(1);
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo(CALL_SCHEDULED_SHIFTED + "-child-span");
        assertThat(reporter.getFirstSpan().getParent()).isEqualTo(reporter.getFirstTransaction());

        // Increase reference counter again to meet mock reporter expectation for test end.
        transaction.incrementReferences();
    }

    @Test
    void testParallel() {
        ExecutorService executorService = Executors.newFixedThreadPool(5);
        executorService.submit(() -> http().get("/parallel/1"));
        executorService.submit(() -> http().get("/parallel/2"));
        executorService.submit(() -> http().get("/parallel/3"));
        executorService.submit(() -> http().get("/parallel/4"));
        executorService.submit(() -> http().get("/parallel/5"));

        long timeout = 5000; // use a longer timeout as this test includes explicit delayed async processing
        int expectedTransactionCount = 5;
        reporter.awaitUntilAsserted(timeout, () -> assertThat(reporter.getNumReportedTransactions())
            .describedAs("expecting %d transactions, transactions = %s", expectedTransactionCount, reporter.getTransactions())
            .isEqualTo(expectedTransactionCount));

        int expectedSpanCount = expectedTransactionCount * 3;
        reporter.awaitUntilAsserted(timeout, () -> assertThat(reporter.getNumReportedSpans())
            .describedAs("expecting %d spans, spans = %s", expectedSpanCount, reporter.getSpans())
            .isEqualTo(expectedSpanCount));

        assertThat(reporter.getTransactions().stream().map(transaction -> transaction.getContext().getRequest().getUrl().getPathname()))
            .containsExactlyInAnyOrder("/parallel/1", "/parallel/2", "/parallel/3", "/parallel/4", "/parallel/5");
        assertThat(reporter.getTransactions().stream().map(AbstractSpanImpl::getNameAsString).distinct()).containsExactlyInAnyOrder("GET /parallel/:param");
        assertThat(reporter.getFirstTransaction().getSpanCount().getTotal().get()).isEqualTo(3);

        AbstractSpanImpl t1 = transaction("/parallel/1");
        AbstractSpanImpl t2 = transaction("/parallel/2");
        AbstractSpanImpl t3 = transaction("/parallel/3");
        AbstractSpanImpl t4 = transaction("/parallel/4");
        AbstractSpanImpl t5 = transaction("/parallel/5");


        assertThat(spansContaining("-1").size()).isEqualTo(3);
        assertThat(spansContaining("-1").stream().map(SpanImpl::getParent).distinct()).containsExactly(t1);
        assertThat(spansContaining("-2").size()).isEqualTo(3);
        assertThat(spansContaining("-2").stream().map(SpanImpl::getParent).distinct()).containsExactly(t2);
        assertThat(spansContaining("-3").size()).isEqualTo(3);
        assertThat(spansContaining("-3").stream().map(SpanImpl::getParent).distinct()).containsExactly(t3);
        assertThat(spansContaining("-4").size()).isEqualTo(3);
        assertThat(spansContaining("-4").stream().map(SpanImpl::getParent).distinct()).containsExactly(t4);
        assertThat(spansContaining("-5").size()).isEqualTo(3);
        assertThat(spansContaining("-5").stream().map(SpanImpl::getParent).distinct()).containsExactly(t5);

        executorService.shutdown();
    }

    @Test
    void testException() throws Exception {
        Response response = http().get("/exception");
        expectTransaction(response, "/exception", INTERNAL_SERVER_ERROR, "GET /exception", 500);

        assertThat(reporter.getErrors().size()).isEqualTo(1);
        assertThat(reporter.getFirstError().getException().getMessage()).isEqualTo(EXCEPTION_MESSAGE);
    }

    @Test
    void testWrongMethod() throws Exception {
        Response response = http().get("/post");
        expectTransaction(response, "/post", "", "GET unknown route", 405);
    }

    private List<SpanImpl> spansContaining(String name) {
        return reporter.getSpans().stream().filter(span -> span.getNameAsString().contains(name)).collect(Collectors.toList());
    }

    private TransactionImpl transaction(String path) {
        return reporter.getTransactions().stream().filter(t -> t.getContext().getRequest().getUrl().getPathname().equals(path)).findAny().get();
    }

    @Override
    protected void initRoutes(Router router) {
        router.get("/test").handler(getDefaultHandlerImpl());
        router.get("/").handler(getDefaultHandlerImpl());
        router.post("/post").handler(BodyHandler.create().setHandleFileUploads(false)).handler(getDefaultHandlerImpl());

        router.get("/test/:param").handler(getDefaultHandlerImpl());

        router.get("/test/:param/group").handler(getDefaultHandlerImpl());

        router.get("/exception/without/handler").handler(getDefaultHandlerImpl());

        router.get("/" + CALL_BLOCKING).handler(routingContext -> routingContext.vertx()
            .executeBlocking(tid -> new HandlerWithCustomNamedSpan(getDefaultHandlerImpl(), routingContext, CALL_BLOCKING).handle(null), result -> {
            }));
        router.get("/" + CALL_BLOCKING + "_context").handler(routingContext -> routingContext.vertx().getOrCreateContext()
            .executeBlocking(tid -> new HandlerWithCustomNamedSpan(getDefaultHandlerImpl(), routingContext, CALL_BLOCKING).handle(null), result -> {
            }));

        router.get("/" + CALL_SCHEDULED).handler(routingContext -> routingContext.vertx()
            .setTimer(1, tid -> new HandlerWithCustomNamedSpan(getDefaultHandlerImpl(), routingContext, CALL_SCHEDULED).handle(null)));
        router.get("/" + CALL_SCHEDULED_SHIFTED).handler(routingContext -> {
            routingContext.vertx()
                .setTimer(500, tid -> {
                    SpanImpl child = Objects.requireNonNull(tracer.getActive()).createSpan();
                    child.withName(CALL_SCHEDULED_SHIFTED + "-child-span");
                    child.end();
                });
            getDefaultHandlerImpl().handle(routingContext);
        });

        router.get("/" + CALL_ON_CONTEXT).handler(routingContext -> routingContext.vertx()
            .runOnContext(new HandlerWithCustomNamedSpan(getDefaultHandlerImpl(), routingContext, CALL_ON_CONTEXT)));
        router.get("/" + CALL_ON_CONTEXT + "_context").handler(routingContext -> routingContext.vertx().getOrCreateContext()
            .runOnContext(new HandlerWithCustomNamedSpan(getDefaultHandlerImpl(), routingContext, CALL_ON_CONTEXT)));

        router.get("/parallel/:param").handler(routingContext -> routingContext.vertx().setTimer(1, tid_1 -> {
            SpanImpl asyncChild = Objects.requireNonNull(tracer.getActive()).createSpan();
            asyncChild.withName("first-child-" + routingContext.pathParam("param"));

            routingContext.vertx().executeBlocking(p -> {
                SpanImpl blockingChild = Objects.requireNonNull(tracer.getActive()).createSpan();
                blockingChild.withName("second-child-" + routingContext.pathParam("param"));

                try {
                    Thread.sleep(new Random(System.currentTimeMillis()).nextInt(100));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                routingContext.vertx().setTimer(1, tid_2 -> {
                    SpanImpl thirdChild = Objects.requireNonNull(tracer.getActive()).createSpan();
                    thirdChild.withName("third-child-" + routingContext.pathParam("param"));
                    getDefaultHandlerImpl().handle(routingContext);
                    thirdChild.end();
                });
                blockingChild.end();
            }, r -> {
            });
            asyncChild.end();
        }));

        router.get("/multi-handler")
            .handler(routingContext -> routingContext.vertx().executeBlocking(tid -> routingContext.next(), result -> {
            }))
            .handler(routingContext -> routingContext.vertx().setTimer(5, tid -> routingContext.next()))
            .handler(getDefaultHandlerImpl());

        router.route("/exception")
            .handler(routingContext -> {
                throw new RuntimeException(EXCEPTION_MESSAGE);
            });
    }

    protected abstract Handler<RoutingContext> getDefaultHandlerImpl();

}
