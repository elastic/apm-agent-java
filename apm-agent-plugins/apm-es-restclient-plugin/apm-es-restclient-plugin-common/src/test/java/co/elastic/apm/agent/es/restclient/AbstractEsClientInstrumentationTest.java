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
package co.elastic.apm.agent.es.restclient;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.context.Db;
import co.elastic.apm.agent.impl.context.Http;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.After;
import org.junit.Before;
import org.junit.runners.Parameterized;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static co.elastic.apm.agent.es.restclient.ElasticsearchRestClientInstrumentationHelperImpl.ELASTICSEARCH;
import static co.elastic.apm.agent.es.restclient.ElasticsearchRestClientInstrumentationHelperImpl.QUERY_REGEXP;
import static co.elastic.apm.agent.es.restclient.ElasticsearchRestClientInstrumentationHelperImpl.SPAN_ACTION;
import static co.elastic.apm.agent.es.restclient.ElasticsearchRestClientInstrumentationHelperImpl.SPAN_TYPE;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractEsClientInstrumentationTest extends AbstractInstrumentationTest {

    @SuppressWarnings("NullableProblems")
    protected static ElasticsearchContainer container;

    protected static final String INDEX = "my-index";
    protected static final String SECOND_INDEX = "my-second-index";
    protected static final String DOC_ID = "38uhjds8s4g";
    protected static final String DOC_TYPE = "_doc";
    protected static final String FOO = "foo";
    protected static final String BAR = "bar";
    protected static final String BAZ = "baz";
    protected static final String SEARCH_QUERY_PATH_SUFFIX = "_search";
    protected static final String MSEARCH_QUERY_PATH_SUFFIX = "_msearch";
    protected static final String COUNT_QUERY_PATH_SUFFIX = "_count";

    protected boolean async;

    @Parameterized.Parameters(name = "Async={0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{{Boolean.FALSE}, {Boolean.TRUE}});
    }

    @Before
    public void startTransaction() {
        Transaction transaction = tracer.startRootTransaction(null).activate();
        transaction.withName("ES Transaction");
        transaction.withType("request");
        transaction.withResultIfUnset("success");
    }

    @After
    public void endTransaction() {
        try {
            Transaction currentTransaction = tracer.currentTransaction();
            if (currentTransaction != null) {
                currentTransaction.deactivate().end();
            }
        } finally {
            reporter.reset();
        }
    }

    public void assertThatErrorsExistWhenDeleteNonExistingIndex() throws IOException {

        List<ErrorCapture> errorCaptures = reporter.getErrors();
        assertThat(errorCaptures).hasSize(1);
        ErrorCapture errorCapture = errorCaptures.get(0);
        assertThat(errorCapture.getException()).isNotNull();
    }

    protected void validateSpanContentWithoutContext(Span span, String expectedName, int statusCode, String method) {
        assertThat(span.getType()).isEqualTo(SPAN_TYPE);
        assertThat(span.getSubtype()).isEqualTo(ELASTICSEARCH);
        assertThat(span.getAction()).isEqualTo(SPAN_ACTION);
        assertThat(span.getNameAsString()).isEqualTo(expectedName);
        assertThat(span.getContext().getDb().getType()).isEqualTo(ELASTICSEARCH);
        if (!expectedName.contains(SEARCH_QUERY_PATH_SUFFIX) && !expectedName.contains(MSEARCH_QUERY_PATH_SUFFIX) && !expectedName.contains(COUNT_QUERY_PATH_SUFFIX)) {
            assertThat((CharSequence) (span.getContext().getDb().getStatementBuffer())).isNull();
        }
    }

    protected void validateDbContextContent(Span span, String statement) {
        Db db = span.getContext().getDb();
        assertThat(db.getType()).isEqualTo(ELASTICSEARCH);
        assertThat((CharSequence) db.getStatementBuffer()).isNotNull();
        assertThat(db.getStatementBuffer().toString()).isEqualTo(statement);
    }


    protected void validateSpanContent(Span span, String expectedName, int statusCode, String method) {
        validateSpanContentWithoutContext(span, expectedName, statusCode, method);
        validateHttpContextContent(span.getContext().getHttp(), statusCode, method);
        validateDestinationContextContent(span.getContext().getDestination());
    }

    private void validateDestinationContextContent(Destination destination) {
        assertThat(destination).isNotNull();
        assertThat(destination.getAddress().toString()).isEqualTo(container.getContainerIpAddress());
        assertThat(destination.getPort()).isEqualTo(container.getMappedPort(9200));
        assertThat(destination.getService().getName().toString()).isEqualTo(ELASTICSEARCH);
        assertThat(destination.getService().getResource().toString()).isEqualTo(ELASTICSEARCH);
        assertThat(destination.getService().getType()).isEqualTo(SPAN_TYPE);
    }

    private void validateHttpContextContent(Http http, int statusCode, String method) {
        assertThat(http).isNotNull();
        assertThat(http.getMethod()).isEqualTo(method);
        assertThat(http.getStatusCode()).isEqualTo(statusCode);
        assertThat(http.getUrl()).isEqualTo("http://" + container.getHttpHostAddress());
    }

    protected void validateSpanContentAfterIndexCreateRequest() {

        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        validateSpanContent(spans.get(0), String.format("Elasticsearch: PUT /%s", SECOND_INDEX), 200, "PUT");
    }

    protected void validateSpanContentAfterIndexDeleteRequest() {

        List<Span>spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        validateSpanContent(spans.get(0), String.format("Elasticsearch: DELETE /%s", SECOND_INDEX), 200, "DELETE");
    }

    protected void validateSpanContentAfterBulkRequest() {

        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).getNameAsString()).isEqualTo("Elasticsearch: POST /_bulk");
    }

}
