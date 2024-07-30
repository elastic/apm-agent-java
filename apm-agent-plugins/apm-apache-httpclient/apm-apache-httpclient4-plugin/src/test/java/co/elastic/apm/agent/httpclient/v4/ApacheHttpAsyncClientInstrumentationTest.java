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
package co.elastic.apm.agent.httpclient.v4;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.tracer.Outcome;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.conn.UnsupportedSchemeException;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.assertj.core.api.Assertions;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ApacheHttpAsyncClientInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private static CloseableHttpAsyncClient client;

    @BeforeClass
    public static void setUp() {
        client = HttpAsyncClients.createDefault();
        client.start();
    }

    @AfterClass
    public static void tearDown() throws IOException {
        client.close();
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    @Override
    protected void performGet(String path) throws Exception {
        final CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();

        RequestConfig requestConfig = RequestConfig.custom()
            .setCircularRedirectsAllowed(true)
            .build();
        HttpClientContext httpClientContext = HttpClientContext.create();
        httpClientContext.setRequestConfig(requestConfig);
        client.execute(new HttpGet(path), httpClientContext, new FutureCallback<>() {
            @Override
            public void completed(HttpResponse result) {
                responseFuture.complete(result);
            }

            @Override
            public void failed(Exception ex) {
                responseFuture.completeExceptionally(ex);
            }

            @Override
            public void cancelled() {
                responseFuture.cancel(true);
            }
        });

        responseFuture.get();
    }

    @Override
    protected boolean isBodyCapturingSupported() {
        return true;
    }

    @Override
    public void testPostBodyCaptureForExistingSpan() throws Exception {
        //TODO: async http client instrumentation does not support capturing bodies for existing spans yet
    }

    @Override
    protected void performPost(String path, byte[] data, String contentTypeHeader) throws Exception {
        final CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();

        HttpClientContext httpClientContext = HttpClientContext.create();
        HttpPost request = new HttpPost(path);
        request.setEntity(new InputStreamEntity(new ByteArrayInputStream(data)));
        request.setHeader("Content-Type", contentTypeHeader);
        client.execute(request, httpClientContext, new FutureCallback<>() {
            @Override
            public void completed(HttpResponse result) {
                responseFuture.complete(result);
            }

            @Override
            public void failed(Exception ex) {
                responseFuture.completeExceptionally(ex);
            }

            @Override
            public void cancelled() {
                responseFuture.cancel(true);
            }
        });

        responseFuture.get();
    }

    @Test
    public void testSpanFinishOnEarlyException() throws Exception {

        client.close(); //this forces execute to immediately exit with an exception

        reporter.disableCheckServiceTarget();
        reporter.disableCheckDestinationAddress();
        try {
            assertThatThrownBy(() -> performGet(getBaseUrl() + "/")).isInstanceOf(IllegalStateException.class);
        } finally {
            //Reset state for other tests
            setUp();
            reporter.resetChecks();
        }
        assertThat(reporter.getFirstSpan(500)).isNotNull();
        Assertions.assertThat(reporter.getSpans()).hasSize(1);
    }

    @Test
    public void testSpanWithIllegalProtocol() throws Exception {
        reporter.disableCheckServiceTarget();
        reporter.disableCheckDestinationAddress();
        try {
            String illegalProtocol = "ottp";
            String url = getBaseUrl().replaceAll("http", illegalProtocol) + "/";
            assertThatThrownBy(() -> performGet(url)).cause().isInstanceOf(UnsupportedSchemeException.class);
        } finally {
            setUp();
            reporter.resetChecks();
        }
        SpanImpl firstSpan = reporter.getFirstSpan(500);
        assertThat(firstSpan).isNotNull();
        assertThat(firstSpan.getOutcome()).isEqualTo(Outcome.FAILURE);
        Assertions.assertThat(reporter.getSpans()).hasSize(1);
    }

}
