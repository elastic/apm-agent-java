package co.elastic.apm.esjavaclient;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;

@RunWith(Parameterized.class)
public class ElasticsearchJavaIT extends AbstractElasticsearchJavaTest {

    private static final String ELASTICSEARCH_CONTAINER_VERSION = "docker.elastic.co/elasticsearch/elasticsearch:7.17.1";

    public ElasticsearchJavaIT(boolean async) {
        this.async = async;
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
}
