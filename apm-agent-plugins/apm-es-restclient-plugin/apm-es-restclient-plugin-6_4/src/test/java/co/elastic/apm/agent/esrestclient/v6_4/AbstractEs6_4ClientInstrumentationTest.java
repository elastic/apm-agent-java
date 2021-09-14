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
package co.elastic.apm.agent.esrestclient.v6_4;

import co.elastic.apm.agent.esrestclient.AbstractEsClientInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Span;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.script.mustache.MultiSearchTemplateRequest;
import org.elasticsearch.script.mustache.MultiSearchTemplateResponse;
import org.elasticsearch.script.mustache.SearchTemplateRequest;
import org.elasticsearch.script.mustache.SearchTemplateResponse;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public abstract class AbstractEs6_4ClientInstrumentationTest extends AbstractEsClientInstrumentationTest {

    protected static final String USER_NAME = "elastic-user";
    protected static final String PASSWORD = "elastic-pass";

    @SuppressWarnings("NullableProblems")
    protected static RestHighLevelClient client;

    @Test
    public void testCreateAndDeleteIndex() throws IOException, ExecutionException, InterruptedException {
        // Create an Index
        doCreateIndex(new CreateIndexRequest(SECOND_INDEX));

        validateSpanContentAfterIndexCreateRequest();
        // Delete the index
        reporter.reset();

        doDeleteIndex(new DeleteIndexRequest(SECOND_INDEX));

        validateSpanContentAfterIndexDeleteRequest();
    }

    @Test
    public void testTryToDeleteNonExistingIndex() throws IOException, InterruptedException {
        ElasticsearchStatusException ese = null;
        try {
            doDeleteIndex(new DeleteIndexRequest(SECOND_INDEX));
        } catch (ElasticsearchStatusException e) {
            // sync scenario
            ese = e;
        } catch (ExecutionException e) {
            // async scenario
            ese = (ElasticsearchStatusException) e.getCause();
        }
        assertThat(ese).isNotNull();
        assertThat(ese.status().getStatus()).isEqualTo(404);

        assertThatErrorsExistWhenDeleteNonExistingIndex();
    }

    @Test
    public void testDocumentScenario() throws Exception {
        // Index a document
        createDocument();

        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        validateSpanContent(spans.get(0), String.format("Elasticsearch: PUT /%s/%s/%s", INDEX, DOC_TYPE, DOC_ID), 201, "PUT");
        reporter.reset();

        // do search request
        SearchRequest searchRequest = defaultSearchRequest();

        SearchResponse response = doSearch(searchRequest);

        verifyTotalHits(response.getHits());
        spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        Span searchSpan = spans.get(0);
        validateSpanContent(searchSpan, String.format("Elasticsearch: POST /%s/_search", INDEX), 200, "POST");
        validateDbContextContent(searchSpan, "{\"from\":0,\"size\":5,\"query\":{\"term\":{\"foo\":{\"value\":\"bar\",\"boost\":1.0}}}}");
        reporter.reset();

        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put(FOO, BAZ);
        UpdateRequest updateRequest = new UpdateRequest(INDEX, DOC_TYPE, DOC_ID).doc(jsonMap).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        UpdateResponse ur = doUpdate(updateRequest);
        assertThat(ur.status().getStatus()).isEqualTo(200);
        SearchResponse sr = doSearch(new SearchRequest(INDEX));
        assertThat(sr.getHits().getAt(0).getSourceAsMap().get(FOO)).isEqualTo(BAZ);

        spans = reporter.getSpans();
        assertThat(spans).hasSize(2);
        boolean updateSpanFound = false;
        for (Span span : spans) {
            if (span.getNameAsString().contains("_update")) {
                updateSpanFound = true;
                break;
            }
        }
        assertThat(updateSpanFound).isTrue();

        // Finally - delete the document
        reporter.reset();
        DeleteResponse dr = deleteDocument();
        assertThat(dr.status().getStatus()).isEqualTo(200);
        validateSpanContent(spans.get(0), String.format("Elasticsearch: DELETE /%s/%s/%s", INDEX, DOC_TYPE, DOC_ID), 200, "DELETE");
    }

    @Test
    public void testCountRequest_validateSpanContentAndDbContext() throws Exception {
        createDocument();
        reporter.reset();

        CountRequest countRequest = new CountRequest(INDEX);
        SearchSourceBuilder countSourceBuilder = new SearchSourceBuilder();
        countSourceBuilder.query(QueryBuilders.termQuery(FOO, BAR));
        countRequest.source(countSourceBuilder);

        CountResponse responses = doCount(countRequest);

        assertThat(responses.getCount()).isEqualTo(1);
        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        Span span = spans.get(0);
        validateSpanContent(span, String.format("Elasticsearch: POST /%s/_count", INDEX), 200, "POST");
        validateDbContextContent(span, "{\"query\":{\"term\":{\"foo\":{\"value\":\"bar\",\"boost\":1.0}}}}");

        deleteDocument();
    }

    @Test
    public void testMultiSearchRequest_validateSpanContentAndDbContext() throws InterruptedException, ExecutionException, IOException {
        createDocument();
        reporter.reset();

        MultiSearchRequest multiSearchRequest = new MultiSearchRequest();
        SearchRequest firstSearchRequest = new SearchRequest(INDEX);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.matchQuery(FOO, BAR));
        firstSearchRequest.source(searchSourceBuilder);
        multiSearchRequest.add(firstSearchRequest);

        MultiSearchResponse response = doMultiSearch(multiSearchRequest);

        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        Span span = spans.get(0);
        validateSpanContent(span, "Elasticsearch: POST /_msearch", 200, "POST");
        verifyMultiSearchSpanContent(span);

        deleteDocument();
    }

    @Test
    public void testRollupSearch_validateSpanContentAndDbContext() throws InterruptedException, ExecutionException, IOException {
        createDocument();
        reporter.reset();

        SearchRequest rollupSearchRequest = new SearchRequest(INDEX);
        SearchSourceBuilder rollupSearchBuilder = new SearchSourceBuilder();
        rollupSearchBuilder.query(QueryBuilders.termQuery(FOO, BAR));
        rollupSearchBuilder.from(0);
        rollupSearchBuilder.size(5);
        rollupSearchRequest.source(rollupSearchBuilder);

        SearchResponse response = doRollupSearch(rollupSearchRequest);

        verifyTotalHits(response.getHits());
        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        Span span = spans.get(0);
        validateSpanContent(span, String.format("Elasticsearch: POST /%s/_rollup_search", INDEX), 200, "POST");
        validateDbContextContent(span, "{\"from\":0,\"size\":5,\"query\":{\"term\":{\"foo\":{\"value\":\"bar\",\"boost\":1.0}}}}");

        deleteDocument();
    }

    @Test
    public void testSearchTemplateRequest_validateSpanContentAndDbContext() throws InterruptedException, ExecutionException, IOException {
        createDocument();
        reporter.reset();

        SearchTemplateRequest searchTemplateRequest = prepareSearchTemplateRequest();

        SearchTemplateResponse response = doSearchTemplate(searchTemplateRequest);

        verifyTotalHits(response.getResponse().getHits());
        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        Span span = spans.get(0);
        validateSpanContent(span, String.format("Elasticsearch: GET /%s/_search/template", INDEX), 200, "GET");
        validateDbContextContent(span, "{\"source\":\"{  \\\"query\\\": { \\\"term\\\" : { \\\"{{field}}\\\" : \\\"{{value}}\\\" } },  \\\"size\\\" : \\\"{{size}}\\\"}\",\"params\":{\"field\":\"foo\",\"size\":5,\"value\":\"bar\"},\"explain\":false,\"profile\":false}");

        deleteDocument();
    }

    @Test
    public void testMultisearchTemplateRequest_validateSpanContentAndDbContext() throws InterruptedException, ExecutionException, IOException {
        createDocument();
        reporter.reset();

        SearchTemplateRequest searchTemplateRequest = prepareSearchTemplateRequest();
        MultiSearchTemplateRequest multiRequest = new MultiSearchTemplateRequest();
        multiRequest.add(searchTemplateRequest);

        MultiSearchTemplateResponse response = doMultiSearchTemplate(multiRequest);

        MultiSearchTemplateResponse.Item[] items = response.getResponses();
        assertThat(items.length).isEqualTo(1);
        verifyTotalHits(items[0].getResponse().getResponse().getHits());
        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        Span span = spans.get(0);
        validateSpanContent(span, String.format("Elasticsearch: POST /_msearch/template", INDEX), 200, "POST");
        verifyMultiSearchTemplateSpanContent(span);

        deleteDocument();
    }

    protected void createDocument() throws IOException, ExecutionException, InterruptedException {
        IndexResponse ir = doIndex(new IndexRequest(INDEX, DOC_TYPE, DOC_ID).source(
            jsonBuilder()
                .startObject()
                .field(FOO, BAR)
                .endObject()
        ).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE));
        assertThat(ir.status().getStatus()).isEqualTo(201);
    }

    protected DeleteResponse deleteDocument() throws InterruptedException, ExecutionException, IOException {
        return doDelete(new DeleteRequest(INDEX, DOC_TYPE, DOC_ID));
    }

    private SearchTemplateRequest prepareSearchTemplateRequest() {
        SearchTemplateRequest searchTemplateRequest = new SearchTemplateRequest();
        searchTemplateRequest.setRequest(new SearchRequest(INDEX));
        searchTemplateRequest.setScriptType(ScriptType.INLINE);
        searchTemplateRequest.setScript(
            "{" +
                "  \"query\": { \"term\" : { \"{{field}}\" : \"{{value}}\" } }," +
                "  \"size\" : \"{{size}}\"" +
                "}");
        Map<String, Object> scriptParams = new HashMap<>();
        scriptParams.put("field", FOO);
        scriptParams.put("value", BAR);
        scriptParams.put("size", 5);
        searchTemplateRequest.setScriptParams(scriptParams);
        return searchTemplateRequest;
    }

    protected void verifyMultiSearchTemplateSpanContent(Span span) {

    }

    protected void verifyMultiSearchSpanContent(Span span) {

    }

    protected void verifyTotalHits(SearchHits searchHits) {

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


    protected interface ClientMethod<Req, Res> {
        void invoke(Req request, RequestOptions options, ActionListener<Res> listener);
    }

    protected  <Req, Res> Res invokeAsync(Req request, ClientMethod<Req, Res> method) throws InterruptedException, ExecutionException {
        final CompletableFuture<Res> resultFuture = new CompletableFuture<>();
        method.invoke(request, RequestOptions.DEFAULT, new ActionListener<>() {
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

    protected CreateIndexResponse doCreateIndex(CreateIndexRequest createIndexRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            ClientMethod<CreateIndexRequest, CreateIndexResponse> method =
                (request, options, listener) -> client.indices().createAsync(request, options, listener);
            return invokeAsync(createIndexRequest, method);
        }
        return client.indices().create(createIndexRequest, RequestOptions.DEFAULT);
    }

    protected AcknowledgedResponse doDeleteIndex(DeleteIndexRequest deleteIndexRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            ClientMethod<DeleteIndexRequest, AcknowledgedResponse> method =
                (request, options, listener) -> client.indices().deleteAsync(request, options, listener);
            return invokeAsync(deleteIndexRequest, method);
        }
        return client.indices().delete(deleteIndexRequest, RequestOptions.DEFAULT);
    }

    protected IndexResponse doIndex(IndexRequest indexRequest) throws ExecutionException, InterruptedException, IOException {
        if (async) {
            ClientMethod<IndexRequest, IndexResponse> method =
                (request, options, listener) -> client.indexAsync(request, options, listener);
            return invokeAsync(indexRequest, method);
        }
        return client.index(indexRequest, RequestOptions.DEFAULT);
    }

    protected SearchResponse doSearch(SearchRequest searchRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            ClientMethod<SearchRequest, SearchResponse> method =
                (request, options, listener) -> client.searchAsync(request, options, listener);
            return invokeAsync(searchRequest, method);
        }
        return client.search(searchRequest, RequestOptions.DEFAULT);
    }

    protected SearchTemplateResponse doSearchTemplate(SearchTemplateRequest searchRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            ClientMethod<SearchTemplateRequest, SearchTemplateResponse> method =
                (request, options, listener) -> client.searchTemplateAsync(request, options, listener);
            return invokeAsync(searchRequest, method);
        }
        return client.searchTemplate(searchRequest, RequestOptions.DEFAULT);
    }

    protected MultiSearchTemplateResponse doMultiSearchTemplate(MultiSearchTemplateRequest searchRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            ClientMethod<MultiSearchTemplateRequest, MultiSearchTemplateResponse> method =
                (request, options, listener) -> client.msearchTemplateAsync(request, options, listener);
            return invokeAsync(searchRequest, method);
        }
        return client.msearchTemplate(searchRequest, RequestOptions.DEFAULT);
    }

    protected SearchResponse doRollupSearch(SearchRequest searchRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            ClientMethod<SearchRequest, SearchResponse> method =
                (request, options, listener) -> client.rollup().searchAsync(request, options, listener);
            return invokeAsync(searchRequest, method);
        }
        return client.rollup().search(searchRequest, RequestOptions.DEFAULT);
    }

    protected MultiSearchResponse doMultiSearch(MultiSearchRequest searchRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            ClientMethod<MultiSearchRequest, MultiSearchResponse> method =
                (request, options, listener) -> client.msearchAsync(request, options, listener);
            return invokeAsync(searchRequest, method);
        }
        return client.msearch(searchRequest, RequestOptions.DEFAULT);
    }

    protected CountResponse doCount(CountRequest countRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            ClientMethod<CountRequest, CountResponse> method =
                (request, options, listener) -> client.countAsync(request, options, listener);
            return invokeAsync(countRequest, method);
        }
        return client.count(countRequest, RequestOptions.DEFAULT);
    }

    protected UpdateResponse doUpdate(UpdateRequest updateRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            ClientMethod<UpdateRequest, UpdateResponse> method =
                (request, options, listener) -> client.updateAsync(request, options, listener);
            return invokeAsync(updateRequest, method);
        }
        return client.update(updateRequest, RequestOptions.DEFAULT);
    }

    protected DeleteResponse doDelete(DeleteRequest deleteRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            ClientMethod<DeleteRequest, DeleteResponse> method =
                (request, options, listener) -> client.deleteAsync(request, options, listener);
            return invokeAsync(deleteRequest, method);
        }
        return client.delete(deleteRequest, RequestOptions.DEFAULT);
    }

    protected BulkResponse doBulk(BulkRequest bulkRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            ClientMethod<BulkRequest, BulkResponse> method =
                (request, options, listener) -> client.bulkAsync(request, options, listener);
            return invokeAsync(bulkRequest, method);
        }
        return client.bulk(bulkRequest, RequestOptions.DEFAULT);
    }

    protected SearchRequest defaultSearchRequest() {
        SearchRequest searchRequest = new SearchRequest(INDEX);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.termQuery(FOO, BAR));
        sourceBuilder.from(0);
        sourceBuilder.size(5);
        searchRequest.source(sourceBuilder);
        return searchRequest;
    }

}
