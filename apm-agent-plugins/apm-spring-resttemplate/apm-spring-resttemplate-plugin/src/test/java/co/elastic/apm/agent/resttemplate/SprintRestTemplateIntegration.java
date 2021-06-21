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
import co.elastic.apm.agent.impl.context.Http;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.web.client.RestTemplate;

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

public class SprintRestTemplateIntegration extends AbstractInstrumentationTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort(), false);

    private final RestTemplate restTemplate;

    private Transaction rootTransaction;

    private final boolean expectSpan;

    public SprintRestTemplateIntegration() {
        this(true);
    }

    protected SprintRestTemplateIntegration(boolean expectSpan){
        this.restTemplate = new RestTemplate();
        this.expectSpan = expectSpan;
    }

    @Before
    public void before() {
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
    public void getRoot() {
        String url = String.format("http://127.0.0.1:%d/", wireMockRule.port());
        restTemplate.getForObject(url, String.class);

        if (!expectSpan) {
            reporter.assertNoSpan(1000);
        } else {
            reporter.awaitSpanCount(1);

            Span span = reporter.getFirstSpan();
            Http http = span.getContext().getHttp();
            assertThat(http.getMethod()).isEqualTo("GET");
            assertThat(http.getUrl().toString()).isEqualTo(url);
        }
    }
}
