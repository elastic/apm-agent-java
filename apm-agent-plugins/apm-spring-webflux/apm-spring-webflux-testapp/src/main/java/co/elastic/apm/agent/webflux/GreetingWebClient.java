/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.webflux;

import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public class GreetingWebClient {

	private final WebClient client;

    public GreetingWebClient(String host, int port) {
        this.client = WebClient.create(String.format("http://%s:%d/", host, port));
    }

    public String getHelloMono() {
        Mono<ClientResponse> response = client.get()
            .uri("/hello")
            .accept(MediaType.TEXT_PLAIN)
            .exchange();

        // exchange or retrieve ?

        return response.flatMap(res -> res.bodyToMono(String.class))
            .block();
    }

    public String get404(){
        Mono<ClientResponse> response = client.get()
            .uri("/error-404")
            .accept(MediaType.TEXT_PLAIN)
            .exchange();

        return response.flatMap(res -> res.bodyToMono(String.class))
            .block();
    }
}
