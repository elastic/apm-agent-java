package co.elastic.apm.agent.webflux.client;

import org.springframework.web.reactive.function.client.ClientResponse;


public class WebClientExchangeFunctionInstrumentationTest extends AbstractWebClientInstrumentationTest {

    public WebClientExchangeFunctionInstrumentationTest() {
        super();
    }

    @Override
    protected void performGet(String path) throws Exception {
        ClientResponse response = this.webClient.get()
            .uri(path)
            .exchange()
            .block();
    }
}
