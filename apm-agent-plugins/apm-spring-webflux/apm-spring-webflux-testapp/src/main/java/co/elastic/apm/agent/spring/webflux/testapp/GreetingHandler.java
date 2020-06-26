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

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.util.Optional;

@Component
public class GreetingHandler {

    public Mono<String> helloMessage(@Nullable String name) {
        return Mono.just(String.format("Hello, %s!", Optional.ofNullable(name).orElse("Spring")));
    }

    public <T> Mono<T> throwException(){
        throw new RuntimeException("intentional handler exception");
    }

    public <T> Mono<T> monoError() {
        return Mono.error(new RuntimeException("intentional error"));
    }

    public <T> Mono<T> monoEmpty() {
        return Mono.empty();
    }

    public String exceptionMessage(Throwable t){
        return "error handler: " + t.getMessage();
    }

    public Flux<String> helloFlux(int count) {
        return Flux.range(1, count)
            .map(i -> String.format("Hello flux %d", i));
    }
}
