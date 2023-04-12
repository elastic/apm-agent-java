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
import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.impl.context.Db;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.impl.transaction.TransactionTest;
import org.apache.http.HttpHost;
import org.apache.http.HttpVersion;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.message.BasicStatusLine;
import org.assertj.core.api.Assertions;
import org.elasticsearch.client.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static co.elastic.apm.agent.esrestclient.ElasticsearchRestClientInstrumentationHelper.ELASTICSEARCH;
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


    @Test
    public void testCaptureBodyUrls() throws Exception {
        testCaptureBodyUrls(false);
        testCaptureBodyUrls(true);
    }

    public void testCaptureBodyUrls(boolean captureEverything) throws Exception {
        if (captureEverything) {
            doReturn(List.of(WildcardMatcher.valueOf("*")))
                .when(config.getConfig(ElasticsearchConfiguration.class))
                .getCaptureBodyUrls();
            assertThat(config.getConfig(ElasticsearchConfiguration.class).getCaptureBodyUrls()).hasSize(1);
        } else {
            assertThat(config.getConfig(ElasticsearchConfiguration.class).getCaptureBodyUrls()).hasSizeGreaterThan(5);
        }

        Span span = (Span) helper.createClientSpan("GET", "/_test",
            new ByteArrayEntity(new byte[0]));
        assertThat(span).isNotNull();
        assertThat(tracer.getActive()).isEqualTo(span);

        Response response = mockResponse(Map.of());
        helper.finishClientSpan(response, span, null);
        span.deactivate();

        assertThat(tracer.getActive()).isEqualTo(transaction);
        assertThat(span).hasType("db").hasSubType("elasticsearch");
        assertThat(span.getContext().getServiceTarget())
            .hasType("elasticsearch")
            .hasNoName();

        Db db = span.getContext().getDb();
        Assertions.assertThat(db.getType()).isEqualTo(ELASTICSEARCH);
        if (captureEverything) {
            assertThat((CharSequence) db.getStatementBuffer()).isNotNull();
        } else {
            assertThat((CharSequence) db.getStatementBuffer()).isNull();
        }
    }
}
