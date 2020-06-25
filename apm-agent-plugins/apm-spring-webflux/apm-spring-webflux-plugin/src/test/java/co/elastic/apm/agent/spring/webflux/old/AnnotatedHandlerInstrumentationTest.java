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
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

public class AnnotatedHandlerInstrumentationTest extends AbstractWebFluxInstrumentationTest {

    @Test
    public void shouldDoGetRequest() throws Exception {
        checkTransaction(new HttpGet(uri("test")), "SimpleRestController#get");
    }

    @Test
    public void shouldDoGetRequestWithoutParams() throws Exception {
        checkTransaction(new HttpGet(uri("test2")), "SimpleRestController#get");
    }

    @Test
    public void shouldDoPostRequest() throws Exception {
        checkTransaction(new HttpPost(uri("test")), "SimpleRestController#post");
    }

    @Test
    public void shouldDoPutRequest() throws Exception {
        checkTransaction(new HttpPut(uri("test")), "SimpleRestController#put");
    }

    @Test
    public void shouldDoDeleteRequest() throws Exception {
        checkTransaction(new HttpDelete(uri("test")), "SimpleRestController#delete");
    }

    @Test
    public void shouldDoPatchRequest() throws Exception {
        checkTransaction(new HttpPatch(uri("test")), "SimpleRestController#patch");
    }

    @Test
    public void shouldDoChainedGetRequest() throws Exception {
        checkTransaction(new HttpGet(uri("/test/chained")), "SimpleRestController#getChained");
    }

    @Test
    public void shouldDoChainedPostRequest() throws Exception {
        checkTransaction(new HttpPost(uri("test/chained")), "SimpleRestController#postChained");
    }

    @Test
    public void shouldDoChainedPutRequest() throws Exception {
        checkTransaction(new HttpPut(uri("test/chained")), "SimpleRestController#putChained");
    }

    @Test
    public void shouldDoChainedDeleteRequest() throws Exception {
        checkTransaction(new HttpDelete(uri("test/chained")), "SimpleRestController#deleteChained");
    }

    @Test
    public void shouldDoChainedPatchRequest() throws Exception {
        checkTransaction(new HttpPatch(uri("test/chained")), "SimpleRestController#patchChained");
    }

    private static void checkTransaction(HttpUriRequest request, String expectedName) throws Exception {
        final HttpClient client = HttpClientBuilder.create().build();
        final HttpResponse response = client.execute(request);
        final int statusCode = response.getStatusLine().getStatusCode();
        Assert.assertEquals(statusCode, HttpStatus.SC_OK);

        Transaction transaction = reporter.getFirstTransaction(200);
        Assert.assertEquals(transaction.getNameAsString(), String.format("co.elastic.apm.agent.spring.webflux.testapp.%s", expectedName));
    }
}

