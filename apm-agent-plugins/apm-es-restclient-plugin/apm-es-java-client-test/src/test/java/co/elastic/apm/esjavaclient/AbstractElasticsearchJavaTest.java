package co.elastic.apm.esjavaclient;

import co.elastic.apm.agent.esrestclient.AbstractEsClientInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.clients.base.ApiException;
import co.elastic.clients.base.ElasticsearchError;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._core.BulkRequest;
import co.elastic.clients.elasticsearch._core.BulkResponse;
import co.elastic.clients.elasticsearch._core.CountRequest;
import co.elastic.clients.elasticsearch._core.CountResponse;
import co.elastic.clients.elasticsearch._core.IndexRequest;
import co.elastic.clients.elasticsearch._core.IndexResponse;
import co.elastic.clients.elasticsearch._core.MsearchRequest;
import co.elastic.clients.elasticsearch._core.MsearchResponse;
import co.elastic.clients.elasticsearch._core.MsearchTemplateRequest;
import co.elastic.clients.elasticsearch._core.MsearchTemplateResponse;
import co.elastic.clients.elasticsearch._core.PutScriptRequest;
import co.elastic.clients.elasticsearch._core.SearchRequest;
import co.elastic.clients.elasticsearch._core.SearchResponse;
import co.elastic.clients.elasticsearch._core.SearchTemplateRequest;
import co.elastic.clients.elasticsearch._core.SearchTemplateResponse;
import co.elastic.clients.elasticsearch._core.UpdateRequest;
import co.elastic.clients.elasticsearch._core.UpdateResponse;
import co.elastic.clients.elasticsearch._core.bulk.DeleteOperation;
import co.elastic.clients.elasticsearch._core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch._core.bulk.Operation;
import co.elastic.clients.elasticsearch._core.msearch_template.TemplateItem;
import co.elastic.clients.elasticsearch._core.search.Hit;
import co.elastic.clients.elasticsearch._core.search.HitsMetadata;
import co.elastic.clients.elasticsearch._types.ScriptLanguage;
import co.elastic.clients.elasticsearch._types.StoredScript;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.indices.CreateRequest;
import co.elastic.clients.elasticsearch.indices.CreateResponse;
import co.elastic.clients.elasticsearch.indices.DeleteRequest;
import co.elastic.clients.elasticsearch.indices.DeleteResponse;
import co.elastic.clients.elasticsearch.rollup.RollupSearchRequest;
import co.elastic.clients.elasticsearch.rollup.RollupSearchResponse;
import co.elastic.clients.json.JsonData;
import jakarta.json.Json;
import jakarta.json.JsonValue;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractElasticsearchJavaTest extends AbstractEsClientInstrumentationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractElasticsearchJavaTest.class);

    protected static final String USER_NAME = "elastic-user";
    protected static final String PASSWORD = "elastic-pass";

    protected static ElasticsearchClient client;

    @Test
    public void testCreateAndDeleteIndex() throws IOException, ExecutionException, InterruptedException {
        // Create an Index
        doCreateIndex(new CreateRequest(builder -> builder.index(SECOND_INDEX)));
        validateSpanContentAfterIndexCreateRequest();

        reporter.reset();

        // Delete the index
        doDeleteIndex(new DeleteRequest(builder -> builder.index(SECOND_INDEX)));
        validateSpanContentAfterIndexDeleteRequest();
    }

    @Test
    public void testTryToDeleteNonExistingIndex() throws IOException, InterruptedException {
        ApiException apiException = null;
        try {
            doDeleteIndex(new DeleteRequest(builder -> builder.index(SECOND_INDEX)));
        } catch (ApiException ae) {
            LOGGER.error("Exception during deleting non-existing index", ae);
            // sync scenario
            apiException = ae;
        } catch (ExecutionException executionException) {
            // TODO async scenario
//            ese = (ElasticsearchStatusException) e.getCause();
        }
        assertThat(apiException).isNotNull();
        ElasticsearchError elasticsearchError = (ElasticsearchError) apiException.error();
        assertThat(elasticsearchError.status()).isEqualTo(404);
        LOGGER.debug("Elasticsearch error = {}", elasticsearchError.error().toString());
        // TODO - check why error not captured..
//        assertThatErrorsExistWhenDeleteNonExistingIndex();
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

        SearchResponse<Map> response = doSearch(searchRequest, Map.class);

        verifyTotalHits(response.hits());
        spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        Span searchSpan = spans.get(0);
        validateSpanContent(searchSpan, String.format("Elasticsearch: POST /%s/_search", INDEX), 200, "POST");
        validateDbContextContent(searchSpan, "{\"from\":0,\"query\":{\"term\":{\"foo\":{\"value\":\"bar\"}}},\"size\":5}");
        reporter.reset();

        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put(FOO, BAZ);
        UpdateRequest updateRequest = new UpdateRequest(new UpdateRequest.Builder().index(INDEX).type(DOC_TYPE).id(DOC_ID).refresh(JsonValue.TRUE).doc(jsonMap));
        UpdateResponse ur = doUpdate(updateRequest, Map.class);
        assertThat(ur).isNotNull();
        assertThat(ur.result().jsonValue()).isEqualTo("updated");

        SearchResponse sr = doSearch(new SearchRequest(builder -> builder.index(INDEX)), Map.class);

        assertThat(((Map) ((Hit) (sr.hits().hits().get(0))).source()).get(FOO)).isEqualTo(BAZ);

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
        co.elastic.clients.elasticsearch._core.DeleteResponse dr = deleteDocument();
        assertThat(dr.result().jsonValue()).isEqualTo("deleted");
        validateSpanContent(spans.get(0), String.format("Elasticsearch: DELETE /%s/%s/%s", INDEX, DOC_TYPE, DOC_ID), 200, "DELETE");
    }

    @Test
    public void testCountRequest_validateSpanContentAndDbContext() throws Exception {
        createDocument();
        reporter.reset();

        CountRequest countRequest = new CountRequest(builder -> builder.index(INDEX).query(new Query.Builder()
            .term(new TermQuery.Builder().field(FOO).value(BAR).build())
            .build()));

        CountResponse responses = doCount(countRequest);

        assertThat(responses.count()).isEqualTo(1L);
        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        Span span = spans.get(0);
        validateSpanContent(span, String.format("Elasticsearch: POST /%s/_count", INDEX), 200, "POST");
        validateDbContextContent(span, "{\"query\":{\"term\":{\"foo\":{\"value\":\"bar\"}}}}");

        deleteDocument();
    }

    // TODO resolve problem with newline
    @Test
    public void testMultiSearchRequest_validateSpanContentAndDbContext() throws InterruptedException, ExecutionException, IOException {
        createDocument();
        reporter.reset();

        MsearchRequest multiSearchRequest = new MsearchRequest(builder -> builder
            .index(INDEX)
            .searches(Json.createArrayBuilder().add(Json.createObjectBuilder()
                .add("query", Json.createObjectBuilder().add("match", Json.createObjectBuilder().add(FOO, BAR)))
                .build()).build()));

        ApiException apiException = null;
        try {
            MsearchResponse response = doMultiSearch(multiSearchRequest, Map.class);
        } catch (ApiException e) {
            apiException = e;
        }

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

        RollupSearchRequest searchRequest = new RollupSearchRequest(builder -> builder.index(INDEX)
            .query(new Query.Builder()
                .term(new TermQuery.Builder().field(FOO).value(BAR).build())
                .build())
            .size(5)
        );
        try {
            RollupSearchResponse<Map> response = doRollupSearch(searchRequest, Map.class);

            verifyTotalHits(response.hits());
            List<Span> spans = reporter.getSpans();
            assertThat(spans).hasSize(1);
            Span span = spans.get(0);
            validateSpanContent(span, String.format("Elasticsearch: POST /%s/_rollup_search", INDEX), 200, "POST");
            validateDbContextContent(span, "{\"query\":{\"term\":{\"foo\":{\"value\":\"bar\"}}},\"size\":5}");
        } finally {
            deleteDocument();
        }
    }

    @Test
    public void testSearchTemplateRequest_validateSpanContentAndDbContext() throws InterruptedException, ExecutionException, IOException {
        createDocument();
        createMustachePutScriptAndSave();

        reporter.reset();
        try {
            SearchTemplateRequest searchTemplateRequest = prepareSearchTemplateRequest("elastic-search-template");
            SearchTemplateResponse response = doSearchTemplate(searchTemplateRequest, Map.class);

            verifyTotalHits(response.hits());
            List<Span> spans = reporter.getSpans();
            assertThat(spans).hasSize(1);
            Span span = spans.get(0);
            validateSpanContent(span, String.format("Elasticsearch: POST /%s/_search/template", INDEX), 200, "POST");
            validateDbContextContent(span, "{\"id\":\"elastic-search-template\",\"params\":{\"field\":\"foo\",\"size\":5,\"value\":\"bar\"}}");
        } finally {
            deleteDocument();
        }
    }

    // TODO - newline problem, check response data and resolve verifyTotalHits
    @Test
    public void testMultisearchTemplateRequest_validateSpanContentAndDbContext() throws InterruptedException, ExecutionException, IOException {
        createDocument();
        createMustachePutScriptAndSave();
        reporter.reset();

        MsearchTemplateRequest multiRequest = new MsearchTemplateRequest(builder -> builder.searchTemplates(
            new TemplateItem(itemBuilder -> itemBuilder.index(INDEX)
                .id("elastic-search-template")
                .params(Map.of("field", JsonData.of(FOO), "value", JsonData.of(BAR), "size", JsonData.of(5))))
        ));

        try {
            MsearchTemplateResponse<Map> response = doMultiSearchTemplate(multiRequest, Map.class);

            List<JsonValue> items = response.responses();
            assertThat(items.size()).isEqualTo(1);
//        verifyTotalHits(items.get(0).getResponse().getResponse().getHits());
            List<Span> spans = reporter.getSpans();
            assertThat(spans).hasSize(1);
            Span span = spans.get(0);
            validateSpanContent(span, String.format("Elasticsearch: POST /_msearch/template", INDEX), 200, "POST");
            verifyMultiSearchTemplateSpanContent(span);
        } finally {
            deleteDocument();
        }
    }

    @Test
    public void testScenarioAsBulkRequest() throws IOException, ExecutionException, InterruptedException {
        BulkRequest bulkRequest = new BulkRequest(new BulkRequest.Builder()
            .addOperation(new Operation.Builder().index(new IndexOperation(indexOperationBuilder -> indexOperationBuilder.index(INDEX).id("2"))).build())
            .addOperation(new Operation.Builder().delete(new DeleteOperation(deleteOperationBuilder -> deleteOperationBuilder.index(INDEX).id("2"))).build()));

        doBulk(bulkRequest);

        validateSpanContentAfterBulkRequest();
    }

    private void verifyMultiSearchTemplateSpanContent(Span span) {
        validateDbContextContent(span, "{\"index\":[\"my-index\"],\"types\":[],\"search_type\":\"query_then_fetch\"}\n" +
            "{\"source\":\"{  \\\"query\\\": { \\\"term\\\" : { \\\"{{field}}\\\" : \\\"{{value}}\\\" } },  \\\"size\\\" : \\\"{{size}}\\\"}\",\"params\":{\"field\":\"foo\",\"size\":5,\"value\":\"bar\"},\"explain\":false,\"profile\":false}\n");
    }

    private void verifyMultiSearchSpanContent(Span span) {
        validateDbContextContent(span, "{\"index\":[\"my-index\"],\"types\":[],\"search_type\":\"query_then_fetch\"}\n" +
            "{\"query\":{\"match\":{\"foo\":{\"query\":\"bar\",\"operator\":\"OR\",\"prefix_length\":0,\"max_expansions\":50,\"fuzzy_transpositions\":true,\"lenient\":false,\"zero_terms_query\":\"NONE\",\"auto_generate_synonyms_phrase_query\":true,\"boost\":1.0}}}}\n");
    }

    private void verifyTotalHits(HitsMetadata hitsMetadata) {
        assertThat(hitsMetadata.total().value()).isEqualTo(1L);
        assertThat(((Map) ((Hit) (hitsMetadata.hits().get(0))).source()).get(FOO)).isEqualTo(BAR);
    }

