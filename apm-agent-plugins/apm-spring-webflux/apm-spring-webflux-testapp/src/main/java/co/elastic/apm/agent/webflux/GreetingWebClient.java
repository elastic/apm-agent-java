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
package co.elastic.apm.agent.webflux;

import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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
        return flatMapToString(exchange("/hello"));
    }

    public String getMappingError404() {
        return expectServerStatus("/error-404", 404);
    }

    public String getHandlerError() {
        return expectServerStatus("/error-handler", 500);
    }

    public String getMonoError() {
        return expectServerStatus("/error-mono", 500);
    }

    public String getMonoEmpty() {
        return expectServerStatus("/empty-mono", 200);
    }

    private String expectServerStatus(String path, int status) {
        Mono<ClientResponse> exchange = exchange(path)
            .map(r -> checkStatus(r, status));
        return flatMapToString(exchange);
    }

    private static ClientResponse checkStatus(ClientResponse r, int expectedStatus) {
        int statusCode = r.rawStatusCode();
        if (statusCode != expectedStatus) {
            throw new IllegalStateException(String.format("unexpected status code %d", statusCode));
        }
        return r;
    }

    private static String flatMapToString(Mono<ClientResponse> response) {
        String result = response.flatMap(res -> res.bodyToMono(String.class))
            .block();
        if (result == null) {
            throw new IllegalStateException("missing result");
        }
        return result;
    }

    private Mono<ClientResponse> exchange(String uri) {
        System.out.println(String.format("GET %s%s", baseUri, uri));

        // exchange or retrieve ?
        return client.get()
            .uri(uri)
            .accept(MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON)
            .exchange();
    }
}
