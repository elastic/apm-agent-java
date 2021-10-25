package co.elastic.apm.esjavaclient;

import co.elastic.clients.base.RestClientTransport;
import co.elastic.clients.base.Transport;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateRequest;
import co.elastic.clients.elasticsearch.indices.DeleteRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.io.IOException;

public class ElasticsearchJavaIT extends AbstractElasticsearchJavaTest {

    private static final String ELASTICSEARCH_CONTAINER_VERSION = "docker.elastic.co/elasticsearch/elasticsearch:7.12.1";

    @BeforeClass
    public static void startElasticsearchContainerAndClient() throws IOException {
        startContainer(ELASTICSEARCH_CONTAINER_VERSION);

        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(USER_NAME, PASSWORD));

        RestClient restClient = RestClient.builder(HttpHost.create(container.getHttpHostAddress()))
            .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
            .build();

        Transport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        client = new ElasticsearchClient(transport);

        client.indices().create(new CreateRequest(builder -> builder.index(INDEX)));
        reporter.reset();
    }

    @AfterClass
    public static void stopElasticsearchContainerAndClient() throws IOException {
        if (client != null) {
            // prevent misleading NPE when failed to start container
            client.indices().delete(new DeleteRequest(builder -> builder.index(INDEX)));
        }
    }
}
