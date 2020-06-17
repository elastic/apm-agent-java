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
package co.elastic.apm.agent.spring.webflux.testapp;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import javax.annotation.Nullable;

public class GreetingWebClient {

    private final WebClient client;
    private final String baseUri;

    // this client also applies a few basic checks to ensure that application behaves
    // as expected within unit tests and in packaged application without duplicating
    // all the testing logic.

    public GreetingWebClient(String host, int port, boolean useFunctionalEndpoint) {
        this.baseUri = String.format("http://%s:%d/%s", host, port, useFunctionalEndpoint ? "router" : "controller");
        this.client = WebClient.create(baseUri);
    }

    public String getHelloMono() {
        return executeAndCheckRequest("GET", "/hello", 200);
    }

    public String getMappingError404() {
        return executeAndCheckRequest("GET", "/error-404", 404);
    }

    public String getHandlerError() {
        return executeAndCheckRequest("GET", "/error-handler", 500);
    }

    public String getMonoError() {
        return executeAndCheckRequest("GET", "/error-mono", 500);
    }

    @Nullable
    public String getMonoEmpty() {
        return executeAndCheckRequest("GET", "/empty-mono", 200);
    }

    public String methodMapping(String method) {
        return executeAndCheckRequest(method, "/hello-mapping", 200);
    }

    private String executeAndCheckRequest(String method, String path, int expectedStatus) {
        System.out.println(String.format("%s %s%s", method, baseUri, path));

        return client.method(HttpMethod.valueOf(method))
            .uri(path)
            .accept(MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON)
            .exchange()// exchange or retrieve ?
            .map(r -> {
                if (r.rawStatusCode() != expectedStatus) {
                    throw new IllegalStateException(String.format("unexpected status code %d", r.rawStatusCode()));
                }
                return r;
            })
            .flatMap(r -> r.bodyToMono(String.class))
            .blockOptional()
            .orElse("");
    }

}
