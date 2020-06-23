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

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.spring.webflux.testapp.WebFluxApplication;
import net.bytebuddy.agent.ByteBuddyAgent;
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
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.SpringApplication;

import java.util.Collections;
import java.util.List;

public class AnnotatedHandlerInstrumentationTest {

    private static MockReporter reporter;
    private static ElasticApmTracer tracer;

    @BeforeClass
    @BeforeAll
    public static void setUpAll() {
        reporter = new MockReporter();
        tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(SpyConfiguration.createSpyConfig())
            .reporter(reporter)
            .build();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install(),
            Collections.singletonList(new ServletHttpHandlerAdapterInstrumentation()));

        SpringApplication.run(WebFluxApplication.class);
    }

    @AfterClass
    @AfterAll
    public static void afterAll() {
        ElasticApmAgent.reset();
    }

    @After
    public void after() {
        reporter.reset();
    }

    @Test
    public void shouldDoGetRequest() throws Exception {
        shouldDoRequest(new HttpGet("http://localhost:8080/test"));
    }

    @Test
    public void shouldDoGetRequestWithoutParams() throws Exception {
        shouldDoRequest(new HttpGet("http://localhost:8080/test2"));
    }

    @Test
    public void shouldDoPostRequest() throws Exception {
        shouldDoRequest(new HttpPost("http://localhost:8080/test"));
    }

    @Test
    public void shouldDoPutRequest() throws Exception {
        shouldDoRequest(new HttpPut("http://localhost:8080/test"));
    }

    @Test
    public void shouldDoDeleteRequest() throws Exception {
        shouldDoRequest(new HttpDelete("http://localhost:8080/test"));
    }

    @Test
    public void shouldDoPatchRequest() throws Exception {
        shouldDoRequest(new HttpPatch("http://localhost:8080/test"));
    }


    @Test
    public void shouldDoChainedGetRequest() throws Exception {
        shouldDoRequest(new HttpGet("http://localhost:8080/test/chained"));
    }

    @Test
    public void shouldDoChainedPostRequest() throws Exception {
        shouldDoRequest(new HttpPost("http://localhost:8080/test/chained"));
    }

    @Test
    public void shouldDoChainedPutRequest() throws Exception {
        shouldDoRequest(new HttpPut("http://localhost:8080/test/chained"));
    }

    @Test
    public void shouldDoChainedDeleteRequest() throws Exception {
        shouldDoRequest(new HttpDelete("http://localhost:8080/test/chained"));
    }

    @Test
    public void shouldDoChainedPatchRequest() throws Exception {
        shouldDoRequest(new HttpPatch("http://localhost:8080/test/chained"));
    }

    private static void shouldDoRequest(HttpUriRequest request) throws Exception {
        final HttpClient client = HttpClientBuilder.create().build();
        final HttpResponse response = client.execute(request);
        final int statusCode = response.getStatusLine().getStatusCode();
        Assert.assertEquals(statusCode, HttpStatus.SC_OK);

        final List<Transaction> transactions = reporter.getTransactions();
        Assert.assertEquals(transactions.size(), 1);
        Assert.assertEquals(transactions.get(0).getNameAsString(), String.format("%s /test", request.getMethod()));
    }
}

