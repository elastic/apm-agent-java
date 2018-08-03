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
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Java6Assertions.assertThat;

public class ApacheHttpClientInstrumentationTest extends AbstractInstrumentationTest {
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());
    private HttpClient client;

    @Before
    public void setUp() throws Exception {
        client = HttpClients.createDefault();
        wireMockRule.stubFor(get(urlEqualTo("/"))
            .willReturn(aResponse()
                .withStatus(200)));
        wireMockRule.stubFor(get(urlEqualTo("/redirect"))
            .willReturn(aResponse()
                .withStatus(303)
                .withHeader("Location", "/")));
    }

    @Test
    public void testHttpCall() throws Exception {
        final Transaction transaction = tracer.startTransaction();
        try (Scope scope = transaction.withType("request").activateInScope()) {
            HttpGet httpGet = new HttpGet("http://localhost:" + wireMockRule.port());
            assertThat(client.execute(httpGet).getStatusLine().getStatusCode()).isEqualTo(200);
        }
        transaction.end();

        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(1);

        final String traceParentHeader = reporter.getFirstSpan().getTraceContext().getOutgoingTraceParentHeader().toString();
        verify(getRequestedFor(urlPathEqualTo("/"))
            .withHeader(TraceContext.TRACE_PARENT_HEADER, equalTo(traceParentHeader)));
    }

    @Test
    public void testHttpCallRedirect() throws Exception {
        final Transaction transaction = tracer.startTransaction();
        try (Scope scope = transaction.withType("request").activateInScope()) {
            HttpGet httpGet = new HttpGet("http://localhost:" + wireMockRule.port() + "/redirect");
            assertThat(client.execute(httpGet).getStatusLine().getStatusCode()).isEqualTo(200);
        }
        transaction.end();

        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(1);

        final String traceParentHeader = reporter.getFirstSpan().getTraceContext().getOutgoingTraceParentHeader().toString();
        verify(getRequestedFor(urlPathEqualTo("/redirect"))
            .withHeader(TraceContext.TRACE_PARENT_HEADER, equalTo(traceParentHeader)));
        verify(getRequestedFor(urlPathEqualTo("/"))
            .withHeader(TraceContext.TRACE_PARENT_HEADER, equalTo(traceParentHeader)));
    }

}
