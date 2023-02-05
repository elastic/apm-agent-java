/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.agent.springwebclient;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.springframework.http.client.reactive.HttpComponentsClientHttpConnector;
import org.springframework.http.client.reactive.JettyClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@RunWith(Parameterized.class)
public class WebClientInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private final WebClient webClient;

    private final RequestStrategy strategy;

    private final boolean isNetty;

    public WebClientInstrumentationTest(String clientIgnored, WebClient webClient, RequestStrategy strategy, boolean isNetty) {
        this.webClient = webClient;
        this.strategy = strategy;
        this.isNetty = isNetty;
    }

    @Parameterized.Parameters(name = "client = {0}, request strategy = {2}")
    public static Object[][] testParams() {
        return new Object[][]{
            {"jetty", jettyClient(), RequestStrategy.EXCHANGE, false},
            {"jetty", jettyClient(), RequestStrategy.EXCHANGE_TO_FLUX, false},
            {"jetty", jettyClient(), RequestStrategy.EXCHANGE_TO_MONO, false},
            {"jetty", jettyClient(), RequestStrategy.RETRIEVE, false},
            {"netty", nettyClient(), RequestStrategy.EXCHANGE, true},
            {"netty", nettyClient(), RequestStrategy.EXCHANGE_TO_FLUX, true},
            {"netty", nettyClient(), RequestStrategy.EXCHANGE_TO_MONO, true},
            {"netty", nettyClient(), RequestStrategy.RETRIEVE, true},
            {"hc5", reactiveHttpClient5(), RequestStrategy.EXCHANGE, false},
            {"hc5", reactiveHttpClient5(), RequestStrategy.EXCHANGE_TO_FLUX, false},
            {"hc5", reactiveHttpClient5(), RequestStrategy.EXCHANGE_TO_MONO, false},
            {"hc5", reactiveHttpClient5(), RequestStrategy.RETRIEVE, false}
        };
    }

    @Override
    public boolean isRequireCheckErrorWhenCircularRedirect() {
        // circular redirect does not trigger an error to capture with netty
        return !isNetty;
    }

    @Override
    public boolean isTestHttpCallWithUserInfoEnabled() {
        // user info URI does not work with netty
        return !isNetty;
    }


    @Override
    protected void performGet(String path) throws Exception {
        strategy.execute(webClient, path);
    }

    /**
     * Client-side API variants to cover. While we know that implementation details might delegate to the same method
     * we have to test for it to prevent regression in a future version
     */
    protected enum RequestStrategy {
        EXCHANGE {
            @Override
            @SuppressWarnings("deprecation")
            void execute(WebClient client, String uri) {
                client.get().uri(uri).exchange() // deprecated API
                    .block();
            }
        },
        EXCHANGE_TO_FLUX {
            @Override
            void execute(WebClient client, String uri) {
                client.get().uri(uri).exchangeToFlux(response -> response.bodyToFlux(String.class)).blockLast();
            }
        },
        EXCHANGE_TO_MONO {
            // TODO
            @Override
            void execute(WebClient client, String uri) {
                client.get().uri(uri).exchangeToMono(response -> response.bodyToMono(String.class)).block();
            }
        },
        RETRIEVE {
            @Override
            void execute(WebClient client, String uri) {
                client.get().uri(uri).retrieve().bodyToMono(String.class).block();
            }
        };

        abstract void execute(WebClient client, String uri);
    }

    private static WebClient jettyClient() {
        return WebClient.builder()
            .clientConnector(new JettyClientHttpConnector())
            .build();
    }

    private static WebClient nettyClient() {
        HttpClient httpClient = HttpClient.create()
            // followRedirect(boolean) only enables redirect for 30[1278], not 303
            .followRedirect((req, res) -> res.status().code() == 303);

        // crete netty reactor client
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }

    public static WebClient reactiveHttpClient5() {
        return WebClient.builder()
            .clientConnector(new HttpComponentsClientHttpConnector())
            .build();
    }

}
