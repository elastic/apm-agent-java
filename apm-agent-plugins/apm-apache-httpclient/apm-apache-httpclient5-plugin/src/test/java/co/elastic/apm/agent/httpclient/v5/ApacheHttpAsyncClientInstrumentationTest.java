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
package co.elastic.apm.agent.httpclient.v5;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.tracer.Outcome;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.net.URIAuthority;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

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
    protected void performGet(String path) throws Exception {
        final CompletableFuture<SimpleHttpResponse> responseFuture = new CompletableFuture<>();
        SimpleHttpRequest req = SimpleRequestBuilder.get().setPath(path)
            .build();
        RequestConfig requestConfig = RequestConfig.custom()
            .setCircularRedirectsAllowed(true)
            .build();
        HttpClientContext httpClientContext = HttpClientContext.create();
        httpClientContext.setRequestConfig(requestConfig);
        client.execute(req, httpClientContext, new FutureCallback<SimpleHttpResponse>() {
            @Override
            public void completed(SimpleHttpResponse simpleHttpResponse) {
                responseFuture.complete(simpleHttpResponse);
            }

            @Override
            public void failed(Exception e) {
                responseFuture.completeExceptionally(e);
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
        SimpleHttpRequest req = SimpleRequestBuilder.get().setPath(path)
            .addHeader("Content-Type", contentTypeHeader)
            .setBody(data, ContentType.parse(contentTypeHeader))
            .build();
        HttpClientContext httpClientContext = HttpClientContext.create();
        client.execute(req, httpClientContext, new FutureCallback<SimpleHttpResponse>() {
            @Override
            public void completed(SimpleHttpResponse simpleHttpResponse) {
                responseFuture.complete(simpleHttpResponse);
            }

            @Override
            public void failed(Exception e) {
                responseFuture.completeExceptionally(e);
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
            assertThatThrownBy(() -> performGet(getBaseUrl() + "/")).cause().isInstanceOf(IllegalStateException.class);
        } finally {
            //Reset state for other tests
            setUp();
            reporter.resetChecks();
        }
        assertThat(reporter.getFirstSpan(500)).isNotNull();
        assertThat(reporter.getSpans()).hasSize(1);
    }

    @Test
    public void testSpanFinishWithIllegalProtocol() throws Exception {
        reporter.disableCheckServiceTarget();
        reporter.disableCheckDestinationAddress();
        String url = getBaseUrl().replaceAll("http", "ottp") + "/";
        performGet(url);

        SpanImpl firstSpan = reporter.getFirstSpan(500);
        assertThat(firstSpan).isNotNull();
        assertThat(firstSpan.getOutcome()).isEqualTo(Outcome.FAILURE);
        assertThat(firstSpan.getNameAsString()).isEqualTo("GET localhost");
        assertThat(reporter.getSpans()).hasSize(1);
    }

    @Test
    public void testSpanFinishWithIllegalUrl() throws Exception {
        reporter.disableCheckServiceTarget();
        reporter.disableCheckDestinationAddress();
        String url = getBaseUrl().replaceAll("http:/", "") + "/";

        try {
            assertThatThrownBy(() -> performGet(url)).cause().isInstanceOf(ProtocolException.class);
        } finally {
            //Reset state for other tests
            setUp();
            reporter.resetChecks();
        }

        SpanImpl firstSpan = reporter.getFirstSpan(500);
        assertThat(firstSpan).isNotNull();
        assertThat(firstSpan.getOutcome()).isEqualTo(Outcome.FAILURE);
        assertThat(firstSpan.getNameAsString()).isEqualTo("GET ");
        assertThat(reporter.getSpans()).hasSize(1);
    }

    /**
     * Difference between sync and async requests is that
     * In async requests you need {@link SimpleRequestBuilder#setAuthority(URIAuthority)} explicitly
     * And in this case exception will be thrown from {@link org.apache.hc.client5.http.impl.async.AsyncProtocolExec#execute}
     * <p>
     * SimpleHttpRequest req = SimpleRequestBuilder.get().setPath(path)
     * .setScheme("http")
     * .setAuthority(new URIAuthority(uri.getUserInfo(), uri.getHost(), uri.getPort()))
     * .build();
     */
    @Override
    public boolean isTestHttpCallWithUserInfoEnabled() {
        return true;
    }
}
