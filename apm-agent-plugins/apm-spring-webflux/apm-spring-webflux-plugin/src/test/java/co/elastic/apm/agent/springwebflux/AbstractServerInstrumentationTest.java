/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent.springwebflux;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.context.Url;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.reactor.TracedSubscriber;
import co.elastic.apm.agent.springwebflux.testapp.GreetingWebClient;
import co.elastic.apm.agent.springwebflux.testapp.WebFluxApplication;
import co.elastic.apm.agent.util.PotentiallyMultiValuedMap;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public abstract class AbstractServerInstrumentationTest extends AbstractInstrumentationTest {

    protected static WebFluxApplication.App app;
    protected GreetingWebClient client;

    @BeforeAll
    static void startApp() {
        app = WebFluxApplication.run(-1, "netty", true);
    }

    @AfterAll
    static void stopApp() {
        app.close();
    }

    @BeforeEach
    void beforeEach() {
        assertThat(reporter.getTransactions()).isEmpty();
        client = getClient();
    }

    @AfterEach
    void afterEach() {
        flushGcExpiry();
    }

    static void flushGcExpiry() {
        // ensure that both reactor & webflux storage maps are properly cleaned
        // if they are not, it means there is a leaked reference that isn't properly decremented.
        flushGcExpiry(TracedSubscriber.getContextMap(), 1);
        flushGcExpiry(TransactionAwareSubscriber.getTransactionMap(), 3);
    }

    protected abstract GreetingWebClient getClient();

    @Test
    void dispatchError() {
        StepVerifier.create(client.getHandlerError())
            .expectErrorMatches(expectClientError(500))
            .verify();

        String expectedName = client.useFunctionalEndpoint()
            ? "GET /functional/error-handler"
            : "GreetingAnnotated#handlerError";
        checkTransaction(getFirstTransaction(), expectedName, "GET", 500);
    }

    @Test
    void dispatchHello() {
        hello(true);
    }

    private void hello(boolean expectHeaders) {
        client.setHeader("random-value", "12345");
        client.setCookie("cookie", "gdpr-compliant-no-chocolate-here");

        StepVerifier.create(client.getHelloMono())
            .expectNext("Hello, Spring!")
            .verifyComplete();

        String expectedName = client.useFunctionalEndpoint()
            ? "GET /functional/hello"
            : "GreetingAnnotated#getHello";
        Transaction transaction = checkTransaction(getFirstTransaction(), expectedName, "GET", 200);

        Request request = transaction.getContext().getRequest();

        checkUrl(transaction, "/hello");

        PotentiallyMultiValuedMap headers = request.getHeaders();
        int headersCount = headers.size();
        if (expectHeaders) {

            assertThat(headersCount)
                .describedAs("unexpected headers count")
                .isEqualTo(6);

            assertThat(headers.getFirst("random-value"))
                .describedAs("non-standard request headers should be captured")
                .isEqualTo("12345");

            assertThat(headers.getFirst("Accept"))
                .isEqualTo("text/plain, application/json");

            assertThat(request.getCookies()
                .getFirst("cookie"))
                .isEqualTo("gdpr-compliant-no-chocolate-here");

        } else {

            assertThat(headersCount)
                .describedAs("no header expected")
                .isEqualTo(0);

            assertThat(request.getCookies().size())
                .describedAs("no cookie expected")
                .isEqualTo(0);
        }
    }

    @Test
    void headerCaptureDisabled() {
        CoreConfiguration coreConfig = getConfig().getConfig(CoreConfiguration.class);
        doReturn(false).when(coreConfig).isCaptureHeaders();

        hello(false);
    }

    @Test
    void dispatch404() {
        StepVerifier.create(client.getMappingError404())
            .expectErrorMatches(expectClientError(404))
            .verify();

        Transaction transaction = checkTransaction(getFirstTransaction(), "GET unknown route", "GET", 404);

        assertThat(transaction.getResult()).isEqualTo("HTTP 4xx");
        assertThat(transaction.getContext().getRequest().getMethod()).isEqualTo("GET");
        assertThat(transaction.getContext().getResponse().getStatusCode()).isEqualTo(404);
    }

    private Predicate<Throwable> expectClientError(int expectedStatus) {
        return error -> (error instanceof WebClientResponseException)
            && ((WebClientResponseException) error).getRawStatusCode() == expectedStatus;
    }

    @ParameterizedTest
    @CsvSource({"GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS", "TRACE"})
    void methodMapping(String method) {
        var verifier = StepVerifier.create(client.methodMapping(method));
        if ("HEAD".equals(method)) {
            verifier.verifyComplete();
        } else {
            verifier.expectNext(String.format("Hello, %s!", method))
                .verifyComplete();
        }

        String expectedName;

        if (client.useFunctionalEndpoint()) {
            expectedName = method + " /functional/hello-mapping";
        } else {
            String prefix = method.toLowerCase(Locale.ENGLISH);
            if (Arrays.asList("head", "options", "trace").contains((prefix))) {
                prefix = "other";
            }
            String methodName = prefix + "Mapping";
            expectedName = "GreetingAnnotated#" + methodName;
        }

        checkTransaction(getFirstTransaction(), expectedName, method, 200);
    }

    @Test
    void transactionDuration() {
        // while we can't accurately measure how long transaction takes, we need to ensure that what we measure is
        // at least somehow consistent, thus we test with a comfortable 50% margin
        long duration = 1000;
        Duration verifyDuration = StepVerifier.create(client.duration(duration))
            .expectNext(String.format("Hello, duration=%d!", duration))
            .verifyComplete();

        String expectedName = client.useFunctionalEndpoint() ? "GET /functional/duration" : "GreetingAnnotated#duration";
        Transaction transaction = checkTransaction(getFirstTransaction(), expectedName, "GET", 200);
        assertThat(transaction.getDurationMs())
            .isCloseTo(duration * 1d, Offset.offset(duration / 2d));

        assertThat(verifyDuration).isCloseTo(Duration.ofMillis(duration), Duration.ofMillis(duration / 2));

        checkUrl(transaction, "/duration?duration=" + duration);
    }

    @Test
    void shouldInstrumentPathWithParameters() {
        StepVerifier.create(client.withPathParameter("1234"))
            .expectNext("Hello, 1234!")
            .verifyComplete();

        String expectedName = client.useFunctionalEndpoint() ? "GET " + client.getPathPrefix() + "/with-parameters/{id}" : "GreetingAnnotated#withParameters";

        Transaction transaction = checkTransaction(getFirstTransaction(), expectedName, "GET", 200);

        checkUrl(transaction, "/with-parameters/1234");
    }

    @Test
    void allowCustomTransactionName() {
        StepVerifier.create(client.customTransactionName())
            .expectNextMatches(s -> s.startsWith("Hello, transaction="))
            .verifyComplete();

        assertThat(getFirstTransaction().getNameAsString()).isEqualTo("user-provided-name");
    }

    @Test
    void childSpans() {
        // only one element is expected with all values at once
        StepVerifier.create(client.childSpans(3, 10, 10))
            .expectNext("child 1child 2child 3")
            .verifyComplete();

        String expectedName = client.useFunctionalEndpoint() ? "GET " + client.getPathPrefix() + "/child-flux" : "GreetingAnnotated#getChildSpans";
        checkChildSpans(expectedName, "/child-flux?duration=10&count=3&delay=10");
    }

    @Test
    void childSpansServerSideEvents_shouldNotCreateTransaction() {
        // elements are streamed and provided separately
        StepVerifier.create(client.childSpansSSE(3, 10, 10))
            .expectNextMatches(checkSSE(1))
            .expectNextMatches(checkSSE(2))
            .expectNextMatches(checkSSE(3))
            .verifyComplete();

        // Transaction should be ignored as streaming is not supported.
        // Given the number of responses could be infinite, we can't instrument this as a regular transaction
        // that would have potentially infinite duration.

        reporter.assertNoTransaction(200);

        // While we'd like to not have any span captured here, there are spans that will be created and reported
        // because the transaction is made a noop very late.
        // reporter.assertNoSpan(200);
    }

    private static Predicate<ServerSentEvent<String>> checkSSE(final int index) {
        return sse -> {
            String data = sse.data();
            if (data == null) {
                return false;
            }
            return data.equals(String.format("child %d", index));
        };
    }

    private void checkChildSpans(String expectedName, String pathAndQuery) {
        Transaction transaction = checkTransaction(getFirstTransaction(), expectedName, "GET", 200);

        checkUrl(transaction, pathAndQuery);

        reporter.awaitSpanCount(3);
        reporter.getSpans().forEach(span -> {
            assertThat(span.getNameAsString()).endsWith(String.format("id=%s", span.getTraceContext().getId()));
        });
    }

    static void checkUrl(GreetingWebClient client, Transaction transaction, String pathAndQuery) {
        Url url = transaction.getContext().getRequest().getUrl();

        assertThat(url.getProtocol()).isEqualTo("http");
        assertThat(url.getHostname()).isEqualTo("localhost");

        String path = client.getPathPrefix() + pathAndQuery;
        String query = null;
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            query = path.substring(queryIndex + 1);
            path = path.substring(0, queryIndex);
        }

        assertThat(url.getPathname()).isEqualTo(path);
        assertThat(url.getSearch()).isEqualTo(query);
        assertThat(url.getPort()).isEqualTo(client.getPort());

        assertThat(url.getFull().toString())
            .isEqualTo(String.format("http://localhost:%d%s%s", client.getPort(), client.getPathPrefix(), pathAndQuery));
    }

    private void checkUrl(Transaction transaction, String pathAndQuery) {
        checkUrl(client, transaction, pathAndQuery);
    }

    protected Transaction getFirstTransaction() {
        return reporter.getFirstTransaction(200);
    }

    static Transaction checkTransaction(Transaction transaction, String expectedName, String expectedMethod, int expectedStatus) {
        assertThat(transaction.getType()).isEqualTo("request");
        assertThat(transaction.getNameAsString()).isEqualTo(expectedName);

        assertThat(transaction.getContext().getRequest().getMethod())
            .isEqualTo(expectedMethod);

        assertThat(transaction.getContext().getResponse().getStatusCode())
            .isEqualTo(expectedStatus);

        assertThat(transaction.getResult())
            .isEqualTo(String.format("HTTP %dxx", expectedStatus / 100));

        return transaction;
    }

}
