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
package co.elastic.apm.agent.springwebflux.testapp;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.Id;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.tracer.GlobalTracer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Optional;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.method;
import static org.springframework.web.reactive.function.server.RequestPredicates.path;

/**
 * Provides functional Webflux endpoint
 */
@Configuration
public class GreetingFunctional {

    @Bean
    public RouterFunction<ServerResponse> route(GreetingHandler greetingHandler) {

        return RouterFunctions.route()
            // 'hello' and 'hello2' are identical, but entry point in builder is not
            .route(path("/functional/hello"),
                request -> helloGreeting(greetingHandler, request.queryParam("name")))
            .route(path("/functional/preauthorized"),
                request -> helloGreeting(greetingHandler, Optional.of("elastic")))
            .route(path("/functional/username"),
                request -> getUsernameFromContext(greetingHandler))
            .route(path("/functional/path-username"),
                request -> getUsernameFromContext(greetingHandler))
            //
            .GET("/functional/hello2", accept(MediaType.TEXT_PLAIN),
                request -> helloGreeting(greetingHandler, request.queryParam("name")))
            // nested routes
            .nest(path("/functional/nested"), builder -> builder
                .route(method(HttpMethod.GET), request -> nested(greetingHandler, request.methodName()))
                .route(method(HttpMethod.POST), request -> nested(greetingHandler, request.methodName()))
            )
            // path with parameters
            .route(path("/functional/with-parameters/{id}"),
                request -> helloGreeting(greetingHandler, Optional.of(request.pathVariable("id"))))
            // route that supports multiple methods mapping
            .route(path("/functional/hello-mapping"),
                request -> helloGreeting(greetingHandler, Optional.of(request.methodName())))
            // errors and mono corner cases
            .GET("/functional/error-handler", accept(MediaType.TEXT_PLAIN), request -> greetingHandler.throwException())
            .GET("/functional/error-mono", accept(MediaType.TEXT_PLAIN), request -> greetingHandler.monoError())
            .GET("/functional/empty-mono", accept(MediaType.TEXT_PLAIN), request -> greetingHandler.monoEmpty())
            // with known transaction duration
            .GET("/functional/duration", accept(MediaType.TEXT_PLAIN), request -> response(greetingHandler.duration(getDuration(request))))
            // custom transaction name set through API
            .GET("/functional/custom-transaction-name", accept(MediaType.TEXT_PLAIN), request -> {
                Id transactionId = null;
                if (!GlobalTracer.isNoop()) {
                    ElasticApmTracer tracer = GlobalTracer.get().require(ElasticApmTracer.class);
                    Transaction transaction = Objects.requireNonNull(tracer.currentTransaction(), "active transaction is required");
                    // This mimics setting the name through the public API. We cannot use the public API if we want to test span recycling
                    transaction.withName("user-provided-name", AbstractSpan.PRIORITY_USER_SUPPLIED);
                    transactionId = transaction.getTraceContext().getId();
                }
                return response(greetingHandler.helloMessage("transaction=" + transactionId));
            })
            .GET("/functional/child-flux", accept(MediaType.TEXT_PLAIN), request -> ServerResponse.ok()
                .body(greetingHandler.childSpans(getCount(request), getDelay(request), getDuration(request)), String.class
                ))
            .route(path("/functional/child-flux/sse"),
                request -> ServerResponse.ok()
                    .body(greetingHandler.childSpans(getCount(request), getDelay(request), getDuration(request))
                        .map(greetingHandler::toSSE), ServerSentEvent.class
                    ))
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

    private Long getDuration(ServerRequest request) {
        return request.queryParam("duration").map(Long::parseLong).orElse(0L);
    }

    private int getCount(ServerRequest request) {
        return request.queryParam("count").map(Integer::parseInt).orElse(1);
    }

    private Long getDelay(ServerRequest request) {
        return request.queryParam("delay").map(Long::parseLong).orElse(0L);
    }

    private Mono<ServerResponse> nested(GreetingHandler greetingHandler, String methodName) {
        return helloGreeting(greetingHandler, Optional.of("nested " + methodName));
    }

    private Mono<ServerResponse> helloGreeting(GreetingHandler greetingHandler, Optional<String> name) {
        return response(greetingHandler.helloMessage(name.orElse(null)));
    }

    private Mono<ServerResponse> getUsernameFromContext(GreetingHandler greetingHandler) {
        return response(greetingHandler.getUsernameFromContext());
    }

    private Mono<ServerResponse> response(Mono<String> value) {
        return value.flatMap(s -> ServerResponse.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .bodyValue(s));
    }
}
