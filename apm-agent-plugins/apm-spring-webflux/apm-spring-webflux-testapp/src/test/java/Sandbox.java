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
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.util.function.Function;

public class Sandbox {

    @Test
    void flux() {
        Flux.just(1, 2, 3)
            .concatMap(i -> Mono.just(i * 2))
            .subscribe(System.out::println);
    }

    @Test
    void mono_error() {

        // should create one span/transaction with an exception
        Mono.error(RuntimeException::new)
            .onErrorReturn("hello")
            .subscribe(System.out::println);
    }

    @Test
    void mono_empty_operator() {
        // should not create span, as operator is not applied
        Mono.<String>empty()
            .map(v -> {
                throw new IllegalStateException("map should not be called");
            })
            .subscribe(System.out::println);
    }

    @Test
    void mono_with_context() {
        String key = "key";
        Mono<Context> mono = Mono.just("a")
            .flatMap(s -> Mono.subscriberContext()
                .doFinally((signalType) -> {
                    switch (signalType) {
                        case ON_COMPLETE:
                        case ON_ERROR:
                        case CANCEL:
                            System.out.println("terminate " + signalType); // we don't have the proper signal
                    }

                })
            )
            .subscriberContext(c -> c.put(key, "value"));

        // does not work as expected :-(
    }

    @Test
    void mono_with_decoration() {

        // try datadog-style decoration

        Mono<Object> mono = Mono.just("a");

        mono = wrap(mono);

        StepVerifier.create(mono)
            .expectNext("a")
            .verifyComplete();
    }

    @Test
    void flux_with_decoration() {
        Flux<String> flux = Flux.just("a", "b", "c");

        flux = wrap(flux);

        StepVerifier.create(flux)
            .expectNext("a", "b", "c")
            .verifyComplete();
    }

    private static <T> Mono<T> wrap(Mono<T> mono) {
        return mono.<T>transform(lift());
    }

    private static <T> Flux<T> wrap(Flux<T> flux) {
        return flux.<T>transform(lift());
    }

    private static <T> Function<? super Publisher<T>, ? extends Publisher<T>> lift() {
        return Operators.lift((scannable, subscriber) -> new DecoratedSubScriber<>(subscriber));
    }

    private static class DecoratedSubScriber<T> implements CoreSubscriber<T> {
        private final CoreSubscriber<? super T> subscriber;

        public DecoratedSubScriber(CoreSubscriber<? super T> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            wrap("onSubscribe", () -> subscriber.onSubscribe(subscription));
        }

        @Override
        public void onNext(T next) {
            wrap("onNext", () -> subscriber.onNext(next));
        }

        @Override
        public void onError(Throwable t) {
            wrap("onError", () -> subscriber.onError(t));
        }

        @Override
        public void onComplete() {
            wrap("onComplete", subscriber::onComplete);
        }

        private static void wrap(String name, Runnable task) {
            System.out.println("before " + name);
            task.run();
            System.out.println("after " + name);
        }
    }
}
