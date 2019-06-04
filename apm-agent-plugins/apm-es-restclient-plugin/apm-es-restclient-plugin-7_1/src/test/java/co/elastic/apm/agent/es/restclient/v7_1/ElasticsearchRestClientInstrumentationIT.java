package co.elastic.apm.agent.es.restclient.v7_1;

import co.elastic.apm.agent.es.restclient.AbstractEsClientInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Span;
import org.apache.http.HttpHost;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;

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

    private static RestHighLevelClient client;

    public ElasticsearchRestClientInstrumentationIT(boolean async) { this.async = async; }

    @BeforeClass
    public static void startElasticsearchContainerAndClient() throws IOException {
        container = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:7.1.0");
        container.start();

        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(USER_NAME, PASSWORD));

        RestClientBuilder builder = RestClient.builder(HttpHost.create(container.getHttpHostAddress()))
            .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));

        lowLevelClient = builder.build();
        client = new RestHighLevelClient(builder);

        Request createIndexRequest = new Request("PUT", "/" + INDEX);
        lowLevelClient.performRequest(createIndexRequest);
        reporter.reset();
    }

    @AfterClass
    public static void stopElasticsearchContainerAndClient() throws IOException {
        Request deleteIndexRequest = new Request("DELETE", "/" + INDEX);
        lowLevelClient.performRequest(deleteIndexRequest);
        container.stop();
        lowLevelClient.close();;
    }

    @Test
    public void testTryToDeleteNonExistingIndex() throws IOException {
        ElasticsearchStatusException ese = null;
        try {
            doDeleteIndex(new DeleteIndexRequest(SECOND_INDEX));
        } catch (ElasticsearchStatusException e) {
            // sync scenario
            ese = e;
        } catch (ExecutionException e) {
            // async scenario
            ese = (ElasticsearchStatusException) e.getCause();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertThat(ese).isNotNull();
        assertThat(ese.status().getStatus()).isEqualTo(404);

        assertThatErrorsExistWhenDeleteNonExistingIndex();
    }


    @Test
    public void testCreateAndDeleteIndex() throws InterruptedException, ExecutionException, IOException {
        doCreateIndex(new CreateIndexRequest(SECOND_INDEX));

        validateSpanContentAfterIndexCreateRequest();

        reporter.reset();

        doDeleteIndex(new DeleteIndexRequest(SECOND_INDEX));

        validateSpanContentAfterIndexDeleteRequest();
    }

    @Test
    public void testDocumentScenario() throws IOException, ExecutionException, InterruptedException {
        IndexResponse ir = doIndex(new IndexRequest(INDEX, DOC_TYPE, DOC_ID).source(
            jsonBuilder()
            .startObject()
            .field(FOO, BAR)
            .endObject()
        ).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE));
        assertThat(ir.status().getStatus()).isEqualTo(201);

        System.out.println(reporter.generateTransactionPayloadJson());

        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        validateSpanContent(spans.get(0), String.format("Elasticsearch: PUT /%s/%s/%s", INDEX, DOC_TYPE, DOC_ID), 201, "PUT");

        reporter.reset();;

        SearchRequest searchRequest = new SearchRequest(INDEX);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.termQuery(FOO, BAR));
        sourceBuilder.from(0);
        sourceBuilder.size(5);
        searchRequest.source(sourceBuilder);
        SearchResponse sr = doSearch(searchRequest);
        assertThat(sr.getHits().getTotalHits().value).isEqualTo(1L);
        assertThat(sr.getHits().getAt(0).getSourceAsMap().get(FOO)).isEqualTo(BAR);

        System.out.println(reporter.generateTransactionPayloadJson());

        spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        Span searchSpan = spans.get(0);
        validateSpanContent(searchSpan, String.format("Elasticsearch: POST /%s/_search", INDEX), 200, "POST");
        validateDbContextContent(searchSpan, "{\"from\":0,\"size\":5,\"query\":{\"term\":{\"foo\":{\"value\":\"bar\",\"boost\":1.0}}}}");

        // Now update and re-search
        reporter.reset();

        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put(FOO, BAZ);
        UpdateRequest updateRequest = new UpdateRequest(INDEX, DOC_TYPE, DOC_ID).doc(jsonMap).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        UpdateResponse ur = doUpdate(updateRequest);
        assertThat(ur.status().getStatus()).isEqualTo(200);
        sr = doSearch(new SearchRequest(INDEX));
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
        DeleteResponse dr = doDelete(new DeleteRequest(INDEX, DOC_TYPE, DOC_ID));
        assertThat(dr.status().getStatus()).isEqualTo(200);
        validateSpanContent(spans.get(0), String.format("Elasticsearch: DELETE /%s/%s/%s", INDEX, DOC_TYPE, DOC_ID), 200, "DELETE");

        System.out.println(reporter.generateTransactionPayloadJson());
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
        void invoke(Req request, RequestOptions options, ActionListener<Res> listener);
    }

    private <Req, Res> Res invokeAsync(Req request, ClientMethod<Req, Res> method) throws ExecutionException, InterruptedException {
        final CompletableFuture<Res> resultFuture = new CompletableFuture<>();
        method.invoke(request, RequestOptions.DEFAULT, new ActionListener<Res>() {
            @Override
            public void onResponse(Res res) {
                resultFuture.complete(res);
            }

            @Override
            public void onFailure(Exception e) {
                resultFuture.completeExceptionally(e);
            }
        });
        return resultFuture.get();
    }

    private CreateIndexResponse doCreateIndex(CreateIndexRequest createIndexRequest) throws ExecutionException, InterruptedException, IOException {
        if (async) {
            ClientMethod<CreateIndexRequest, CreateIndexResponse> method =
                (request, options, listener) -> client.indices().createAsync(request, options, listener);
            return invokeAsync(createIndexRequest, method);
        }
        return client.indices().create(createIndexRequest, RequestOptions.DEFAULT);
    }

    private AcknowledgedResponse doDeleteIndex(DeleteIndexRequest deleteIndexRequest) throws ExecutionException, InterruptedException, IOException {
        if (async) {
            ClientMethod<DeleteIndexRequest, AcknowledgedResponse> method =
                (request, options, listener) -> client.indices().deleteAsync(request, options, listener);
            return invokeAsync(deleteIndexRequest, method);
        }
        return client.indices().delete(deleteIndexRequest, RequestOptions.DEFAULT);
    }

    private IndexResponse doIndex(IndexRequest indexRequest) throws ExecutionException, InterruptedException, IOException {
        if (async) {
            ClientMethod<IndexRequest, IndexResponse> method =
                (request, options, listener) -> client.indexAsync(request, options, listener);
            return invokeAsync(indexRequest, method);
        }
        return client.index(indexRequest, RequestOptions.DEFAULT);
    }

    private SearchResponse doSearch(SearchRequest searchRequest) throws ExecutionException, InterruptedException, IOException {
        if (async) {
            ClientMethod<SearchRequest, SearchResponse> method =
                (request, options, listener) -> client.searchAsync(request, options, listener);
            return invokeAsync(searchRequest, method);
        }
        return client.search(searchRequest, RequestOptions.DEFAULT);
    }

    private UpdateResponse doUpdate(UpdateRequest updateRequest) throws ExecutionException, InterruptedException, IOException {
        if (async) {
            ClientMethod<UpdateRequest, UpdateResponse> method =
                (request, options, listener) -> client.updateAsync(request, options, listener);
            return invokeAsync(updateRequest, method);
        }
        return client.update(updateRequest, RequestOptions.DEFAULT);
    }

    private DeleteResponse doDelete(DeleteRequest deleteRequest) throws ExecutionException, InterruptedException, IOException {
        if (async) {
            ClientMethod<DeleteRequest, DeleteResponse> method =
                (request, options, listener) -> client.deleteAsync(request, options, listener);
            return invokeAsync(deleteRequest, method);
        }
        return client.delete(deleteRequest, RequestOptions.DEFAULT);
    }

    private BulkResponse doBulk(BulkRequest bulkRequest) throws ExecutionException, InterruptedException, IOException {
        if (async) {
            ClientMethod<BulkRequest, BulkResponse> method =
                ((request, options, listener) -> client.bulkAsync(request, options, listener));
            return invokeAsync(bulkRequest, method);
        }
        return client.bulk(bulkRequest, RequestOptions.DEFAULT);
    }
}
