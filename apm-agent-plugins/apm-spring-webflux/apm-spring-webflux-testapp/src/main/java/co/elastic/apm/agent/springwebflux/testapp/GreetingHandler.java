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
package co.elastic.apm.agent.springwebflux.testapp;

import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Span;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Optional;

@Component
public class GreetingHandler {

    public static final Scheduler CHILDREN_SCHEDULER = Schedulers.newElastic("children");

    public Mono<String> helloMessage(@Nullable String name) {
        return Mono.just(String.format("Hello, %s!", Optional.ofNullable(name).orElse("Spring")));
    }

    public <T> Mono<T> throwException() {
        throw new RuntimeException("intentional handler exception");
    }

    public <T> Mono<T> monoError() {
        return Mono.error(new RuntimeException("intentional error"));
    }

    public <T> Mono<T> monoEmpty() {
        return Mono.empty();
    }

    public String exceptionMessage(Throwable t) {
        return "error handler: " + t.getMessage();
    }

    public Flux<String> helloFlux(int count) {
        return Flux.range(1, count)
            .map(i -> String.format("Hello flux %d", i));
    }

    public Flux<String> childSpans(int count, long delayMillis, long durationMilis) {
        return Flux.range(1, count)
            .subscribeOn(CHILDREN_SCHEDULER)
            // initial delay
            .delayElements(Duration.ofMillis(delayMillis))
            .map(i -> String.format("child %d", i))
            .doOnNext(name -> {
                Span span = ElasticApm.currentTransaction().startSpan();
                span.setName(String.format("%s id=%s", name, span.getId()));
                try {
                    Thread.sleep(durationMilis);
                } catch (InterruptedException e) {
                    // silently ignored
                } finally {
                    span.end();
                }
            });

    }

    // Emulates a transaction that takes a known amount of time
    // the whole transaction duration should include the delay
    public Mono<String> duration(long durationMillis) {
        return helloMessage(String.format("duration=%d", durationMillis))
            .delayElement(Duration.ofMillis(durationMillis));
    }

}
