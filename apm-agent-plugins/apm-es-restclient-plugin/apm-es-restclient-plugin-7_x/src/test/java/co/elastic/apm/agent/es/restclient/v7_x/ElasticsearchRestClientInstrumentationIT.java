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
package co.elastic.apm.agent.es.restclient.v7_x;

import co.elastic.apm.agent.es.restclient.v6_4.AbstractEs6_4ClientInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Span;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
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
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Cancellable;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.script.mustache.MultiSearchTemplateRequest;
import org.elasticsearch.script.mustache.MultiSearchTemplateResponse;
import org.elasticsearch.script.mustache.SearchTemplateRequest;
import org.elasticsearch.script.mustache.SearchTemplateResponse;
import org.elasticsearch.search.SearchHits;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class ElasticsearchRestClientInstrumentationIT extends AbstractEs6_4ClientInstrumentationTest {
    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchRestClientInstrumentationIT.class);

    private static final String ELASTICSEARCH_CONTAINER_VERSION = "docker.elastic.co/elasticsearch/elasticsearch:7.11.0";

    public ElasticsearchRestClientInstrumentationIT(boolean async) { this.async = async; }

    @BeforeClass
    public static void startElasticsearchContainerAndClient() throws IOException {
        startContainer(ELASTICSEARCH_CONTAINER_VERSION);

        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(USER_NAME, PASSWORD));

        RestClientBuilder builder = RestClient.builder(HttpHost.create(container.getHttpHostAddress()))
            .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));

        client = new RestHighLevelClient(builder);

        client.indices().create(new CreateIndexRequest(INDEX), RequestOptions.DEFAULT);
        reporter.reset();
    }

    @AfterClass
    public static void stopElasticsearchContainerAndClient() throws IOException {
        if (client != null) {
            // prevent misleading NPE when failed to start container
            client.indices().delete(new DeleteIndexRequest(INDEX), RequestOptions.DEFAULT);
            client.close();
        }
    }

    @Test
    public void testCancelScenario() throws InterruptedException, ExecutionException, IOException {
        // When spans are cancelled, we can't know the actual address, because there is no response, and we set the outcome as UNKNOWN
        reporter.disableDestinationAddressCheck();
        reporter.checkUnknownOutcome(false);
        super.disableHttpUrlCheck();

        createDocument();
        reporter.reset();

        SearchRequest searchRequest = defaultSearchRequest();

        Cancellable cancellable = client.searchAsync(searchRequest, RequestOptions.DEFAULT, new ActionListener<SearchResponse>() {
            @Override
            public void onResponse(SearchResponse searchResponse) {
                throw new IllegalStateException("This should not be called, ofFailure should be called by cancel first");
            }

            @Override
            public void onFailure(Exception e) {
                // nothing to do - we wrap this listener and synchronously end the span
            }
        });
        // This ends the span synchronously
        cancellable.cancel();

        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        Span searchSpan = spans.get(0);
        validateSpanContent(searchSpan, String.format("Elasticsearch: POST /%s/_search", INDEX), -1, "POST");

        deleteDocument();

        reporter.enableDestinationAddressCheck();
        reporter.checkUnknownOutcome(true);
        super.enableHttpUrlCheck();
    }

    @Override
    protected void verifyMultiSearchTemplateSpanContent(Span span) {
        validateDbContextContent(span, "{\"index\":[\"my-index\"],\"types\":[],\"search_type\":\"query_then_fetch\",\"ccs_minimize_roundtrips\":true}\n" +
            "{\"source\":\"{  \\\"query\\\": { \\\"term\\\" : { \\\"{{field}}\\\" : \\\"{{value}}\\\" } },  \\\"size\\\" : \\\"{{size}}\\\"}\",\"params\":{\"field\":\"foo\",\"size\":5,\"value\":\"bar\"},\"explain\":false,\"profile\":false}\n");
    }

    @Override
    protected void verifyMultiSearchSpanContent(Span span) {
        validateDbContextContent(span, "{\"index\":[\"my-index\"],\"types\":[],\"search_type\":\"query_then_fetch\",\"ccs_minimize_roundtrips\":true}\n" +
            "{\"query\":{\"match\":{\"foo\":{\"query\":\"bar\",\"operator\":\"OR\",\"prefix_length\":0,\"max_expansions\":50,\"fuzzy_transpositions\":true,\"lenient\":false,\"zero_terms_query\":\"NONE\",\"auto_generate_synonyms_phrase_query\":true,\"boost\":1.0}}}}\n");
    }

    @Override
    protected void verifyTotalHits(SearchHits searchHits) {
        assertThat(searchHits.getTotalHits().value).isEqualTo(1L);
        assertThat(searchHits.getAt(0).getSourceAsMap().get(FOO)).isEqualTo(BAR);
    }

    @Override
    protected CreateIndexResponse doCreateIndex(CreateIndexRequest createIndexRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            ClientMethod<CreateIndexRequest, CreateIndexResponse> method =
                (request, options, listener) -> client.indices().createAsync(request, options, listener);
            return invokeAsync(createIndexRequest, method);
        }
        return client.indices().create(createIndexRequest, RequestOptions.DEFAULT);
    }

    @Override
    protected AcknowledgedResponse doDeleteIndex(DeleteIndexRequest deleteIndexRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            ClientMethod<DeleteIndexRequest, AcknowledgedResponse> method =
                (request, options, listener) -> client.indices().deleteAsync(request, options, listener);
            return invokeAsync(deleteIndexRequest, method);
        }
        return client.indices().delete(deleteIndexRequest, RequestOptions.DEFAULT);
    }

    @Override
    protected IndexResponse doIndex(IndexRequest indexRequest) throws ExecutionException, InterruptedException, IOException {
        if (async) {
            ClientMethod<IndexRequest, IndexResponse> method =
                (request, options, listener) -> client.indexAsync(request, options, listener);
            return invokeAsync(indexRequest, method);
        }
        return client.index(indexRequest, RequestOptions.DEFAULT);
    }

    @Override
    protected SearchResponse doSearch(SearchRequest searchRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            ClientMethod<SearchRequest, SearchResponse> method =
                (request, options, listener) -> client.searchAsync(request, options, listener);
            return invokeAsync(searchRequest, method);
        }
        return client.search(searchRequest, RequestOptions.DEFAULT);
    }

    @Override
    protected SearchTemplateResponse doSearchTemplate(SearchTemplateRequest searchRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            ClientMethod<SearchTemplateRequest, SearchTemplateResponse> method =
                (request, options, listener) -> client.searchTemplateAsync(request, options, listener);
            return invokeAsync(searchRequest, method);
        }
        return client.searchTemplate(searchRequest, RequestOptions.DEFAULT);
    }

    @Override
    protected MultiSearchTemplateResponse doMultiSearchTemplate(MultiSearchTemplateRequest searchRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            ClientMethod<MultiSearchTemplateRequest, MultiSearchTemplateResponse> method =
                (request, options, listener) -> client.msearchTemplateAsync(request, options, listener);
            return invokeAsync(searchRequest, method);
        }
        return client.msearchTemplate(searchRequest, RequestOptions.DEFAULT);
    }

    @Override
    protected SearchResponse doRollupSearch(SearchRequest searchRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            ClientMethod<SearchRequest, SearchResponse> method =
                (request, options, listener) -> client.rollup().searchAsync(request, options, listener);
            return invokeAsync(searchRequest, method);
        }
        return client.rollup().search(searchRequest, RequestOptions.DEFAULT);
    }

    @Override
    protected MultiSearchResponse doMultiSearch(MultiSearchRequest searchRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            ClientMethod<MultiSearchRequest, MultiSearchResponse> method =
                (request, options, listener) -> client.msearchAsync(request, options, listener);
            return invokeAsync(searchRequest, method);
        }
        return client.msearch(searchRequest, RequestOptions.DEFAULT);
    }

    @Override
    protected CountResponse doCount(CountRequest countRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            ClientMethod<CountRequest, CountResponse> method =
                (request, options, listener) -> client.countAsync(request, options, listener);
            return invokeAsync(countRequest, method);
        }
        return client.count(countRequest, RequestOptions.DEFAULT);
    }

    @Override
    protected UpdateResponse doUpdate(UpdateRequest updateRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            ClientMethod<UpdateRequest, UpdateResponse> method =
                (request, options, listener) -> client.updateAsync(request, options, listener);
            return invokeAsync(updateRequest, method);
        }
        return client.update(updateRequest, RequestOptions.DEFAULT);
    }

    @Override
    protected DeleteResponse doDelete(DeleteRequest deleteRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            ClientMethod<DeleteRequest, DeleteResponse> method =
                (request, options, listener) -> client.deleteAsync(request, options, listener);
            return invokeAsync(deleteRequest, method);
        }
        return client.delete(deleteRequest, RequestOptions.DEFAULT);
    }

    @Override
    protected BulkResponse doBulk(BulkRequest bulkRequest) throws IOException, ExecutionException, InterruptedException {
        if (async) {
            ClientMethod<BulkRequest, BulkResponse> method =
                (request, options, listener) -> client.bulkAsync(request, options, listener);
            return invokeAsync(bulkRequest, method);
        }
        return client.bulk(bulkRequest, RequestOptions.DEFAULT);
    }
}
