/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.httpclient;

import co.elastic.apm.AbstractInstrumentationTest;
import co.elastic.apm.impl.Scope;
import co.elastic.apm.impl.transaction.TraceContext;
import co.elastic.apm.impl.transaction.Transaction;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.assertj.core.api.Java6Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
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
    public WireMockRule wireMockRule = new WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort());

    @Before
    public final void setUpWiremock() {
        wireMockRule.stubFor(get(urlEqualTo("/"))
            .willReturn(aResponse()
                .withStatus(200)));
        wireMockRule.stubFor(get(urlEqualTo("/redirect"))
            .willReturn(seeOther("/")));
        wireMockRule.stubFor(get(urlEqualTo("/circular-redirect"))
            .willReturn(seeOther("/circular-redirect")));
    }

    @Test
    public void testHttpCall() {
        performGetWithinTransaction("/");

        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(1);

        final String traceParentHeader = reporter.getFirstSpan().getTraceContext().getOutgoingTraceParentHeader().toString();
        verify(getRequestedFor(urlPathEqualTo("/"))
            .withHeader(TraceContext.TRACE_PARENT_HEADER, equalTo(traceParentHeader)));
    }

    @Test
    public void testHttpCallRedirect() {
        performGetWithinTransaction("/redirect");

        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(1);

        final String traceParentHeader = reporter.getFirstSpan().getTraceContext().getOutgoingTraceParentHeader().toString();
        verify(getRequestedFor(urlPathEqualTo("/redirect"))
            .withHeader(TraceContext.TRACE_PARENT_HEADER, equalTo(traceParentHeader)));
        verify(getRequestedFor(urlPathEqualTo("/"))
            .withHeader(TraceContext.TRACE_PARENT_HEADER, equalTo(traceParentHeader)));
    }

    @Test
    public void testHttpCallCircularRedirect() {
        performGetWithinTransaction("/circular-redirect");

        Java6Assertions.assertThat(reporter.getTransactions()).hasSize(1);
        Java6Assertions.assertThat(reporter.getSpans()).hasSize(1);
        Java6Assertions.assertThat(reporter.getErrors()).hasSize(1);
        Java6Assertions.assertThat(reporter.getFirstError().getException()).isNotNull();
        Java6Assertions.assertThat(reporter.getFirstError().getException().getClass()).isNotNull();

        final String traceParentHeader = reporter.getFirstSpan().getTraceContext().getOutgoingTraceParentHeader().toString();
        verify(getRequestedFor(urlPathEqualTo("/circular-redirect"))
            .withHeader(TraceContext.TRACE_PARENT_HEADER, equalTo(traceParentHeader)));
    }

    private String getBaseUrl() {
        return "http://localhost:" + wireMockRule.port();
    }

    protected void performGetWithinTransaction(String path) {
        final Transaction transaction = tracer.startTransaction();
        try (Scope scope = transaction.withType("request").activateInScope()) {
            try {
                performGet(getBaseUrl() + path);
            } catch (Exception ignore) {
            }
        }
        transaction.end();
    }

    protected abstract void performGet(String path) throws Exception;
}