//    protected interface ClientMethod<Req, Res> {
//        void invoke(Req request, RequestOptions options, ActionListener<Res> listener);
//    }

//    protected <Req, Res> Res invokeAsync(Req request, AbstractEs6_4ClientInstrumentationTest.ClientMethod<Req, Res> method) throws InterruptedException, ExecutionException {
//        final CompletableFuture<Res> resultFuture = new CompletableFuture<>();
//        method.invoke(request, RequestOptions.DEFAULT, new ActionListener<>() {
//            @Override
//            public void onResponse(Res response) {
//                resultFuture.complete(response);
//            }
//
//            @Override
//            public void onFailure(Exception e) {
//                resultFuture.completeExceptionally(e);
//            }
//        });
//        return resultFuture.get();
//    }

    private CreateResponse doCreateIndex(CreateRequest createRequest) throws IOException, ExecutionException, InterruptedException {
        return client.indices().create(createRequest);
    }

    private DeleteResponse doDeleteIndex(DeleteRequest deleteIndexRequest) throws IOException, ExecutionException, InterruptedException {
        return client.indices().delete(deleteIndexRequest);
    }

    private IndexResponse doIndexDocument(IndexRequest indexRequest) throws ExecutionException, InterruptedException, IOException {
        return client.index(indexRequest);
    }

    private SearchResponse doSearch(SearchRequest searchRequest, Class responseClass) throws IOException, ExecutionException, InterruptedException {
        return client.search(searchRequest, responseClass);
    }

    private SearchTemplateResponse doSearchTemplate(SearchTemplateRequest searchRequest, Class responseClass) throws IOException, ExecutionException, InterruptedException {
        return client.searchTemplate(searchRequest, responseClass);
    }

    private MsearchTemplateResponse doMultiSearchTemplate(MsearchTemplateRequest searchRequest, Class responseClass) throws IOException, ExecutionException, InterruptedException {
//        if (async) {
//            AbstractEs6_4ClientInstrumentationTest.ClientMethod<MultiSearchTemplateRequest, MultiSearchTemplateResponse> method =
//                (request, options, listener) -> client.msearchTemplateAsync(request, options, listener);
//            return invokeAsync(searchRequest, method);
//        }
        return client.msearchTemplate(searchRequest, responseClass);
    }

    private RollupSearchResponse doRollupSearch(RollupSearchRequest rollupSearchRequest, Class responseClass) throws IOException, ExecutionException, InterruptedException {
        return client.rollup().rollupSearch(rollupSearchRequest, responseClass);
    }

    private MsearchResponse doMultiSearch(MsearchRequest searchRequest, Class responseClass) throws IOException, ExecutionException, InterruptedException {
//        if (async) {
//            AbstractEs6_4ClientInstrumentationTest.ClientMethod<MultiSearchRequest, MultiSearchResponse> method =
//                (request, options, listener) -> client.msearchAsync(request, options, listener);
//            return invokeAsync(searchRequest, method);
//        }
        return client.msearch(searchRequest, responseClass);
    }

    private CountResponse doCount(CountRequest countRequest) throws IOException, ExecutionException, InterruptedException {
        return client.count(countRequest);
    }

    private UpdateResponse doUpdate(UpdateRequest updateRequest, Class updateClass) throws IOException, ExecutionException, InterruptedException {
//        if (async) {
//            AbstractEs6_4ClientInstrumentationTest.ClientMethod<UpdateRequest, UpdateResponse> method =
//                (request, options, listener) -> client.updateAsync(request, options, listener);
//            return invokeAsync(updateRequest, method);
//        }
        return client.update(updateRequest, updateClass);
    }

    private co.elastic.clients.elasticsearch._core.DeleteResponse doDocumentDelete(co.elastic.clients.elasticsearch._core.DeleteRequest deleteRequest) throws IOException {
        return client.delete(deleteRequest);
    }

    private BulkResponse doBulk(BulkRequest bulkRequest) throws IOException, ExecutionException, InterruptedException {
//        if (async) {
//            AbstractEs6_4ClientInstrumentationTest.ClientMethod<BulkRequest, BulkResponse> method =
//                (request, options, listener) -> client.bulkAsync(request, options, listener);
//            return invokeAsync(bulkRequest, method);
//        }
        return client.bulk(bulkRequest);
    }

    private SearchRequest defaultSearchRequest() {
        return new SearchRequest(builder -> builder.index(INDEX)
            .query(new Query.Builder()
                .term(new TermQuery.Builder().field(FOO).value(BAR).build())
                .build())
            .from(0)
            .size(5)
        );
    }

    private void createMustachePutScriptAndSave() throws IOException {
        client.putScript(new PutScriptRequest(builder -> builder
            .id("elastic-search-template")
            .script(new StoredScript(scriptBuilder ->
                scriptBuilder.lang(ScriptLanguage.Mustache)
                    .source("{" +
                        "  \"query\": { \"term\" : { \"{{field}}\" : \"{{value}}\" } }," +
                        "  \"size\" : \"{{size}}\"" +
                        "}")
            ))));
    }

    private void createDocument() throws IOException, ExecutionException, InterruptedException {
        IndexResponse ir = doIndexDocument(new IndexRequest(new IndexRequest.Builder()
            .index(INDEX)
            .type(DOC_TYPE)
            .id(DOC_ID)
            .refresh(JsonValue.TRUE)
            .document(Map.of(FOO, BAR))));
        assertThat(ir).isNotNull();
        assertThat(ir.result().jsonValue()).isEqualTo("created");
        assertThat(ir.id()).isNotBlank();
        assertThat(ir.index()).isEqualTo(INDEX);
    }

    private co.elastic.clients.elasticsearch._core.DeleteResponse deleteDocument() throws InterruptedException, ExecutionException, IOException {
        return doDocumentDelete(new co.elastic.clients.elasticsearch._core.DeleteRequest(builder -> builder.index(INDEX).type(DOC_TYPE).id(DOC_ID)));
    }

    private SearchTemplateRequest prepareSearchTemplateRequest(String templateId) {
        Map<String, JsonData> scriptParams = new HashMap<>();
        scriptParams.put("field", JsonData.of(FOO));
        scriptParams.put("value", JsonData.of(BAR));
        scriptParams.put("size", JsonData.of(5));
        SearchTemplateRequest searchTemplateRequest = new SearchTemplateRequest(builder -> builder
            .index(INDEX)
            .id(templateId)
            .params(scriptParams)
        );
        return searchTemplateRequest;
    }
}
