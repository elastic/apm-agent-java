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

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.context.web.ResultUtil;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.reactivestreams.Subscription;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;

import java.util.Collection;
import java.util.Collections;

import static org.springframework.web.reactive.function.server.RouterFunctions.MATCHING_PATTERN_ATTRIBUTE;

public abstract class WebFluxInstrumentation extends ElasticApmInstrumentation {

    public static final String TRANSACTION_ATTRIBUTE = WebFluxInstrumentation.class.getName() + ".transaction";

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("spring-webflux");
    }


    @VisibleForAdvice
    public static <T> Mono<T> dispatcherWrap(Mono<T> mono, Transaction transaction) {
        return mono.<T>transform(
            Operators.lift((scannable, subscriber) -> new DecoratedSubscriber<>(subscriber, transaction, false))
        );
    }

    @VisibleForAdvice
    public static <T> Mono<T> handlerWrap(Mono<T> mono, Transaction transaction) {
        return mono.<T>transform(
            Operators.lift((scannable, subscriber) -> new DecoratedSubscriber<>(subscriber, transaction, true))
        );
    }

    @VisibleForAdvice
    public static <T> Mono<T> setNameOnComplete(Mono<T> mono, ServerWebExchange exchange) {
        return mono.<T>transform(
            Operators.lift((scannable, subscriber) -> new BaseSubscriber<T>(subscriber) {

                @Override
                public void onComplete() {
                    subscriber.onComplete();

                    // set transaction name from URL pattern (if applicable)
                    Transaction transaction = exchange.getAttribute(TRANSACTION_ATTRIBUTE);
                    if (transaction == null) {
                        return;
                    }
                    PathPattern pattern = exchange.getAttribute(MATCHING_PATTERN_ATTRIBUTE);
                    if (pattern == null) {
                        return;
                    }
                    transaction.withName(pattern.getPatternString());
                }

            })
        );
    }

    private static class BaseSubscriber<T> implements CoreSubscriber<T> {
        private final CoreSubscriber<? super T> subscriber;

        public BaseSubscriber(CoreSubscriber<? super T> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void onSubscribe(Subscription s) {
            subscriber.onSubscribe(s);
        }

        @Override
        public void onNext(T t) {
            subscriber.onNext(t);
        }

        @Override
        public void onError(Throwable t) {
            subscriber.onError(t);
        }

        @Override
        public void onComplete() {
            subscriber.onComplete();
        }
    }

    private static class DecoratedSubscriber<T> implements CoreSubscriber<T> {
        private final CoreSubscriber<? super T> subscriber;
        private final Transaction transaction;
        private final boolean terminateTransactionOnComplete;

        // TODO need to activate/deactivate transaction during next,error,complete method execution

        public DecoratedSubscriber(CoreSubscriber<? super T> subscriber, Transaction transaction, boolean terminateTransactionOnComplete) {
            this.subscriber = subscriber;
            this.transaction = transaction;
            this.terminateTransactionOnComplete = terminateTransactionOnComplete;
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
            if (t instanceof ResponseStatusException) {
                // no matching mapping, generates a 404 error
                HttpStatus status = ((ResponseStatusException) t).getStatus();

                transaction.getContext()
                    .getResponse()
                    .withStatusCode(status.value())
                    .withFinished(true);


                transaction.captureException(t)
                    .withResultIfUnset(ResultUtil.getResultByHttpStatus(status.value()))
                    .end();
            }
            wrap("onError", () -> subscriber.onError(t));
        }

        @Override
        public void onComplete() {
            wrap("onComplete", subscriber::onComplete);
            if (terminateTransactionOnComplete) {
                transaction.end();
            }

        }

        private void wrap(String name, Runnable task) {
//            System.out.println(String.format("before\t%s\t%x\t%s", name, System.identityHashCode(subscriber), subscriber.getClass().getCanonicalName()));
//            transaction.activate();
            try {
                task.run();
            } finally {
//                transaction.deactivate();
            }
//            System.out.println(String.format("after\t%s\t%x\t%s", name, System.identityHashCode(subscriber), subscriber.getClass().getCanonicalName()));
        }
    }

}
