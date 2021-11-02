package co.elastic.apm.agent.webflux.client;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;


public class WebClientRetrieveInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private final WebClient webClient;

    public WebClientRetrieveInstrumentationTest() {
        HttpClient httpClient = HttpClient.create().followRedirect(true);

        webClient = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }

    @Override
    protected void performGet(String path) throws Exception {
        this.webClient.get()
            .uri(path)
            .retrieve()
            .bodyToMono(String.class)
            .block();
    }
}
