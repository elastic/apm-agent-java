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
package co.elastic.apm.agent.httpclient;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.ContentPattern;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.seeOther;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractHttpClientInstrumentationTest extends AbstractInstrumentationTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort(), false);

    @Before
    public final void setUpWiremock() {
        wireMockRule.stubFor(any(urlEqualTo("/"))
            .willReturn(aResponse()
                // tests chunked encoded responses
                .withBodyFile("test.txt")
                .withStatus(200)));
        wireMockRule.stubFor(get(urlEqualTo("/error"))
            .willReturn(aResponse()
                .withStatus(515)));
        wireMockRule.stubFor(get(urlEqualTo("/redirect"))
            .willReturn(seeOther("/")));
        wireMockRule.stubFor(get(urlEqualTo("/circular-redirect"))
            .willReturn(seeOther("/circular-redirect")));
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, getClass().getClassLoader());
        transaction.withName("transaction").withType("request").activate();
    }

    @After
    public final void after() {
        tracer.currentTransaction().deactivate().end();
        assertThat(reporter.getTransactions()).hasSize(1);
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

    protected void verifyHttpSpan(String path) throws Exception {
        assertThat(reporter.getFirstSpan(500)).isNotNull();
        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getSpans().get(0).getContext().getHttp().getUrl()).isEqualTo(getBaseUrl() + path);
        assertThat(reporter.getSpans().get(0).getContext().getHttp().getStatusCode()).isEqualTo(200);
        assertThat(reporter.getSpans().get(0).getType()).isEqualTo("external");
        assertThat(reporter.getSpans().get(0).getSubtype()).isEqualTo("http");
        assertThat(reporter.getSpans().get(0).getAction()).isNull();

        final String traceParentHeader = reporter.getFirstSpan().getTraceContext().getOutgoingTraceParentHeader().toString();
        verify(anyRequestedFor(urlPathEqualTo(path))
            .withHeader(TraceContext.TRACE_PARENT_HEADER, equalTo(traceParentHeader)));
    }

    @Test
    public void testNonExistingHttpCall() throws Exception {
        String path = "/non-existing";
        performGetWithinTransaction(path);

        assertThat(reporter.getFirstSpan(500)).isNotNull();
        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getSpans().get(0).getContext().getHttp().getUrl()).isEqualTo(getBaseUrl() + path);
        assertThat(reporter.getSpans().get(0).getContext().getHttp().getStatusCode()).isEqualTo(404);
    }

    @Test
    public void testErrorHttpCall() throws Exception {
        String path = "/error";
        performGetWithinTransaction(path);

        assertThat(reporter.getFirstSpan(500)).isNotNull();
        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getSpans().get(0).getContext().getHttp().getUrl()).isEqualTo(getBaseUrl() + path);
        assertThat(reporter.getSpans().get(0).getContext().getHttp().getStatusCode()).isEqualTo(515);
    }

    @Test
    public void testHttpCallRedirect() throws Exception {
        String path = "/redirect";
        performGetWithinTransaction(path);

        assertThat(reporter.getFirstSpan(500)).isNotNull();
        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getFirstSpan().getContext().getHttp().getUrl()).isEqualTo(getBaseUrl() + path);
        assertThat(reporter.getFirstSpan().getContext().getHttp().getStatusCode()).isEqualTo(200);

        final String traceParentHeader = reporter.getFirstSpan().getTraceContext().getOutgoingTraceParentHeader().toString();
        verify(getRequestedFor(urlPathEqualTo("/redirect"))
            .withHeader(TraceContext.TRACE_PARENT_HEADER, equalTo(traceParentHeader)));
        verify(getRequestedFor(urlPathEqualTo("/"))
            .withHeader(TraceContext.TRACE_PARENT_HEADER, equalTo(traceParentHeader)));
    }

    @Test
    public void testHttpCallCircularRedirect() throws Exception {
        String path = "/circular-redirect";
        performGetWithinTransaction(path);

        assertThat(reporter.getFirstSpan(500)).isNotNull();
        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getErrors()).hasSize(1);
        assertThat(reporter.getFirstError().getException()).isNotNull();
        assertThat(reporter.getFirstError().getException().getClass()).isNotNull();
        assertThat(reporter.getSpans().get(0).getContext().getHttp().getUrl()).isEqualTo(getBaseUrl() + path);

        final String traceParentHeader = reporter.getFirstSpan().getTraceContext().getOutgoingTraceParentHeader().toString();
        verify(getRequestedFor(urlPathEqualTo("/circular-redirect"))
            .withHeader(TraceContext.TRACE_PARENT_HEADER, equalTo(traceParentHeader)));
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
}
