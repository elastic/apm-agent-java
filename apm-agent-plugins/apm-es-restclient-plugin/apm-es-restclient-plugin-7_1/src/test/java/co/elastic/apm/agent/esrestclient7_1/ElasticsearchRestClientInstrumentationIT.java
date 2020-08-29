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
package co.elastic.apm.agent.esrestclient7_1;

import co.elastic.apm.agent.esrestclient6_4.AbstractEs6_4ClientInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Span;
import org.apache.http.HttpHost;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHits;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class ElasticsearchRestClientInstrumentationIT extends AbstractEs6_4ClientInstrumentationTest {

    private static final String ELASTICSEARCH_CONTAINER_VERSION = "docker.elastic.co/elasticsearch/elasticsearch:7.1.0";

    public ElasticsearchRestClientInstrumentationIT(boolean async) { this.async = async; }

    @BeforeClass
    public static void startElasticsearchContainerAndClient() throws IOException {
        container = new ElasticsearchContainer(ELASTICSEARCH_CONTAINER_VERSION);
        container.start();

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
        client.indices().delete(new DeleteIndexRequest(INDEX), RequestOptions.DEFAULT);
        container.stop();
        client.close();
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

}
