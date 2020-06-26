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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;

@RestController()
public class GreetingController {

    final GreetingHandler greetingHandler;

    @Autowired
    public GreetingController(GreetingHandler greetingHandler) {
        this.greetingHandler = greetingHandler;
    }

    @RequestMapping(produces = MediaType.TEXT_PLAIN_VALUE, path = "/controller/hello")
    public Mono<String> getHello(@RequestParam(value = "name", required = false) @Nullable String name) {
        return greetingHandler.helloMessage(name);
    }

    @RequestMapping(produces = MediaType.TEXT_PLAIN_VALUE, path = "/controller/error-handler")
    public Mono<String> handlerError() {
        return greetingHandler.throwException();
    }

    @RequestMapping(produces = MediaType.TEXT_PLAIN_VALUE, path = "/controller/error-mono")
    public Mono<String> monoError() {
        return greetingHandler.monoError();
    }

    @RequestMapping(produces = MediaType.TEXT_PLAIN_VALUE, path = "/controller/empty-mono")
    public Mono<String> monoEmpty() {
        return greetingHandler.monoEmpty();
    }

    @ExceptionHandler
    public ResponseEntity<String> handleException(RuntimeException e){
        e.printStackTrace(System.err);
        return ResponseEntity.status(500)
            .body(greetingHandler.exceptionMessage(e));
    }

    @GetMapping("/controller/hello-mapping")
    public Mono<String> getMapping() {
        return greetingHandler.helloMessage("GET");
    }

    @PostMapping("/controller/hello-mapping")
    public Mono<String> postMapping() {
        return greetingHandler.helloMessage("POST");
    }

    @PutMapping("/controller/hello-mapping")
    public Mono<String> putMapping() {
        return greetingHandler.helloMessage("PUT");
    }

    @DeleteMapping("/controller/hello-mapping")
    public Mono<String> deleteMapping() {
        return greetingHandler.helloMessage("DELETE");
    }

    @PatchMapping("/controller/hello-mapping")
    public Mono<String> patchMapping() {
        return greetingHandler.helloMessage("PATCH");
    }

    @RequestMapping(path = "/controller/hello-mapping", method = {RequestMethod.HEAD, RequestMethod.OPTIONS, RequestMethod.TRACE})
    public Mono<String> otherMapping(ServerHttpRequest request) {
        return greetingHandler.helloMessage(request.getMethodValue());
    }

    @GetMapping("/controller/with-parameters/{id}")
    public Mono<String> withParameters(@PathVariable("id") String id) {
        return greetingHandler.helloMessage(id);
    }

    @GetMapping("/controller/flux")
    public Flux<String> getFlux(@RequestParam(value = "count", required = false, defaultValue = "3") int count){
        return greetingHandler.helloFlux(count);
    }

}
