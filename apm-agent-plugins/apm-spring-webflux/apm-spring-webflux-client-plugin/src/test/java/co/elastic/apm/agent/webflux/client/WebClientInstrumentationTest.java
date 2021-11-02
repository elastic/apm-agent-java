package co.elastic.apm.agent.webflux.client;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import org.eclipse.jetty.client.HttpClient;
import org.springframework.http.client.reactive.JettyClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;


public class WebClientInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private final WebClient webClient;

    public WebClientInstrumentationTest() {
        HttpClient httpClient = new HttpClient();

        webClient =  WebClient.builder()
            .clientConnector(new JettyClientHttpConnector())
            .build();
    }

    @Override
    protected void performGet(String path) throws Exception {
        ClientResponse response = this.webClient.get()
            .uri(path)
            .exchange()
            .block();
    }
}
