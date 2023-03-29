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
package co.elastic.apm.agent.esrestclient.v5_6;

import co.elastic.apm.agent.esrestclient.AbstractEsClientInstrumentationTest;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.ResponseListener;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

@RunWith(Parameterized.class)
public class ElasticsearchRestClientInstrumentationIT extends AbstractEsClientInstrumentationTest {

    private static final String ELASTICSEARCH_CONTAINER_VERSION = "docker.elastic.co/elasticsearch/elasticsearch:5.6.0";
    protected static final String USER_NAME = "elastic";
    protected static final String PASSWORD = "changeme";

    protected static final String DOC_TYPE = "doc";
    private static RestHighLevelClient client;
    @SuppressWarnings("NullableProblems")
    protected static RestClient lowLevelClient;

    public ElasticsearchRestClientInstrumentationIT(boolean async) {
        this.async = async;
    }

    @BeforeClass
    public static void startElasticsearchContainerAndClient() throws IOException {
        // Start the container
        startContainer(ELASTICSEARCH_CONTAINER_VERSION);

        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
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
        lowLevelClient.close();
    }

    @Test
    public void testTryToDeleteNonExistingIndex() throws IOException {
        ResponseException re = null;
        try {
            doPerformRequest("POST", "/non-existing/1/_mapping");
        } catch (ResponseException e) {
            re = e;
        } catch (ExecutionException e) {
            re = (ResponseException) e.getCause();
        }
        assertThat(re).isNotNull();
        assertThat(re.getResponse().getStatusLine().getStatusCode()).isEqualTo(400);

        assertThatErrorsExistWhenDeleteNonExistingIndex();

        assertThat(reporter.getFirstSpan().getOutcome()).isEqualTo(Outcome.FAILURE);
    }

    @Test
    public void testCreateAndDeleteIndex() throws IOException, ExecutionException {
        // Create an Index
        doPerformRequest("PUT", "/" + SECOND_INDEX);

        validateSpanContentAfterIndexCreateRequest();

        // Delete the index
        reporter.reset();

        doPerformRequest("DELETE", "/" + SECOND_INDEX);

        validateSpanContentAfterIndexDeleteRequest();

        assertThat(reporter.getFirstSpan().getOutcome()).isEqualTo(Outcome.SUCCESS);
    }

    @Test
    public void testDocumentScenario() throws IOException, ExecutionException, InterruptedException {
        // Index a document
        IndexResponse ir = doIndex(new IndexRequest(INDEX, DOC_TYPE, DOC_ID).source(
            jsonBuilder()
                .startObject()
                .field(FOO, BAR)
                .endObject()
        ).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE));
        assertThat(ir.status().getStatus()).isEqualTo(201);


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
        SearchResponse sr = doSearch(searchRequest);
        assertThat(sr.getHits().totalHits).isEqualTo(1L);
        assertThat(sr.getHits().getAt(0).getSourceAsMap().get(FOO)).isEqualTo(BAR);


        spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        Span searchSpan = spans.get(0);
        validateSpanContent(searchSpan, String.format("Elasticsearch: GET /%s/_search", INDEX), 200, "GET");
        validateDbContextContent(searchSpan, "{\"from\":0,\"size\":5,\"query\":{\"term\":{\"foo\":{\"value\":\"bar\",\"boost\":1.0}}}}");

        // Now update and re-search
        reporter.reset();

        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put(FOO, BAZ);
        UpdateResponse ur = doUpdate(new UpdateRequest(INDEX, DOC_TYPE, DOC_ID).doc(jsonMap)
            .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE));
        assertThat(ur.status().getStatus()).isEqualTo(200);
        sr = doSearch(new SearchRequest(INDEX));
        assertThat(sr.getHits().getAt(0).getSourceAsMap().get(FOO)).isEqualTo(BAZ);


        spans = reporter.getSpans();
        assertThat(spans).hasSize(2);
        boolean updateSpanFound = false;
        for(Span span: spans) {
            if(span.getNameAsString().contains("_update")) {
                updateSpanFound = true;
                break;
            }
        }
        assertThat(updateSpanFound).isTrue();

        // Finally - delete the document
        reporter.reset();
        DeleteResponse dr = doDelete(new DeleteRequest(INDEX, DOC_TYPE, DOC_ID));
        validateSpanContent(spans.get(0), String.format("Elasticsearch: DELETE /%s/%s/%s", INDEX, DOC_TYPE, DOC_ID), 200, "DELETE");
    }

    @Test
    public void testScenarioAsBulkRequest() throws IOException, ExecutionException, InterruptedException {
        doBulk(new BulkRequest()
            .add(new IndexRequest(INDEX, DOC_TYPE, "2").source(
                jsonBuilder()
                    .startObject()
                    .field(FOO, BAR)
                    .endObject()
            ))
            .add(new DeleteRequest(INDEX, DOC_TYPE, "2")));

        validateSpanContentAfterBulkRequest();
    }

    private interface ClientMethod<Req, Res> {
        void invoke(Req request, ActionListener<Res> listener);
    }

    private <Req, Res> Res invokeAsync(Req request, ClientMethod<Req, Res> method) throws InterruptedException, ExecutionException {
        final CompletableFuture<Res> resultFuture = new CompletableFuture<>();
        method.invoke(request, new ActionListener<>() {
            @Override
            public void onResponse(Res response) {
                resultFuture.complete(response);
            }

            @Override
            public void onFailure(Exception e) {
                resultFuture.completeExceptionally(e);
            }
        });
        return resultFuture.get();
    }

    private IndexResponse doIndex(IndexRequest indexRequest) throws ExecutionException, InterruptedException, IOException {
        if (async) {
            ClientMethod<IndexRequest, IndexResponse> method =
                (request, listener) -> client.indexAsync(request, listener);
            return invokeAsync(indexRequest, method);
        }
        return client.index(indexRequest);
    }

    private SearchResponse doSearch(SearchRequest searchRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            ClientMethod<SearchRequest, SearchResponse> method =
                (request, listener) -> client.searchAsync(request, listener);
            return invokeAsync(searchRequest, method);
        }
        return client.search(searchRequest);
    }

    private UpdateResponse doUpdate(UpdateRequest updateRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            ClientMethod<UpdateRequest, UpdateResponse> method =
                (request, listener) -> client.updateAsync(request, listener);
            return invokeAsync(updateRequest, method);
        }
        return client.update(updateRequest);
    }

    private DeleteResponse doDelete(DeleteRequest deleteRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            ClientMethod<DeleteRequest, DeleteResponse> method =
                (request, listener) -> client.deleteAsync(request, listener);
            return invokeAsync(deleteRequest, method);
        }
        return client.delete(deleteRequest);
    }

    private BulkResponse doBulk(BulkRequest bulkRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            ClientMethod<BulkRequest, BulkResponse> method =
                (request, listener) -> client.bulkAsync(request, listener);
            return invokeAsync(bulkRequest, method);
        }
        return client.bulk(bulkRequest);
    }


    private Response doPerformRequest(String method, String path) throws IOException, ExecutionException {
        if (async) {
            final CompletableFuture<Response> resultFuture = new CompletableFuture<>();
            lowLevelClient.performRequestAsync(method, path, new ResponseListener() {
                @Override
                public void onSuccess(Response response) {
                    resultFuture.complete(response);
                }

                @Override
                public void onFailure(Exception exception) {
                    resultFuture.completeExceptionally(exception);
                }
            });
            try {
                return resultFuture.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return lowLevelClient.performRequest(method, path);
    }

}
