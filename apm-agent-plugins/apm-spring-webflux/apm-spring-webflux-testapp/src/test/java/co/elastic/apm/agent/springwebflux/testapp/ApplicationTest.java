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
package co.elastic.apm.agent.springwebflux.testapp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

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
            .expectNextMatches( s->s.contains("/error-404"))
            .verifyComplete();
    }

    @Test
    void handlerException() {
        StepVerifier.create(client.getHandlerError())
            .expectNextMatches(s->s.contains("intentional handler exception"))
            .verifyComplete();
    }

    @Test
    void handlerMonoError() {
        StepVerifier.create(client.getMonoError())
            .expectNextMatches(s-> s.equals("error handler: intentional error"))
            .verifyComplete();
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
        assertThat(client.childSpans(3, 50, 10))
            .isEqualTo("child 1child 2child 3");
    }
}
