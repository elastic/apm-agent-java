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
package co.elastic.apm.es.restclient.v5_6;

import co.elastic.apm.AbstractInstrumentationTest;
import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.transaction.Db;
import co.elastic.apm.impl.transaction.Http;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.transaction.Transaction;
import org.apache.http.HttpHost;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static co.elastic.apm.es.restclient.v5_6.ElasticsearchRestClientInstrumentation.DB_CONTEXT_TYPE;
import static co.elastic.apm.es.restclient.v5_6.ElasticsearchRestClientInstrumentation.SEARCH_QUERY_PATH_SUFFIX;
import static co.elastic.apm.es.restclient.v5_6.ElasticsearchRestClientInstrumentation.SPAN_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public class ElasticsearchRestClientInstrumentationIT extends AbstractInstrumentationTest {
    private static final String USER_NAME = "elastic-user";
    private static final String PASSWORD = "elastic-pass";

    @SuppressWarnings("NullableProblems")
    private static ElasticsearchContainer container;
    @SuppressWarnings("NullableProblems")
    private static RestClient lowLevelClient;
    private static RestHighLevelClient client;

    private static final String INDEX = "my-index";
    private static final String SECOND_INDEX = "my-second-index";
    private static final String DOC_ID = "38uhjds8s4g";
    private static final String DOC_TYPE = "_doc";
    private static final String FOO = "foo";
    private static final String BAR = "bar";
    private static final String BAZ = "baz";

    @BeforeClass
    public static void startElasticsearchContainerAndClient() throws IOException {
        // Start the container
        container = new ElasticsearchContainer();
        container.start();

        // Create the client
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(USER_NAME, PASSWORD));

        RestClientBuilder builder =  RestClient.builder(HttpHost.create(container.getHttpHostAddress()))
            .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        lowLevelClient = builder.build();
        client = new RestHighLevelClient(lowLevelClient);

        lowLevelClient.performRequest("PUT", "/" + INDEX);
        reporter.reset();
    }

    @AfterClass
    public static void stopElasticsearchContainerAndClient() throws IOException {
        lowLevelClient.performRequest("DELETE", "/" + INDEX);
        container.stop();
        lowLevelClient.close();
    }

    @Before
    public void startTransaction() {
        Transaction transaction = tracer.startTransaction().activate();
        transaction.setName("ES Transaction");
        transaction.withType("request");
        transaction.withResult("success");
    }

    @After
    public void endTransaction() {
        Transaction currentTransaction = tracer.currentTransaction();
        if (currentTransaction != null) {
            currentTransaction.deactivate().end();
        }
        reporter.reset();
    }

    @Test
    public void testTryToDeleteNonExistingIndex() throws IOException {
        ResponseException re = null;
        try {
            lowLevelClient.performRequest("POST", "/non-existing/1/_mapping");
        } catch (ResponseException e) {
            re = e;
        }
        assertThat(re).isNotNull();
        assertThat(re.getResponse().getStatusLine().getStatusCode()).isEqualTo(400);

        System.out.println(reporter.generateErrorPayloadJson());

        List<ErrorCapture> errorCaptures = reporter.getErrors();
        assertThat(errorCaptures).hasSize(1);
        ErrorCapture errorCapture = errorCaptures.get(0);
        assertThat(errorCapture.getException()).isNotNull();
/*
        Map<String, String> tags = errorCapture.getContext().getTags();
        assertThat(tags).containsKey(QUERY_STATUS_CODE_KEY);
        assertThat(tags.get(QUERY_STATUS_CODE_KEY)).isEqualTo("404");
        assertThat(tags).containsKey(ERROR_REASON_KEY);
        assertThat(tags).containsKey(ELASTICSEARCH_NODE_URL_KEY);
        assertThat(tags.get(ELASTICSEARCH_NODE_URL_KEY)).isEqualTo(container.getHost().toURI().toString());
*/
    }

    private void validateSpanContent(Span span, String expectedName, int statusCode, String method) {
        assertThat(span.getType()).isEqualTo(SPAN_TYPE);
        assertThat(span.getName().toString()).isEqualTo(expectedName);
        validateHttpContextContent(span.getContext().getHttp(), statusCode, method);

        assertThat(span.getContext().getDb().getType()).isEqualTo(DB_CONTEXT_TYPE);

        if (!expectedName.contains(SEARCH_QUERY_PATH_SUFFIX)) {
            assertThat(span.getContext().getDb().getStatement()).isNull();
        }
    }

    private void validateHttpContextContent(Http http, int statusCode, String method) {
        assertThat(http).isNotNull();
        assertThat(http.getMethod()).isEqualTo(method);
        assertThat(http.getStatusCode()).isEqualTo(statusCode);
        assertThat(http.getUrl()).isEqualTo("http://" + container.getHttpHostAddress());
    }

    private void validateDbContextContent(Span span, String statement) {
        Db db = span.getContext().getDb();
        assertThat(db.getType()).isEqualTo(DB_CONTEXT_TYPE);
        assertThat((Object) db.getStatementBuffer()).isNotNull();
        assertThat(db.getStatementBuffer().toString()).isEqualTo(statement);
    }

    @Test
    public void testCreateAndDeleteIndex() throws IOException {
        // Create an Index
        lowLevelClient.performRequest("PUT", "/" + SECOND_INDEX);

        System.out.println(reporter.generateTransactionPayloadJson());

        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        validateSpanContent(spans.get(0), String.format("Elasticsearch: PUT /%s", SECOND_INDEX), 200, "PUT");

        // Delete the index
        reporter.reset();

        lowLevelClient.performRequest("DELETE", "/" + SECOND_INDEX);

        System.out.println(reporter.generateTransactionPayloadJson());

        spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        validateSpanContent(spans.get(0), String.format("Elasticsearch: DELETE /%s", SECOND_INDEX), 200, "DELETE");
    }

    @Test
    public void testDocumentScenario() throws IOException {
        // Index a document
        IndexResponse ir = client.index(new IndexRequest(INDEX, DOC_TYPE, DOC_ID).source(
            jsonBuilder()
                .startObject()
                .field(FOO, BAR)
                .endObject()
        ).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE));
        assertThat(ir.status().getStatus()).isEqualTo(200);

        System.out.println(reporter.generateTransactionPayloadJson());

        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        validateSpanContent(spans.get(0), String.format("Elasticsearch: PUT /%s/%s/%s", INDEX, DOC_TYPE, DOC_ID), 201, "PUT");

        // Search the index
        reporter.reset();

        SearchRequest searchRequest = new SearchRequest(INDEX);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.termQuery(FOO, BAR));
        sourceBuilder.from(0);
        sourceBuilder.size(5);
        searchRequest.source(sourceBuilder);
        SearchResponse sr = client.search(searchRequest);
        assertThat(sr.getHits().totalHits).isEqualTo(1L);
        assertThat(sr.getHits().getAt(0).getSourceAsMap().get(FOO)).isEqualTo(BAR);

        System.out.println(reporter.generateTransactionPayloadJson());

        spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        Span searchSpan = spans.get(0);
        validateSpanContent(searchSpan, String.format("Elasticsearch: GET /%s/_search", INDEX), 200, "GET");
        validateDbContextContent(searchSpan, "{\"from\":0,\"size\":5,\"query\":{\"term\":{\"foo\":{\"value\":\"bar\",\"boost\":1.0}}}}");

        // Now update and re-search
        reporter.reset();

        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put(FOO, BAZ);
        UpdateResponse ur = client.update(new UpdateRequest(INDEX, DOC_TYPE, DOC_ID).doc(jsonMap)
            .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE));
        assertThat(ur.status().getStatus()).isEqualTo(200);
        sr = client.search(new SearchRequest(INDEX));
        assertThat(sr.getHits().getAt(0).getSourceAsMap().get(FOO)).isEqualTo(BAZ);

        System.out.println(reporter.generateTransactionPayloadJson());

        spans = reporter.getSpans();
        assertThat(spans).hasSize(2);
        boolean updateSpanFound = false;
        for(Span span: spans) {
            if(span.getName().toString().contains("_update")) {
                updateSpanFound = true;
                break;
            }
        }
        assertThat(updateSpanFound).isTrue();

        // Finally - delete the document
        reporter.reset();
        DeleteResponse dr = client.delete(new DeleteRequest(INDEX, DOC_TYPE, DOC_ID));
        validateSpanContent(spans.get(0), String.format("Elasticsearch: DELETE /%s/%s/%s", INDEX, DOC_TYPE, DOC_ID), 200, "DELETE");

        System.out.println(reporter.generateTransactionPayloadJson());
    }

    @Test
    public void testScenarioAsBulkRequest() throws IOException {
        client.bulk(new BulkRequest()
            .add(new IndexRequest(INDEX, DOC_TYPE, "2").source(
                jsonBuilder()
                    .startObject()
                    .field(FOO, BAR)
                    .endObject()
            ))
            .add(new IndexRequest(INDEX, DOC_TYPE, "3").source(
                jsonBuilder()
                    .startObject()
                    .field(FOO, BAR)
                    .endObject()
            ))
        );

        System.out.println(reporter.generateTransactionPayloadJson());

        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).getName().toString()).isEqualTo("Elasticsearch: POST /_bulk");
    }
}
