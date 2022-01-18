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
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Provides Webflux annotated endpoint
 */
@RestController
@RequestMapping(value = "/annotated", produces = MediaType.TEXT_PLAIN_VALUE)
public class GreetingAnnotated {

    private static final Logger log = LoggerFactory.getLogger(GreetingAnnotated.class);

    final GreetingHandler greetingHandler;

    @Autowired
    public GreetingAnnotated(GreetingHandler greetingHandler) {
        this.greetingHandler = greetingHandler;
    }

    @RequestMapping("/hello")
    public Mono<String> getHello(@RequestParam(value = "name", required = false) @Nullable String name) {
        return greetingHandler.helloMessage(name);
    }

    @RequestMapping("/error-handler")
    public Mono<String> handlerError() {
        // using delayed exception here allows to ensure that the exception handler is properly
        // executed, as its execution is part of the "dispatch" phase and is outside of "handler"
        //
        // this does not apply for functional definitions as the exception handler is directly part of the "handler"
        // which is wrapped into the "dispatcher" (which we instrument).
        return greetingHandler.delayedException();
    }

    @RequestMapping("/error-mono")
    public Mono<String> monoError() {
        return greetingHandler.monoError();
    }

    @RequestMapping("/empty-mono")
    public Mono<String> monoEmpty() {
        return greetingHandler.monoEmpty();
    }

    @ExceptionHandler
    public ResponseEntity<String> handleException(RuntimeException e) {
        return ResponseEntity.status(500)
            .body(greetingHandler.exceptionMessage(e));
    }

    @GetMapping("/hello-mapping")
    public Mono<String> getMapping() {
        return greetingHandler.helloMessage("GET");
    }

    @PostMapping("/hello-mapping")
    public Mono<String> postMapping() {
        return greetingHandler.helloMessage("POST");
    }

    @PutMapping("/hello-mapping")
    public Mono<String> putMapping() {
        return greetingHandler.helloMessage("PUT");
    }

    @DeleteMapping("/hello-mapping")
    public Mono<String> deleteMapping() {
        return greetingHandler.helloMessage("DELETE");
    }

    @PatchMapping("/hello-mapping")
    public Mono<String> patchMapping() {
        return greetingHandler.helloMessage("PATCH");
    }

    @RequestMapping(path = "/hello-mapping", method = {RequestMethod.HEAD, RequestMethod.OPTIONS, RequestMethod.TRACE})
    public Mono<String> otherMapping(ServerHttpRequest request) {
        return greetingHandler.helloMessage(request.getMethodValue());
    }

    @GetMapping("/with-parameters/{id}")
    public Mono<String> withParameters(@PathVariable("id") String id) {
        return greetingHandler.helloMessage(id);
    }

    @GetMapping(path = "/child-flux")
    public Flux<String> getChildSpans(@RequestParam(value = "count", required = false, defaultValue = "3") int count,
                                      @RequestParam(value = "duration", required = false, defaultValue = "5") long durationMillis,
                                      @RequestParam(value = "delay", required = false, defaultValue = "5") long delayMillis) {

        return greetingHandler.childSpans(count, delayMillis, durationMillis);
    }

    @GetMapping(path = "/child-flux/sse")
    public Flux<ServerSentEvent<String>> getChildSpansSSE(@RequestParam(value = "count", required = false, defaultValue = "3") int count,
                                                          @RequestParam(value = "duration", required = false, defaultValue = "5") long durationMillis,
                                                          @RequestParam(value = "delay", required = false, defaultValue = "5") long delayMillis) {

        return greetingHandler.childSpans(count, durationMillis, delayMillis)
            .map(greetingHandler::toSSE);
    }

    @GetMapping("/custom-transaction-name")
    public Mono<String> customTransactionName() {
        log.debug("enter customTransactionName");
        try {
            // Transaction should be active, even if we are outside of Mono/Flux execution
            // In practice, it's called after onSubscribe and before onNext, thus the active context is not provided
            // by reactor plugin, but only by the webflux plugin that keeps the transaction active.
            ElasticApmTracer tracer = GlobalTracer.requireTracerImpl();
            Transaction transaction = Objects.requireNonNull(tracer.currentTransaction(), "active transaction is required");
            // This mimics setting the name through the public API. We cannot use the public API if we want to test span recycling
            transaction.withName("user-provided-name", AbstractSpan.PRIO_USER_SUPPLIED);


            return greetingHandler.helloMessage("transaction=" + Objects.requireNonNull(tracer.currentTransaction()).getTraceContext().getId());
        } finally {
            log.debug("exit customTransactionName");
        }
    }

    @GetMapping("/duration")
    public Mono<String> duration(@RequestParam("duration") int durationMillis) {
        return greetingHandler.duration(durationMillis);
    }

}
