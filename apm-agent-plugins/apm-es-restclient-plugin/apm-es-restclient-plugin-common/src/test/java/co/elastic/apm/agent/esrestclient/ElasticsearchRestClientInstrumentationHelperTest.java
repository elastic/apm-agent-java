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
package co.elastic.apm.agent.esrestclient;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.impl.transaction.TransactionTest;
import org.apache.http.HttpHost;
import org.apache.http.HttpVersion;
import org.apache.http.message.BasicStatusLine;
import org.elasticsearch.client.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class ElasticsearchRestClientInstrumentationHelperTest extends AbstractInstrumentationTest {

    private final ElasticsearchRestClientInstrumentationHelper helper = ElasticsearchRestClientInstrumentationHelper.get();

    private Transaction transaction;

    @BeforeEach
    void before() {
        transaction = startTestRootTransaction();
    }

    @AfterEach
    void after(){
        transaction.deactivate().end();
    }

    @Test
    void testCreateSpan() {
        Span span = (Span) helper.createClientSpan("GET", "/_test", null);
        assertThat(span).isNotNull();

        assertThat(tracer.getActive()).isEqualTo(span);


        Response response = mockResponse(Map.of());

        helper.finishClientSpan(response, span, null);
        span.deactivate();

        assertThat(tracer.getActive()).isEqualTo(transaction);

        assertThat(span)
            .hasType("db")
            .hasSubType("elasticsearch");

        assertThat(span.getContext().getServiceTarget())
            .hasType("elasticsearch")
            .hasNoName();

    }

    @Test
    void testCreateSpanWithClusterName() {
        Span span = (Span) helper.createClientSpan("GET", "/_test", null);
        assertThat(span).isNotNull();

        assertThat(tracer.getActive()).isEqualTo(span);

        Response response = mockResponse(Map.of("x-found-handling-cluster", "my-cluster-name"));

        helper.finishClientSpan(response, span, null);
        span.deactivate();

        assertThat(tracer.getActive()).isEqualTo(transaction);

        assertThat(span)
            .hasType("db")
            .hasSubType("elasticsearch");

        assertThat(span.getContext().getServiceTarget())
            .hasType("elasticsearch")
            .hasName("my-cluster-name");
    }

    private static Response mockResponse(Map<String,String> headers) {
        Response response = mock(Response.class);
        HttpHost httpHost = new HttpHost("host", 9200);
        BasicStatusLine statusLine = new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK");

        // only mocking the way we actually retrieve headers
        doAnswer(invocation -> {
            String headerName = invocation.getArgument(0);
            return headers.get(headerName);
        }).when(response).getHeader(any());

        doReturn(httpHost).when(response).getHost();
        doReturn(statusLine).when(response).getStatusLine();
        return response;
    }

    @Test
    void testNonSampledSpan() {
        TransactionTest.setRecorded(false, transaction);
        Span esSpan = (Span) helper.createClientSpan("SEARCH", "/test", null);
        assertThat(esSpan).isNotNull();
        try {
            assertThat(esSpan.isSampled()).isFalse();
            assertThat(esSpan.getContext().getServiceTarget().getType())
                .describedAs("service target field should not be null in non-sampled spans because it is used for dropped spans stats")
                .isNotNull();
        } finally {
            esSpan.deactivate().end();
        }
    }
}
