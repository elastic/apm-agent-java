/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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

import co.elastic.apm.agent.impl.transaction.Transaction;
import io.undertow.Undertow;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

public class FunctionalHandlerInstrumentationTest extends AbstractWebFluxInstrumentationTest {

    private static final Logger log = Logger.getLogger(FunctionalHandlerInstrumentationTest.class.getCanonicalName());

    @After
    public void after() {
        reporter.reset();
    }

    @Test
    public void shouldInstrumentSimpleGetRequest() throws IOException {
        final RouterFunction<ServerResponse> route = RouterFunctions.route()
            .GET("/hello", request -> ServerResponse.ok().body(BodyInserters.fromObject("Hello World")))
            .build();

        final Undertow server = startServer(route);

        final HttpClient client = new DefaultHttpClient();
        final HttpGet request = new HttpGet("http://localhost:8200/hello");
        final HttpResponse response = client.execute(request);
        final int statusCode = response.getStatusLine().getStatusCode();
        Assert.assertEquals(statusCode, HttpStatus.SC_OK);

        final List<Transaction> transactions = reporter.getTransactions();

        Assert.assertEquals(transactions.size(), 1);
        Assert.assertEquals(transactions.get(0).getNameAsString(), "GET /hello");

        log.info("Request size: " + transactions.get(0).getContext().getCustom(WebFluxInstrumentationHelper.CONTENT_LENGTH));
        log.info("Request handled in: " + transactions.get(0).getDuration());

        server.stop();
    }

    @Test
    public void shouldIgnoreNestedPostRequest() throws IOException {
        final HandlerFunction<ServerResponse> hf1 = new HandlerFunctionWrapper<>(request -> ServerResponse.ok().body(BodyInserters.fromObject("Hello World")));
        final HandlerFunction<ServerResponse> hf2 = request -> hf1.handle(request);

        final RouterFunction<ServerResponse> route = RouterFunctions.route()
            .GET("/hello", hf2)
            .GET("/nested", hf1)
            .build();

        final Undertow server = startServer(route);

        final HttpClient client = new DefaultHttpClient();
        final HttpGet request = new HttpGet("http://localhost:8200/hello");
        final HttpResponse response = client.execute(request);
        final int statusCode = response.getStatusLine().getStatusCode();
        Assert.assertEquals(statusCode, HttpStatus.SC_OK);

        final List<Transaction> transactions = reporter.getTransactions();

        Assert.assertEquals(transactions.size(), 1);
        Assert.assertEquals(transactions.get(0).getNameAsString(), "GET /hello");

        log.info("Request size: " + transactions.get(0).getContext().getCustom(WebFluxInstrumentationHelper.CONTENT_LENGTH));
        log.info("Request handled in: " + transactions.get(0).getDuration());

        server.stop();
    }
}
