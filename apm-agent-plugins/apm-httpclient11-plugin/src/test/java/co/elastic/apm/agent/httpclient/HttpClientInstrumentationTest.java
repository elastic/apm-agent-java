package co.elastic.apm.agent.httpclient;

import org.junit.Before;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpClientInstrumentationTest extends AbstractHttpClientInstrumentationTest {
    private HttpClient client;

    @Before
    public void setUp() {
        client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
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

    @Override
    public void assertCircularRedirect() {
        assertThat(reporter.getSpans().get(0).getContext().getHttp().getStatusCode()).isEqualTo(303);
    }
}
