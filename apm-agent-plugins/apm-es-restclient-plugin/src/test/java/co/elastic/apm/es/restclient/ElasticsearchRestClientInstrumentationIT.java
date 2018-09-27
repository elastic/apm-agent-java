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
package co.elastic.apm.es.restclient;

import co.elastic.apm.AbstractInstrumentationTest;
import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.transaction.Transaction;
import fr.pilato.elasticsearch.containers.ElasticsearchContainer;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static co.elastic.apm.es.restclient.ElasticsearchRestClientInstrumentation.ELASTICSEARCH_NODE_KEY;
import static co.elastic.apm.es.restclient.ElasticsearchRestClientInstrumentation.ERROR_REASON_KEY;
import static co.elastic.apm.es.restclient.ElasticsearchRestClientInstrumentation.QUERY_STATUS_CODE_KEY;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.assertj.core.api.Assertions.assertThat;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ElasticsearchRestClientInstrumentationIT extends AbstractInstrumentationTest {
    private static final String USER_NAME = "elastic-user";
    private static final String PASSWORD = "elastic-pass";

    @SuppressWarnings("NullableProblems")
    private static ElasticsearchContainer container;
    @SuppressWarnings("NullableProblems")
    private static RestHighLevelClient client;

    private static final String INDEX = "my-index";
    private static final String DOC_ID = "1";
    private static final String DOC_TYPE = "_doc";
    private static final String FOO = "foo";
    private static final String BAR = "bar";
    private static final String BAZ = "baz";

    /**
     * This Integration testing relies on <a href="https://github.com/dadoonet/testcontainers-java-module-elasticsearch">this
     * ES testcontainer module</a> (will be replaced with an official version once merged into the
     * <a href="https://github.com/testcontainers/testcontainers-java">testcontainers repo</a>)
     */
    @BeforeClass
    public static void startElasticsearchContainerAndClient() {
        // Start the container
        container = new ElasticsearchContainer();
        container.start();

        // Create the client
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(USER_NAME, PASSWORD));

        RestClientBuilder builder =  RestClient.builder(container.getHost())
            .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        client = new RestHighLevelClient(builder);
    }

    @AfterClass
    public static void stopElasticsearchContainerAndClient() throws IOException {
        container.stop();
        client.close();
    }

    @Before
    public void startTransaction() {
        Transaction transaction = tracer.startTransaction().activate();
        transaction.setName("transaction");
        transaction.withType("request");
        transaction.withResult("success");
    }

    @After
    public void endTransaction() {
        Transaction currentTransaction = tracer.currentTransaction();
        if (currentTransaction != null) {
            tracer.endTransaction(currentTransaction);
        }
        reporter.reset();
    }

    @Test
    public void testATryToDeleteNonExistingIndex() throws IOException {
        ElasticsearchStatusException ese = null;
        try {
            client.indices().delete(new DeleteIndexRequest(INDEX), RequestOptions.DEFAULT);
        } catch (ElasticsearchStatusException e) {
            ese = e;
        }
        assertThat(ese).isNotNull();
        assertThat(ese.status().getStatus()).isEqualTo(404);

        System.out.println(reporter.generateErrorPayloadJson());

        List<ErrorCapture> errorCaptures = reporter.getErrors();
        assertThat(errorCaptures).hasSize(1);
        ErrorCapture errorCapture = errorCaptures.get(0);
        assertThat(errorCapture.getException()).isNotNull();
        Map<String, String> tags = errorCapture.getContext().getTags();
        assertThat(tags).containsKey(QUERY_STATUS_CODE_KEY);
        assertThat(tags.get(QUERY_STATUS_CODE_KEY)).isEqualTo("404");
        assertThat(tags).containsKey(ERROR_REASON_KEY);
        assertThat(tags).containsKey(ELASTICSEARCH_NODE_KEY);
        assertThat(tags.get(ELASTICSEARCH_NODE_KEY)).isEqualTo(container.getHost().toHostString());
    }

    private void validateSpanContent(Span span, String expectedName, String expectedStatus) {
        assertThat(span.getName().toString()).isEqualTo(expectedName);
        Map<String, String> tags = span.getContext().getTags();
        assertThat(tags).containsKey(QUERY_STATUS_CODE_KEY);
        assertThat(tags.get(QUERY_STATUS_CODE_KEY)).isEqualTo(expectedStatus);
    }

    @Test
    public void testBCreateIndex() throws IOException {
        client.indices().create(new CreateIndexRequest(INDEX), RequestOptions.DEFAULT);

        System.out.println(reporter.generateTransactionPayloadJson());

        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        validateSpanContent(spans.get(0), String.format("/%s PUT", INDEX), "200");
    }

    @Test
    public void testCIndexADocument() throws IOException {
        IndexResponse ir = client.index(new IndexRequest(INDEX, DOC_TYPE, DOC_ID).source(
            jsonBuilder()
                .startObject()
                .field(FOO, BAR)
                .endObject()
        ).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE), RequestOptions.DEFAULT);
        assertThat(ir.status().getStatus()).isEqualTo(201);

        System.out.println(reporter.generateTransactionPayloadJson());

        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        validateSpanContent(spans.get(0), String.format("/%s/%s/%s PUT", INDEX, DOC_TYPE, DOC_ID), "201");
    }

    @Test
    public void testDSearchTheIndex() throws IOException {
        SearchResponse sr = client.search(new SearchRequest(INDEX), RequestOptions.DEFAULT);
        assertThat(sr.getHits().totalHits).isEqualTo(1L);
        assertThat(sr.getHits().getAt(0).getSourceAsMap().get(FOO)).isEqualTo(BAR);

        System.out.println(reporter.generateTransactionPayloadJson());

        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        validateSpanContent(spans.get(0), String.format("/%s/_search POST", INDEX), "200");
    }

    @Test
    public void testEUpdateTheDocument() throws IOException {
        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put(FOO, BAZ);
        UpdateResponse ur = client.update(new UpdateRequest(INDEX, DOC_TYPE, DOC_ID).doc(jsonMap)
            .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE), RequestOptions.DEFAULT);
        assertThat(ur.status().getStatus()).isEqualTo(200);
        SearchResponse sr = client.search(new SearchRequest(INDEX), RequestOptions.DEFAULT);
        assertThat(sr.getHits().getAt(0).getSourceAsMap().get(FOO)).isEqualTo(BAZ);

        System.out.println(reporter.generateTransactionPayloadJson());

        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(2);
        boolean updateSpanFound = false;
        for(Span span: spans) {
            if(span.getName().toString().contains("_update")) {
                updateSpanFound = true;
                break;
            }
        }
        assertThat(updateSpanFound).isTrue();
    }

    @Test
    public void testFScenarioAsBulkRequest() throws IOException {
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
        , RequestOptions.DEFAULT);

        System.out.println(reporter.generateTransactionPayloadJson());

        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).getName().toString()).isEqualTo("/_bulk POST");
    }

    @Test
    public void testGDeleteExistingIndex() throws IOException {
        // Remove any existing index
        client.indices().delete(new DeleteIndexRequest(INDEX), RequestOptions.DEFAULT);

        System.out.println(reporter.generateTransactionPayloadJson());

        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        validateSpanContent(spans.get(0), String.format("/%s DELETE", INDEX), "200");
    }
}
