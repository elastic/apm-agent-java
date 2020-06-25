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
package co.elastic.apm.agent.spring.webflux.testapp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.method;
import static org.springframework.web.reactive.function.server.RequestPredicates.path;

@Configuration
public class GreetingRouter {

    @Bean
    public RouterFunction<ServerResponse> route(GreetingHandler greetingHandler) {

        return RouterFunctions.route()
            // 'hello' and 'hello2' are identical, but entry point in builder is not
            .route(path("/router/hello"),
                request -> helloGreeting(greetingHandler, request.queryParam("name")))
            .GET("/router/hello2", accept(MediaType.TEXT_PLAIN),
                request -> helloGreeting(greetingHandler, request.queryParam("name")))
            // nested routes
            .nest(path("/router/nested"), builder -> builder
                .route(method(HttpMethod.GET), request -> nested(greetingHandler, request.methodName()))
                .route(method(HttpMethod.POST), request -> nested(greetingHandler, request.methodName()))
            )
            // path with parameters
            .route(path("/router/with-parameters/{id}"),
                request -> helloGreeting(greetingHandler, Optional.of(request.pathVariable("id"))))
            // route that supports multiple methods mapping
            .route(path("/router/hello-mapping"),
                request -> helloGreeting(greetingHandler, Optional.of(request.methodName())))
            // errors and mono corner cases
            .GET("/router/error-handler", accept(MediaType.TEXT_PLAIN), request -> greetingHandler.throwException())
            .GET("/router/error-mono", accept(MediaType.TEXT_PLAIN), request -> greetingHandler.monoError())
            .GET("/router/empty-mono", accept(MediaType.TEXT_PLAIN), request -> greetingHandler.monoEmpty())
            // error handler
            .onError(
                e -> true, (e, request) -> ServerResponse
                    .status(request.queryParam("status")
                        .map(Integer::parseInt)
                        .orElse(500))
                    .bodyValue(greetingHandler.exceptionMessage(e))
            )
            .build();
    }

    private Mono<ServerResponse> nested(GreetingHandler greetingHandler, String methodName) {
        return helloGreeting(greetingHandler, Optional.of("nested " + methodName));
    }

    private Mono<ServerResponse> helloGreeting(GreetingHandler greetingHandler, Optional<String> name) {
        return ServerResponse.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .body(BodyInserters.fromValue(greetingHandler.helloMessage(name).block()));
    }
}
