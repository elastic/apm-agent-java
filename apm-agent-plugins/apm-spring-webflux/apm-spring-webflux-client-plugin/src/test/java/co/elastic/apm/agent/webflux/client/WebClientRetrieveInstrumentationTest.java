package co.elastic.apm.agent.webflux.client;

public class WebClientRetrieveInstrumentationTest extends AbstractWebClientInstrumentationTest {

    public WebClientRetrieveInstrumentationTest() {
        super();
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
