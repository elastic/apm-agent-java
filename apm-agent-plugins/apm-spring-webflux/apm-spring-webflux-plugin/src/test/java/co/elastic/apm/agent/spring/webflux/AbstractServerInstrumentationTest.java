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
package co.elastic.apm.agent.spring.webflux;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.spring.webflux.testapp.GreetingWebClient;
import co.elastic.apm.agent.spring.webflux.testapp.WebFluxApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractServerInstrumentationTest extends AbstractInstrumentationTest {

    // TODO support random port for easier testing (without any spring-related test).
    public static final int PORT = 8081;

    private static ConfigurableApplicationContext context;

    @BeforeAll
    static void startApp() {
        context = WebFluxApplication.run(PORT);
    }

    @AfterAll
    static void stopApp() {
        context.close();
    }

    @BeforeEach
    void beforeEach(){
        assertThat(reporter.getTransactions()).isEmpty();
    }

    protected abstract GreetingWebClient getClient();

    @Test
    void dispatchHello() {
        GreetingWebClient client = getClient();
        assertThat(client.getHelloMono()).isEqualTo("Hello, Spring!");

        String expectedName = client.useFunctionalEndpoint()
            ? "/functional/hello"
            : "co.elastic.apm.agent.spring.webflux.testapp.GreetingAnnotated#getHello";
        Transaction transaction = checkTransaction(reporter.getFirstTransaction(500), expectedName);

        // TODO : add assertions to ensure that request/response is properly captured

        // HTTP method is not set (yet), as adding it requires to also have the full URL
//        assertThat(transaction.getContext().getRequest().getMethod()).isEqualTo("GET");

        // status code is not set (yet)
//        assertThat(transaction.getResult()).isEqualTo("HTTP 2xx");
//        assertThat(transaction.getContext().getResponse().getStatusCode()).isEqualTo(200);
    }

    @Test
    void dispatch404() {
        GreetingWebClient client = getClient();
        assertThat(client.getMappingError404()).contains("Not Found");

        Transaction transaction = checkTransaction(getFirstTransaction(), client.getPathPrefix() + "/error-404");// TODO might be "unknown route" like with servlets

        assertThat(transaction.getResult()).isEqualTo("HTTP 4xx");
//        assertThat(transaction.getContext().getRequest().getMethod()).isEqualTo("GET");
        assertThat(transaction.getContext().getResponse().getStatusCode()).isEqualTo(404);
    }

    @ParameterizedTest
    @CsvSource({"GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS", "TRACE"})
    void methodMapping(String method) {
        assertThat(getClient().methodMapping(method))
            .isEqualTo("HEAD".equals(method) ? "" : String.format("Hello, %s!", method));

        String expectedName;

        if (getClient().useFunctionalEndpoint()) {
            expectedName = "/functional/hello-mapping";
        } else {
            String prefix = method.toLowerCase(Locale.ENGLISH);
            if (Arrays.asList("head", "options", "trace").contains((prefix))) {
                prefix = "other";
            }
            String methodName = prefix + "Mapping";
            expectedName = "co.elastic.apm.agent.spring.webflux.testapp.GreetingAnnotated#" + methodName;
        }

        Transaction transaction = checkTransaction(getFirstTransaction(), expectedName);

        // TODO : check for HTTP method in request
    }

    protected Transaction getFirstTransaction() {
        return reporter.getFirstTransaction(200);
    }

    protected Transaction checkTransaction(Transaction transaction, String expectedName) {
        assertThat(transaction.getType()).isEqualTo("request");
        assertThat(transaction.getNameAsString()).isEqualTo(expectedName);

        return transaction;
    }

}
