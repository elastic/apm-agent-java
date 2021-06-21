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
package co.elastic.apm.agent.httpclient;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.TextHeaderMapAccessor;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.context.Http;
import co.elastic.apm.agent.impl.context.web.ResultUtil;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.seeOther;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractHttpClientInstrumentationTest extends AbstractInstrumentationTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort(), false);

    @Before
    public final void setUpWiremock() {
        // ensure that HTTP spans outcome is not unknown
        wireMockRule.stubFor(any(urlEqualTo("/"))
            .willReturn(dummyResponse()
                .withStatus(200)));
        wireMockRule.stubFor(get(urlEqualTo("/error"))
            .willReturn(dummyResponse()
                .withStatus(515)));
        wireMockRule.stubFor(get(urlEqualTo("/redirect"))
            .willReturn(seeOther("/")));
        wireMockRule.stubFor(get(urlEqualTo("/circular-redirect"))
            .willReturn(seeOther("/circular-redirect")));

        startTestRootTransaction("parent of http span");
    }

    protected ResponseDefinitionBuilder dummyResponse() {
        return aResponse()
            // old spring 3.0 require content type
            .withHeader("Content-Type", "text/plain")
            .withBody("hello");
    }

    @After
    public final void after() {
        Transaction transaction = tracer.currentTransaction();
        assertThat(transaction).isNotNull();
        transaction.deactivate().end();
        assertThat(reporter.getTransactions()).hasSize(1);
    }

    protected boolean isIpv6Supported() {
        return true;
    }

    protected boolean isErrorOnCircularRedirectSupported(){
        return true;
    }

    @Test
    public void testHttpCall() throws Exception {
        String path = "/";
        performGetWithinTransaction(path);

        verifyHttpSpan(path);
    }

    @Test
    public void testHttpCallWithUserInfo() throws Exception {
        performGet("http://user:passwd@localhost:" + wireMockRule.port() + "/");
        verifyHttpSpan("/");
    }

    @Test
    public void testHttpCallWithIpv4() throws Exception {
        performGet("http://127.0.0.1:" + wireMockRule.port() + "/");
        verifyHttpSpan("127.0.0.1", "/");
    }

    @Test
    public void testHttpCallWithIpv6() throws Exception {
        if (!isIpv6Supported()) {
            return;
        }
        performGet(String.format("http://[::1]:%d/", wireMockRule.port()));
        verifyHttpSpan("[::1]", "/");
    }

    protected Span verifyHttpSpan(String path) {
        return verifyHttpSpan("localhost", path);
    }

    protected Span verifyHttpSpan(String host, String path, int status) {
        return verifyHttpSpan(host, path, status, true);
    }

    protected Span verifyHttpSpan(String host, String path, int status, boolean requestExecuted) {
        assertThat(reporter.getFirstSpan(500)).isNotNull();
        assertThat(reporter.getSpans()).hasSize(1);
        Span span = reporter.getSpans().get(0);

        int port = wireMockRule.port();

        String baseUrl = String.format("http://%s:%d", host, port);

        Http httpContext = span.getContext().getHttp();

        assertThat(span.getNameAsString()).isEqualTo(String.format("%s %s", httpContext.getMethod(), host));
        assertThat(httpContext.getUrl().toString()).isEqualTo(baseUrl + path);
        assertThat(httpContext.getStatusCode()).isEqualTo(status);

        if (requestExecuted) {
            assertThat(span.getOutcome()).isEqualTo(ResultUtil.getOutcomeByHttpClientStatus(status));
        } else {
            assertThat(span.getOutcome()).isEqualTo(Outcome.FAILURE);
        }

        assertThat(span.getType()).isEqualTo("external");
        assertThat(span.getSubtype()).isEqualTo("http");
        assertThat(span.getAction()).isNull();

        Destination destination = span.getContext().getDestination();
        int addressStartIndex = (host.startsWith("[")) ? 1 : 0;
        int addressEndIndex = (host.endsWith("]")) ? host.length() - 1 : host.length();
        assertThat(destination.getAddress().toString()).isEqualTo(host.substring(addressStartIndex, addressEndIndex));
        assertThat(destination.getPort()).isEqualTo(port);
        assertThat(destination.getService().getName().toString()).isEqualTo(baseUrl);
        assertThat(destination.getService().getResource().toString()).isEqualTo("%s:%d", host, port);
        assertThat(destination.getService().getType()).isEqualTo("external");

        if (requestExecuted) {
            verifyTraceContextHeaders(span, path);
        }

        return span;
    }

    protected Span verifyHttpSpan(String host, String path) {
        return verifyHttpSpan(host, path, 200);
    }

    private void verifyTraceContextHeaders(Span span, String path) {
        Map<String, String> headerMap = new HashMap<>();
        span.propagateTraceContext(headerMap, TextHeaderMapAccessor.INSTANCE);
        assertThat(headerMap).isNotEmpty();
        List<LoggedRequest> loggedRequests = wireMockRule.findAll(anyRequestedFor(urlPathEqualTo(path)));
        assertThat(loggedRequests).isNotEmpty();
        loggedRequests.forEach(request -> {
            assertThat(TraceContext.containsTraceContextTextHeaders(request, new HeaderAccessor())).isTrue();
            headerMap.forEach((key, value) -> assertThat(request.getHeader(key)).isEqualTo(value));
        });
    }

    @Test
    public void testNonExistingHttpCall() {
        String path = "/non-existing";
        performGetWithinTransaction(path);

        verifyHttpSpan("localhost", path, 404);
    }

    @Test
    public void testErrorHttpCall() {
        String path = "/error";
        performGetWithinTransaction(path);

        verifyHttpSpan("localhost", path, 515);
    }

    @Test
    public void testHttpCallRedirect() {
        String path = "/redirect";
        performGetWithinTransaction(path);

        Span span = verifyHttpSpan(path);

        verifyTraceContextHeaders(span, "/redirect");
        verifyTraceContextHeaders(span, "/");
    }

    @Test
    public void testHttpCallCircularRedirect() {
        if (!isErrorOnCircularRedirectSupported()) {
            return;
        }

        String path = "/circular-redirect";
        performGetWithinTransaction(path);

        Span span = reporter.getFirstSpan(500);
        assertThat(span).isNotNull();

        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getErrors()).hasSize(1);
        assertThat(reporter.getFirstError().getException()).isNotNull();
        assertThat(reporter.getFirstError().getException().getClass()).isNotNull();
        assertThat(span.getOutcome()).isEqualTo(Outcome.FAILURE);

        verifyTraceContextHeaders(span, "/circular-redirect");
    }

    protected String getBaseUrl() {
        return "http://localhost:" + wireMockRule.port();
    }

    protected void performGetWithinTransaction(String path) {
        try {
            performGet(getBaseUrl() + path);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    protected abstract void performGet(String path) throws Exception;

    private static class HeaderAccessor implements TextHeaderGetter<LoggedRequest> {
        @Nullable
        @Override
        public String getFirstHeader(String headerName, LoggedRequest loggedRequest) {
            return loggedRequest.getHeader(headerName);
        }

        @Override
        public <S> void forEach(String headerName, LoggedRequest loggedRequest, S state, HeaderConsumer<String, S> consumer) {
            HttpHeaders headers = loggedRequest.getHeaders();
            if (headers != null) {
                HttpHeader header = headers.getHeader(headerName);
                if (header != null) {
                    List<String> values = header.values();
                    for (int i = 0, size = values.size(); i < size; i++) {
                        consumer.accept(values.get(i), state);
                    }
                }
            }
        }
    }
}
