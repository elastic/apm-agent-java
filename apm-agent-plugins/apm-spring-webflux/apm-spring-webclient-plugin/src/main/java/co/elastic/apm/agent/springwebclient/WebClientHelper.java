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
package co.elastic.apm.agent.springwebclient;

import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Tracer;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;

import java.util.function.BiFunction;
import java.util.function.Function;

public class WebClientHelper {

    private static final Logger log = LoggerFactory.getLogger(WebClientHelper.class);

    public static <T> Publisher<T> wrapSubscriber(Publisher<T> publisher, final Span<?> span, final Tracer tracer) {

        Function<? super Publisher<T>, ? extends Publisher<T>> lift = Operators.liftPublisher(
            new BiFunction<Publisher, CoreSubscriber<? super T>, CoreSubscriber<? super T>>() {
                @Override
                public CoreSubscriber<? super T> apply(Publisher publisher, CoreSubscriber<? super T> subscriber) {
                    log.trace("Trying to subscribe with span {}", span);
                    if (tracer.getActive() == null) {
                        return subscriber;
                    }
                    return new WebClientSubscriber<>(subscriber, span, tracer);
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
