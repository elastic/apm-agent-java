package co.elastic.apm.agent.webflux.client;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import org.eclipse.jetty.client.HttpClient;
import org.springframework.http.client.reactive.JettyClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;


public class WebClientRetrieveInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private final WebClient webClient;

    public WebClientRetrieveInstrumentationTest() {
        HttpClient httpClient = new HttpClient();

        webClient = WebClient.builder()
            .clientConnector(new JettyClientHttpConnector(httpClient))
            .build();
    }

    @Override
    protected void performGet(String path) throws Exception {
        String response = this.webClient.get()
            .uri(path)
            .retrieve()
            .bodyToMono(String.class)
            .block();
    }
}
