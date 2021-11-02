package co.elastic.apm.agent.webflux.client;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;


public class WebClientExchangeFunctionInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private final WebClient webClient;

    public WebClientExchangeFunctionInstrumentationTest() {
        HttpClient httpClient = HttpClient.create().followRedirect(true);

        webClient = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
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
