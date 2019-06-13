package co.elastic.apm.agent.es.restclient.v7_1;

import co.elastic.apm.agent.es.restclient.v6_4.AbstractEs6_4ClientInstrumentationTest;
import org.apache.http.HttpHost;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.Request;
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

    @Override
    protected void verifyTotalHits(SearchHits searchHits) {
        assertThat(searchHits.getTotalHits().value).isEqualTo(1L);
        assertThat(searchHits.getAt(0).getSourceAsMap().get(FOO)).isEqualTo(BAR);
    }

}
