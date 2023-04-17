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
package co.elastic.apm.agent.springwebflux.testapp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.test.StepVerifier;

import java.util.function.Predicate;

public abstract class ApplicationTest {

    protected GreetingWebClient client;

    protected abstract GreetingWebClient createClient();

    @BeforeEach
    void beforeEach() {
        // test with functional endpoints only, testing both functional and annotated controller should be properly
        // covered by instrumentation tests.
        client = createClient();
    }

    @Test
    void helloMono() {
        StepVerifier.create(client.getHelloMono())
            .expectNext("Hello, Spring!")
            .verifyComplete();
    }

    @Test
    void mappingError() {
        StepVerifier.create(client.getMappingError404())
            .expectErrorMatches(expectClientError(404))
            .verify();
    }

    @Test
    void handlerException() {
        StepVerifier.create(client.getHandlerError())
            .expectErrorMatches(expectClientError(500))
            .verify();
    }

    @Test
    void handlerMonoError() {
        StepVerifier.create(client.getMonoError())
            .expectErrorMatches(expectClientError(500))
            .verify();
    }

    @Test
    void handlerMonoEmpty() {
        StepVerifier.create(client.getMonoEmpty())
            .verifyComplete();
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
    }

    @Test
    void withPathParameter() {
        StepVerifier.create(client.withPathParameter("42"))
            .expectNext("Hello, 42!")
            .verifyComplete();
    }

    @Test
    void withChildrenSpans() {
        StepVerifier.create(client.childSpans(3, 50, 10))
            .expectNext("child 1child 2child 3")
            .verifyComplete();
    }

    @Test
    void withChildrenSpansSSE() {
        StepVerifier.create(client.childSpansSSE(3, 50, 10))
            .expectNextMatches(checkSSE(1))
            .expectNextMatches(checkSSE(2))
            .expectNextMatches(checkSSE(3))
            .verifyComplete();
    }

    @Test
    void customTransactionName() {
        StepVerifier.create(client.customTransactionName())
            .expectNext("Hello, transaction=null!")
            .verifyComplete();
    }

    @Test
    void duration() {
        StepVerifier.create(client.duration(42))
            .expectNext("Hello, duration=42!")
            .verifyComplete();
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

    private Predicate<Throwable> expectClientError(int expectedStatus) {
        return error -> (error instanceof WebClientResponseException)
            && ((WebClientResponseException) error).getRawStatusCode() == expectedStatus;
    }
}
