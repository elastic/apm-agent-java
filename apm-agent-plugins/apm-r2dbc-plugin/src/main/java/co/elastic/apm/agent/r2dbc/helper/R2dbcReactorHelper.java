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
package co.elastic.apm.agent.r2dbc.helper;

import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.CoreSubscriber;
import reactor.core.Fuseable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;

import java.util.function.BiFunction;
import java.util.function.Function;

public class R2dbcReactorHelper {

    private static final Logger log = LoggerFactory.getLogger(R2dbcReactorHelper.class);

    public static <T> Publisher<T> wrapPublisher(final Tracer tracer, Publisher<T> publisher, final Span span, final Connection connection) {
        Function<? super Publisher<T>, ? extends Publisher<T>> lift = Operators.liftPublisher(
            new BiFunction<Publisher, CoreSubscriber<? super T>, CoreSubscriber<? super T>>() {
                @Override
                public CoreSubscriber<? super T> apply(Publisher publisher, CoreSubscriber<? super T> subscriber) {
                    log.debug("wrapping subscribe with span {}", span);
                    if (publisher instanceof Fuseable.ScalarCallable) {
                        log.info("skip wrapping {}", subscriber.toString());
                        return subscriber;
                    }
                    final AbstractSpan<?> active = tracer.getActive();
                    if (active == null) {
                        return subscriber;
                    }
                    return new R2dbcSubscriber<>(subscriber, tracer, span, connection);
                }
            }
        );
        if (publisher instanceof Mono) {
            publisher = ((Mono) publisher).transform(lift);
        } else if (publisher instanceof Flux) {
            publisher = ((Flux) publisher).transform(lift);
        }
        return publisher;
    }

    public static <T> Publisher<T> wrapConnectionPublisher(Publisher<T> publisher, final ConnectionFactoryOptions connectionFactoryOptions) {
        Function<? super Publisher<T>, ? extends Publisher<T>> lift = Operators.liftPublisher(
            new BiFunction<Publisher, CoreSubscriber<? super T>, CoreSubscriber<? super T>>() {
                @Override
                public CoreSubscriber<? super T> apply(Publisher publisher, CoreSubscriber<? super T> subscriber) {
                    return new R2dbcConnectionSubscriber<>(subscriber, connectionFactoryOptions);
                }
            }
        );
        if (publisher instanceof Mono) {
            publisher = ((Mono) publisher).transform(lift);
        } else if (publisher instanceof Flux) {
            publisher = ((Flux) publisher).transform(lift);
        }
        return publisher;
    }

    public static <T> Publisher<T> wrapResultPublisher(Publisher<T> publisher, final Span span) {
        Function<? super Publisher<T>, ? extends Publisher<T>> lift = Operators.liftPublisher(
            new BiFunction<Publisher, CoreSubscriber<? super T>, CoreSubscriber<? super T>>() {
                @Override
                public CoreSubscriber<? super T> apply(Publisher publisher, CoreSubscriber<? super T> subscriber) {
                    return new R2dbcResultSubscriber<>(subscriber, span);
                }
            }
        );
        if (publisher instanceof Mono) {
            publisher = ((Mono) publisher).transform(lift);
        } else if (publisher instanceof Flux) {
            publisher = ((Flux) publisher).transform(lift);
        }
        return publisher;
    }

}
