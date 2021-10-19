package co.elastic.apm.spring.boot.webflux;

import org.springframework.web.reactive.function.client.WebClient;

public class WebClientFactory {

    public static WebClient webClient(int port) {
        return WebClient.builder()
            .baseUrl("http://localhost:" + port)
            .build();
    }
}
