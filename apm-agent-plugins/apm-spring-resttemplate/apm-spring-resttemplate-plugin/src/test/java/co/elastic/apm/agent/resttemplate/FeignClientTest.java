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
package co.elastic.apm.agent.resttemplate;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.context.HttpImpl;
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import feign.Feign;
import feign.hc5.ApacheHttp5Client;
import feign.jaxrs.JAXRSContract;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

public class FeignClientTest extends AbstractInstrumentationTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort(), false);

    private TransactionImpl rootTransaction;

    private String baseUrl = null;

    @Before
    public void before() {
        baseUrl = String.format("http://127.0.0.1:%d/", wireMockRule.port());

        wireMockRule.stubFor(any(urlEqualTo("/"))
            .willReturn(okJson("{}")
                .withStatus(200)));

        rootTransaction = tracer.startRootTransaction(getClass().getClassLoader());
        rootTransaction.withName("parent of http span")
            .withType("request")
            .activate();
    }

    @After
    public void after() {
        rootTransaction.deactivate().end();
        reporter.awaitTransactionCount(1);
    }

    @Test
    public void testThatSpanCreated() {
        final JaxRsTestInterface testInterface = buildTestInterface();

        testInterface.getRoot();

        reporter.awaitSpanCount(1);

        SpanImpl span = reporter.getFirstSpan();
        HttpImpl http = span.getContext().getHttp();

        assertThat(span.getNameAsString()).isEqualTo("GET 127.0.0.1");
        assertThat(http.getMethod()).isEqualTo("GET");
        assertThat(http.getUrl().toString()).isNotNull().isEqualTo(baseUrl);
    }

    private JaxRsTestInterface buildTestInterface() {
        return Feign.builder()
            .contract(new JAXRSContract())
            .client(new ApacheHttp5Client(HttpClientBuilder.create().build()))
            .target(JaxRsTestInterface.class, baseUrl);
    }


    @Path("/")
    public interface JaxRsTestInterface {

        @GET
        @Path("/")
        String getRoot();
    }
}
