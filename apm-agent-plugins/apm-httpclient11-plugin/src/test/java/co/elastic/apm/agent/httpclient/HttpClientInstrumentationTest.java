package co.elastic.apm.agent.httpclient;

import org.junit.Before;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class HttpClientInstrumentationTest extends AbstractHttpClientInstrumentationTest {
    private HttpClient client;

    @Before
    public void setUp() {
        client = HttpClient.newHttpClient();
    }

    @Override
    protected void performGet(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(path))
            .build();
        client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Override
    protected boolean isIpv6Supported() {
        return true;
    }
}
