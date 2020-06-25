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
package co.elastic.apm.agent.spring.webflux.old;

import co.elastic.apm.agent.impl.transaction.Transaction;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.stagemonitor.util.IOUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class FunctionalHandlerInstrumentationTest extends AbstractWebFluxInstrumentationTest {

    @ParameterizedTest
    @CsvSource({"router/hello", "router/hello2"})
    public void shouldInstrumentSimpleGetRequest(String path) throws IOException {
        doRequest(new HttpGet(uri(path)), "Hello, Spring!", "");
    }

    @Test
    public void shouldInstrumentNestedRoutes() throws IOException {
        doRequest(new HttpGet(uri("router/nested")), "Hello, nested GET!", "/router/nested");
        doRequest(new HttpPost(uri("router/nested")), "Hello, nested POST!", "/router/nested");
    }

    // TODO still fails
    @Test
    void shouldInstrumentPathWithParameters() throws IOException {
        // TODO since 1234 is a path parameter, we should not get the URI as-is, but only the param template
        doRequest(new HttpGet(uri("router/with-parameters/1234")), "Hello, 1234!", "/router/with-parameters/{}");
    }

    private Transaction doRequest(HttpUriRequest request, String expectedBody, String expectedTransactionName) throws IOException {
        final HttpClient client = HttpClients.createDefault();
//        request.addHeader("Content-Length", "1234");
        final HttpResponse response = client.execute(request);
        final int statusCode = response.getStatusLine().getStatusCode();
        assertThat(statusCode).isEqualTo(HttpStatus.SC_OK);
        assertThat(IOUtils.toString(response.getEntity().getContent())).isEqualTo(expectedBody);

        // checking expected transaction

        Transaction transaction = reporter.getFirstTransaction(200);

        // TODO with lambdas, using class name for naming has no sense, thus it's probably better to name with method + path
        // TODO why do we need to care about content-length ? it should be provided within request itself
        assertThat(transaction.getNameAsString()).isEqualTo(request.getURI().getPath());
//        assertThat(transaction.getContext().getCustom("Content-Length"))
//            .describedAs("request headers should be captured")
//            .isEqualTo("1234");

        return transaction;
    }

//    @Test
//    public void shouldIgnoreNestedPostRequest() throws IOException {
//        final HandlerFunction<ServerResponse> hf1 = new HandlerFunctionWrapper<>(request -> ServerResponse.ok().body(BodyInserters.fromObject("Hello World")));
//        final HandlerFunction<ServerResponse> hf2 = request -> hf1.handle(request);
//
//        final RouterFunction<ServerResponse> route = RouterFunctions.route()
//            .GET("/hello", hf2)
//            .GET("/nested", hf1)
//            .build();
//
//        final Undertow server = startServer(route, 8081);
//
//        final HttpClient client = new DefaultHttpClient();
//        final HttpGet request = new HttpGet("http://localhost:8081/hello");
//        final HttpResponse response = client.execute(request);
//        final int statusCode = response.getStatusLine().getStatusCode();
//        Assert.assertEquals(statusCode, HttpStatus.SC_OK);
//
//        final List<Transaction> transactions = reporter.getTransactions();
//
//        Assert.assertEquals(transactions.size(), 1);
//        Assert.assertEquals(transactions.get(0).getNameAsString(), "GET /hello");
//
//        log.info("Request size: " + transactions.get(0).getContext().getCustom(WebFluxInstrumentationHelper.CONTENT_LENGTH));
//        log.info("Request handled in: " + transactions.get(0).getDuration());
//
//        server.stop();
//    }
}
