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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.util.Optional;

@RestController
public class GreetingController {

    final GreetingHandler greetingHandler;

    @Autowired
    public GreetingController(GreetingHandler greetingHandler) {
        this.greetingHandler = greetingHandler;
    }

    @RequestMapping(produces = MediaType.TEXT_PLAIN_VALUE, path = "/controller/hello")
    public Mono<String> getHello(@RequestParam(value = "name", required = false) @Nullable String name) {
        return Mono.just(greetingHandler.helloMessage(Optional.ofNullable(name)));
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
        return ResponseEntity.status(500)
            .body(greetingHandler.exceptionMessage(e));
    }

}
