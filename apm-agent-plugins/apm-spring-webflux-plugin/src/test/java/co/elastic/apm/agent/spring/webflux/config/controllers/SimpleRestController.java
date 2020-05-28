/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.spring.webflux.config.controllers;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

import java.net.InetSocketAddress;
import java.util.Optional;

@RestController
public class SimpleRestController {

    @GetMapping("/test")
    public Flux<String> get(ServerHttpRequest serverHttpRequest) {
        return Flux.just("Hello " + Optional.ofNullable(serverHttpRequest.getHeaders().getHost()).map(InetSocketAddress::getHostName).orElse("Unknown"));
    }

    @PostMapping("/test")
    public Flux<String> post(ServerHttpRequest serverHttpRequest) {
        return Flux.just("Hello " + Optional.ofNullable(serverHttpRequest.getHeaders().getHost()).map(InetSocketAddress::getHostName).orElse("Unknown"));
    }

    @PutMapping("/test")
    public Flux<String> put(ServerHttpRequest serverHttpRequest) {
        return Flux.just("Hello " + Optional.ofNullable(serverHttpRequest.getHeaders().getHost()).map(InetSocketAddress::getHostName).orElse("Unknown"));
    }

    @DeleteMapping("/test")
    public Flux<String> delete(ServerHttpRequest serverHttpRequest) {
        return Flux.just("Hello " + Optional.ofNullable(serverHttpRequest.getHeaders().getHost()).map(InetSocketAddress::getHostName).orElse("Unknown"));
    }

    @PatchMapping("/test")
    public Flux<String> patch(ServerHttpRequest serverHttpRequest) {
        return Flux.just("Hello " + Optional.ofNullable(serverHttpRequest.getHeaders().getHost()).map(InetSocketAddress::getHostName).orElse("Unknown"));
    }

    @GetMapping("/test/chained")
    public Flux<String> getChained(ServerWebExchange serverWebExchange) {
        return get(serverWebExchange.getRequest());
    }

    @PostMapping("/test/chained")
    public Flux<String> postChained(ServerWebExchange serverWebExchange) {
        return post(serverWebExchange.getRequest());
    }

    @PutMapping("/test/chained")
    public Flux<String> putChained(ServerWebExchange serverWebExchange) {
        return put(serverWebExchange.getRequest());
    }

    @DeleteMapping("/test/chained")
    public Flux<String> deleteChained(ServerWebExchange serverWebExchange) {
        return delete(serverWebExchange.getRequest());
    }

    @PatchMapping("/test/chained")
    public Flux<String> patchChained(ServerWebExchange serverWebExchange) {
        return patch(serverWebExchange.getRequest());
    }
}
