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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import javax.annotation.Nullable;

public class GreetingWebClient {

    private static final Logger logger = LoggerFactory.getLogger(GreetingWebClient.class);

    private final WebClient client;
    private final String baseUri;
    private final String pathPrefix;
    private final boolean useFunctionalEndpoint;
    private final int port;
    private final MultiValueMap<String, String> headers;

    // this client also applies a few basic checks to ensure that application behaves
    // as expected within unit tests and in packaged application without duplicating
    // all the testing logic.

    public GreetingWebClient(String host, int port, boolean useFunctionalEndpoint) {
        this.pathPrefix = useFunctionalEndpoint ? "/functional" : "/annotated";
        this.baseUri = String.format("http://%s:%d%s", host, port, pathPrefix);
        this.port = port;
        this.client = WebClient.builder()
            .baseUrl(baseUri)
            .clientConnector(new ReactorClientHttpConnector()) // allows to use either netty/reactor or jetty client
            .build();
        this.useFunctionalEndpoint = useFunctionalEndpoint;
        this.headers = new HttpHeaders();
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

    public String withPathParameter(String param) {
        return executeAndCheckRequest("GET", "/with-parameters/" + param, 200);
    }

    // nested routes, only relevant for functional routing
    public String nested(String method) {
        return executeAndCheckRequest(method, "/nested", 200);
    }

    public String duration(long durationMillis) {
        return executeAndCheckRequest("GET", "/duration?duration=" + durationMillis, 200);
    }

    public String executeAndCheckRequest(String method, String path, int expectedStatus) {
        logger.info("execute request : {} {}{}", method, baseUri, path);

        String result = client.method(HttpMethod.valueOf(method))
            .uri(path)
            .accept(MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON)
            .headers(httpHeaders -> httpHeaders.addAll(headers))
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

        logger.info("result = {}", result);
        return result;
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
}
