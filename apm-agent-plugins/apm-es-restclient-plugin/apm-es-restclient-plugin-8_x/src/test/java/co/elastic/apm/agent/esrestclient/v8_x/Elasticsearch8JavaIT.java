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
package co.elastic.apm.agent.esrestclient.v8_x;

import co.elastic.apm.agent.esrestclient.AbstractEsClientInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.testutils.TestContainersUtils;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.SearchType;
import co.elastic.clients.elasticsearch._types.StoredScript;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.DeleteScriptRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.MsearchRequest;
import co.elastic.clients.elasticsearch.core.MsearchResponse;
import co.elastic.clients.elasticsearch.core.MsearchTemplateRequest;
import co.elastic.clients.elasticsearch.core.MsearchTemplateResponse;
import co.elastic.clients.elasticsearch.core.PutScriptRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.SearchTemplateRequest;
import co.elastic.clients.elasticsearch.core.SearchTemplateResponse;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.UpdateResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.DeleteOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.core.msearch.MultiSearchItem;
import co.elastic.clients.elasticsearch.core.msearch.MultiSearchResponseItem;
import co.elastic.clients.elasticsearch.core.msearch.MultisearchBody;
import co.elastic.clients.elasticsearch.core.msearch.MultisearchHeader;
import co.elastic.clients.elasticsearch.core.msearch.RequestItem;
import co.elastic.clients.elasticsearch.core.msearch_template.TemplateConfig;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexResponse;
import co.elastic.clients.elasticsearch.rollup.RollupSearchRequest;
import co.elastic.clients.elasticsearch.rollup.RollupSearchResponse;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import jakarta.json.Json;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class Elasticsearch8JavaIT extends AbstractEsClientInstrumentationTest {

    private static final String ELASTICSEARCH_CONTAINER_VERSION = "docker.elastic.co/elasticsearch/elasticsearch:7.17.2";

    private static final Logger LOGGER = LoggerFactory.getLogger(Elasticsearch8JavaIT.class);

    protected static final String USER_NAME = "elastic-user";
    protected static final String PASSWORD = "elastic-pass";

    protected static ElasticsearchClient client;
    protected static ElasticsearchAsyncClient asyncClient;

    public Elasticsearch8JavaIT(boolean async) {
        this.async = async;
    }

    protected static void startContainer(String image) {
        container = new ElasticsearchContainer(image)
            .withEnv("ES_JAVA_OPTS", "-XX:-UseContainerSupport")
            .withCreateContainerCmdModifier(TestContainersUtils.withMemoryLimit(4096));
        container.start();
    }

    @BeforeClass
    public static void startElasticsearchContainerAndClient() throws IOException {
        startContainer(ELASTICSEARCH_CONTAINER_VERSION);

        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(USER_NAME, PASSWORD));

        RestClient restClient = RestClient.builder(HttpHost.create(container.getHttpHostAddress()))
            .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
            .build();

        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        client = new ElasticsearchClient(transport);
        asyncClient = new ElasticsearchAsyncClient(transport);

        client.indices().create(new CreateIndexRequest.Builder().index(INDEX).build());
        reporter.reset();
    }

    @AfterClass
    public static void stopElasticsearchContainerAndClient() throws IOException {
        if (client != null) {
            // prevent misleading NPE when failed to start container
            client.indices().delete(new DeleteIndexRequest.Builder().index(INDEX).build());
        }
    }


    @Test
    public void testCreateAndDeleteIndex() throws IOException, ExecutionException, InterruptedException {
        // Create an Index
        doCreateIndex(new CreateIndexRequest.Builder().index(SECOND_INDEX).build());
        validateSpan()
            .method("PUT")
            .endpointName("indices.create")
            .expectPathPart("index", SECOND_INDEX)
            .check();

        reporter.reset();

        // Delete the index
        doDeleteIndex(new DeleteIndexRequest.Builder().index(SECOND_INDEX).build());
        validateSpan()
            .method("DELETE")
            .endpointName("indices.delete")
            .expectPathPart("index", SECOND_INDEX)
            .check();
    }

    @Test
    public void testTryToDeleteNonExistingIndex() {
        Throwable cause = null;
        try {
            doDeleteIndex(new DeleteIndexRequest.Builder().index(SECOND_INDEX).build());
        } catch (ExecutionException ee) {
            // async scenario
            cause = ee.getCause();
        } catch (Exception e) {
            LOGGER.error("Exception during deleting non-existing index", e);
            // sync scenario
            cause = e;
        }

        assertThat(cause).isInstanceOf(ElasticsearchException.class);

        ElasticsearchException ee = (ElasticsearchException) cause;
        assertThat(ee).isNotNull();
        assertThat(ee.status()).isEqualTo(404);

        assertThat(cause).isNotNull();

        // todo: investigate this - no errors captured, because in this case ResponseListener#onSuccess is invoked instead of onFailure
        // assertThatErrorsExistWhenDeleteNonExistingIndex();

        SpanImpl span = reporter.getFirstSpan();
        assertThat(span.getOutcome()).isEqualTo(Outcome.FAILURE);
        validateSpan(span)
            .method("DELETE")
            .endpointName("indices.delete")
            .expectPathPart("index", SECOND_INDEX)
            .statusCode(404)
            .check();
    }

    @Test
    public void testDocumentScenario() throws Exception {
        // 1. Index a document and validate span content
        prepareDefaultDocumentAndIndex();
        List<SpanImpl> spans = reporter.getSpans();
        try {
            assertThat(spans).hasSize(1);
            validateSpan(spans.get(0))
                .method("PUT")
                .endpointName("index")
                .expectPathPart("index", INDEX)
                .expectPathPart("id", DOC_ID)
                .statusCode(201)
                .check();

            // *** RESET ***
            reporter.reset();
            // *** RESET ***

            // 2. Search document and validate span content
            SearchRequest searchRequest = prepareSearchRequestWithTermQuery();
            SearchResponse<Map> response = doSearch(searchRequest, Map.class);

            verifyTotalHits(response.hits());
            spans = reporter.getSpans();
            assertThat(spans).hasSize(1);
            SpanImpl searchSpan = spans.get(0);

            validateSpan(searchSpan)
                .method("POST")
                .endpointName("search")
                .expectPathPart("index", INDEX)
                .expectStatement("{\"from\":0,\"query\":{\"term\":{\"foo\":{\"value\":\"bar\"}}},\"size\":5}")
                .check();

            // *** RESET ***
            reporter.reset();
            // *** RESET ***

            // 3. Update existing document and validate content
            Map<String, Object> jsonMap = new HashMap<>();
            jsonMap.put(FOO, BAZ);
            UpdateRequest updateRequest = new UpdateRequest.Builder().index(INDEX).id(DOC_ID).refresh(Refresh.True).doc(jsonMap).build();
            UpdateResponse ur = doUpdate(updateRequest, Map.class);
            assertThat(ur).isNotNull();
            assertThat(ur.result().jsonValue()).isEqualTo("updated");
            SearchResponse<Map> sr = doSearch(new SearchRequest.Builder().index(INDEX).build(), Map.class);
            assertThat(((Map) ((Hit) (sr.hits().hits().get(0))).source()).get(FOO)).isEqualTo(BAZ);

            spans = reporter.getSpans();
            assertThat(spans).hasSize(2);
            SpanImpl updateSpan = spans.get(0);
            validateSpan(updateSpan)
                .method("POST")
                .endpointName("update")
                .expectPathPart("index", INDEX)
                .expectPathPart("id", DOC_ID)
                .check();

            searchSpan = spans.get(1);
            validateSpan(searchSpan)
                .method("POST")
                .endpointName("search")
                .expectPathPart("index", INDEX)
                .expectStatement("{}")
                .check();

            // *** RESET ***
            reporter.reset();
            // *** RESET ***
        } finally {
            // 4. Delete document and validate span content.
            co.elastic.clients.elasticsearch.core.DeleteResponse dr = deleteDocument();
            assertThat(dr.result().jsonValue()).isEqualTo("deleted");
            validateSpan(spans.get(0))
                .method("DELETE")
                .endpointName("delete")
                .expectPathPart("index", INDEX)
                .expectPathPart("id", DOC_ID)
                .expectAsync(false) // delete is a sync operation
                .check();
        }
    }

    @Test
    public void testCountRequest_validateSpanContentAndDbContext() throws Exception {
        prepareDefaultDocumentAndIndex();
        reporter.reset();

        CountRequest countRequest = new CountRequest.Builder().index(INDEX).query(new Query.Builder()
            .term(new TermQuery.Builder().field(FOO).value(FieldValue.of(BAR)).build())
            .build()).build();

        try {
            CountResponse responses = doCount(countRequest);
            assertThat(responses.count()).isEqualTo(1L);
            validateSpan()
                .method("POST")
                .endpointName("count")
                .expectPathPart("index", INDEX)
                .expectStatement("{\"query\":{\"term\":{\"foo\":{\"value\":\"bar\"}}}}")
                .check();
        } finally {
            deleteDocument();
        }
    }

    @Test
    public void testMultiSearchRequest_validateSpanContentAndDbContext() throws InterruptedException, ExecutionException, IOException {
        prepareDefaultDocumentAndIndex();
        reporter.reset();

        MsearchRequest multiSearchRequest = getMultiSearchRequestBuilder().build();
        MsearchRequest multiSearchRequestWithIndex = getMultiSearchRequestBuilder().index(INDEX).build();

        try {
            doMultiSearch(multiSearchRequest, Map.class);

            String statement = "{\"index\":[\"my-index\"],\"search_type\":\"query_then_fetch\"}\n" +
                "{\"query\":{\"match\":{\"foo\":{\"query\":\"bar\"}}}}\n";

            validateSpan()
                .method("POST")
                .endpointName("msearch")
                .expectNoPathParts()
                .expectStatement(statement)
                .check();
            reporter.reset();
            doMultiSearch(multiSearchRequestWithIndex, Map.class);

            validateSpan()
                .method("POST")
                .endpointName("msearch")
                .expectPathPart("index", INDEX)
                .expectStatement(statement)
                .check();
        } finally {
            deleteDocument();
        }
    }

    private MsearchRequest.Builder getMultiSearchRequestBuilder() {
        return new MsearchRequest.Builder()
            .searches(new RequestItem.Builder()
                .header(new MultisearchHeader.Builder()
                    .index(INDEX)
                    .searchType(SearchType.QueryThenFetch)
                    .build())
                .body(new MultisearchBody.Builder()
                    .query(new Query.Builder()
                        .match(new MatchQuery.Builder()
                            .field(FOO)
                            .query(FieldValue.of(BAR))
                            .build())
                        .build())
                    .build())
                .build());
    }

    @Test
    public void testRollupSearch_validateSpanContentAndDbContext() throws InterruptedException, ExecutionException, IOException {
        prepareDefaultDocumentAndIndex();
        reporter.reset();

        RollupSearchRequest searchRequest = new RollupSearchRequest.Builder()
            .index(INDEX)
            .query(new Query.Builder()
                .term(new TermQuery.Builder().field(FOO).value(FieldValue.of(BAR)).build())
                .build())
            .size(5)
            .build();
        try {
            RollupSearchResponse<Map> response = doRollupSearch(searchRequest, Map.class);

            verifyTotalHits(response.hits());

            validateSpan()
                .method("POST")
                .endpointName("rollup.rollup_search")
                .expectPathPart("index", INDEX)
                .expectStatement("{\"query\":{\"term\":{\"foo\":{\"value\":\"bar\"}}},\"size\":5}")
                .check();
        } finally {
            deleteDocument();
        }
    }

    @Test
    public void testSearchTemplateRequest_validateSpanContentAndDbContext() throws InterruptedException, ExecutionException, IOException {
        prepareDefaultDocumentAndIndex();
        prepareMustacheScriptAndSave();

        reporter.reset();
        try {
            SearchTemplateRequest searchTemplateRequest = prepareSearchTemplateRequest("elastic-search-template");
            SearchTemplateResponse response = doSearchTemplate(searchTemplateRequest, Map.class);

            verifyTotalHits(response.hits());

            validateSpan()
                .method("POST")
                .endpointName("search_template")
                .expectPathPart("index", INDEX)
                .expectStatement("{\"id\":\"elastic-search-template\",\"params\":{\"field\":\"foo\",\"size\":5,\"value\":\"bar\"}}")
                .check();
        } finally {
            deleteMustacheScript();
            deleteDocument();
        }
    }

    @Test
    public void testMultisearchTemplateRequest_validateSpanContentAndDbContext() throws InterruptedException, ExecutionException, IOException {
        prepareDefaultDocumentAndIndex();
        prepareMustacheScriptAndSave();
        reporter.reset();


        MsearchTemplateRequest multiRequest = new MsearchTemplateRequest.Builder()
            .searchTemplates(new co.elastic.clients.elasticsearch.core.msearch_template.RequestItem.Builder()
                .header(new MultisearchHeader.Builder()
                    .index(INDEX)
                    .searchType(SearchType.QueryThenFetch)
                    .build())
                .body(new TemplateConfig.Builder()
                    .id("elastic-search-template")
                    .params(
                        Map.of("field", JsonData.of(FOO), "value", JsonData.of(BAR), "size", JsonData.of(5)))
                    .build()
                ).build())
            .build();

        try {
            MsearchTemplateResponse<Map> response = doMultiSearchTemplate(multiRequest, Map.class);

            List<MultiSearchResponseItem<Map>> items = response.responses();
            assertThat(items.size()).isEqualTo(1);
            MultiSearchItem multiSearchItem = (MultiSearchItem) items.get(0)._get();
            verifyTotalHits(multiSearchItem.hits());
            List<SpanImpl> spans = reporter.getSpans();
            assertThat(spans).hasSize(1);
            SpanImpl span = spans.get(0);

            validateSpan(span)
                .method("POST")
                .endpointName("msearch_template")
                .expectNoPathParts()
                .expectStatement("{\"index\":[\"my-index\"],\"search_type\":\"query_then_fetch\"}\n" +
                    "{\"id\":\"elastic-search-template\",\"params\":{\"value\":\"bar\",\"field\":\"foo\",\"size\":5}}")
                .check();
        } finally {
            deleteMustacheScript();
            deleteDocument();
        }
    }

    @Test
    public void testScenarioAsBulkRequest() throws IOException, ExecutionException, InterruptedException {
        BulkRequest bulkRequest = new BulkRequest.Builder()
            .operations(new BulkOperation.Builder()
                    .index(new IndexOperation.Builder<>()
                        .index(INDEX)
                        .id("2")
                        .document(Json.createObjectBuilder()
                            .add(FOO, BAR)
                            .build())
                        .build()).build(),
                new BulkOperation.Builder()
                    .delete(new DeleteOperation.Builder()
                        .index(INDEX)
                        .id("2")
                        .build()).build())
            .refresh(Refresh.True)
            .build();

        doBulk(bulkRequest);

        validateSpan()
            .method("POST")
            .endpointName("bulk")
            .expectNoPathParts()
            .check();
    }


    private void verifyTotalHits(HitsMetadata hitsMetadata) {
        assertThat(hitsMetadata.total().value()).isEqualTo(1L);
        assertThat(((Map) ((Hit) (hitsMetadata.hits().get(0))).source()).get(FOO)).isEqualTo(BAR);
    }

    private CreateIndexResponse doCreateIndex(CreateIndexRequest createRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            return asyncClient.indices().create(createRequest).get();
        }
        return client.indices().create(createRequest);
    }

    private DeleteIndexResponse doDeleteIndex(DeleteIndexRequest deleteIndexRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            return asyncClient.indices().delete(deleteIndexRequest).get();
        }
        return client.indices().delete(deleteIndexRequest);
    }

    private IndexResponse doIndexDocument(IndexRequest<Object> indexRequest) throws ExecutionException, InterruptedException, IOException {
        if (async) {
            return asyncClient.index(indexRequest).get();
        }
        return client.index(indexRequest);
    }

    private SearchResponse doSearch(SearchRequest searchRequest, Class responseClass) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            return ((CompletableFuture<SearchResponse>) asyncClient.search(searchRequest, responseClass)).get();
        }
        return client.search(searchRequest, responseClass);
    }

    private SearchTemplateResponse doSearchTemplate(SearchTemplateRequest searchRequest, Class responseClass) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            return ((CompletableFuture<SearchTemplateResponse>) asyncClient.searchTemplate(searchRequest, responseClass)).get();
        }
        return client.searchTemplate(searchRequest, responseClass);
    }

    private MsearchTemplateResponse doMultiSearchTemplate(MsearchTemplateRequest searchRequest, Class responseClass) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            return ((CompletableFuture<MsearchTemplateResponse>) asyncClient.msearchTemplate(searchRequest, responseClass)).get();
        }
        return client.msearchTemplate(searchRequest, responseClass);
    }

    private RollupSearchResponse doRollupSearch(RollupSearchRequest rollupSearchRequest, Class responseClass) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            return ((CompletableFuture<RollupSearchResponse>) asyncClient.rollup().rollupSearch(rollupSearchRequest, responseClass)).get();
        }
        return client.rollup().rollupSearch(rollupSearchRequest, responseClass);
    }

    private MsearchResponse doMultiSearch(MsearchRequest searchRequest, Class responseClass) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            return ((CompletableFuture<MsearchResponse>) asyncClient.msearch(searchRequest, responseClass)).get();
        }
        return client.msearch(searchRequest, responseClass);
    }

    private CountResponse doCount(CountRequest countRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            return asyncClient.count(countRequest).get();
        }
        return client.count(countRequest);
    }

    private UpdateResponse doUpdate(UpdateRequest updateRequest, Class updateClass) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            return ((CompletableFuture<UpdateResponse>) asyncClient.update(updateRequest, updateClass)).get();
        }
        return client.update(updateRequest, updateClass);
    }

    private BulkResponse doBulk(BulkRequest bulkRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            return ((CompletableFuture<BulkResponse>) asyncClient.bulk(bulkRequest)).get();
        }
        return client.bulk(bulkRequest);
    }

    private DeleteResponse deleteDocument() throws IOException {
        return client.delete(new DeleteRequest.Builder().index(INDEX).id(DOC_ID).refresh(Refresh.True).build());
    }

    private SearchRequest prepareSearchRequestWithTermQuery() {
        return new SearchRequest.Builder().index(INDEX)
            .query(new Query.Builder()
                .term(new TermQuery.Builder().field(FOO).value(FieldValue.of(BAR)).build())
                .build())
            .from(0)
            .size(5)
            .build();
    }

    private void prepareMustacheScriptAndSave() throws IOException {
        client.putScript(new PutScriptRequest.Builder()
            .id("elastic-search-template")
            .script(new StoredScript.Builder()
                .lang("mustache")
                .source("{" +
                    "  \"query\": { \"term\" : { \"{{field}}\" : \"{{value}}\" } }," +
                    "  \"size\" : \"{{size}}\"" +
                    "}").build()
            ).build());
    }

    private void deleteMustacheScript() throws IOException {
        client.deleteScript(new DeleteScriptRequest.Builder()
            .id("elastic-search-template")
            .build());
    }

    private void prepareDefaultDocumentAndIndex() throws IOException, ExecutionException, InterruptedException {
        IndexResponse ir = doIndexDocument(new IndexRequest.Builder()
            .index(INDEX)
            .id(DOC_ID)
            .refresh(Refresh.True)
            .document(Map.of(FOO, BAR))
            .build());
        assertThat(ir).isNotNull();
        assertThat(ir.result().jsonValue()).isEqualTo("created");
        assertThat(ir.id()).isNotBlank();
        assertThat(ir.index()).isEqualTo(INDEX);
    }

    private SearchTemplateRequest prepareSearchTemplateRequest(String templateId) {
        Map<String, JsonData> scriptParams = new HashMap<>();
        scriptParams.put("field", JsonData.of(FOO));
        scriptParams.put("value", JsonData.of(BAR));
        scriptParams.put("size", JsonData.of(5));
        SearchTemplateRequest searchTemplateRequest = new SearchTemplateRequest.Builder()
            .index(INDEX)
            .id(templateId)
            .params(scriptParams)
            .build();
        return searchTemplateRequest;
    }
}
