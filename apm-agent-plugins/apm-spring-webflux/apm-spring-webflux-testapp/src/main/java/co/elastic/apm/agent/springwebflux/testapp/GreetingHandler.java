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
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Span;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

@Component
public class GreetingHandler {

    public static final Scheduler CHILDREN_SCHEDULER = Schedulers.newElastic("children");

    public Mono<String> helloMessage(@Nullable String name) {
        return Mono.just(String.format("Hello, %s!", Optional.ofNullable(name).orElse("Spring")));
    }

    public <T> Mono<T> throwException() {
        throw new RuntimeException("intentional exception");
    }

    public Mono<String> delayedException() {
        return helloMessage(null)
            .delayElement(Duration.ofMillis(50))
            .flatMap(s -> {
                throw new RuntimeException("intentional exception");
            });
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

    public Flux<String> childSpans(int count, long delayMillis, long durationMillis) {
        return Flux.range(1, count)
            .subscribeOn(CHILDREN_SCHEDULER)
            // initial delay
            .delayElements(Duration.ofMillis(delayMillis))
            .map(i -> String.format("child %d", i))
            .doOnNext(name -> {
                if (!GlobalTracer.isNoop()) {
                    Span<?> span = Objects.requireNonNull(GlobalTracer.get().require(ElasticApmTracer.class).currentTransaction()).createSpan();
                    span.withName(String.format("%s id=%s", name, span.getTraceContext().getId()));
                    try {
                        fakeWork(durationMillis);
                    } finally {
                        span.end();
                    }
                }
            });
    }

    public ServerSentEvent<String> toSSE(String s) {
        // we might be able to inject a comment into SSE event for context propagation
        return ServerSentEvent.<String>builder().data(s).build();
    }

    // Emulates a transaction that takes a known amount of time
    // the whole transaction duration should include the delay
    public Mono<String> duration(long durationMillis) {
        return helloMessage(String.format("duration=%d", durationMillis))
            .doOnNext(m -> fakeWork(durationMillis));
    }

    public Mono<String> getUsernameFromContext() {
        return ReactiveSecurityContextHolder.getContext()
            .flatMap(ctx -> Mono.just(ctx.getAuthentication().getName()));
    }

    private static void fakeWork(long durationMs) {
        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException e) {
            // silently ignored
        }
    }

}
