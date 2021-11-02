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
package co.elastic.apm.agent.webfluxclient;

import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.reactivestreams.Publisher;
import org.springframework.http.HttpMethod;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;

import java.net.URI;
import java.util.function.BiFunction;
import java.util.function.Function;

public class WebfluxClientHelper {

    public static AbstractSpan createHttpSpan(AbstractSpan parentSpan, HttpMethod httpMethod, URI uri) {
        return HttpClientHelper.startHttpClientSpan(parentSpan, httpMethod.toString(), uri, uri.getHost());
    }

    public static Transaction getOrCreateTransaction(Tracer tracer, Class clazz) {
        Transaction t = tracer.currentTransaction();
        if (t == null) {
            //TODO: check the object
            t = tracer.startRootTransaction(clazz.getClassLoader());
            //FIXME
            ((Transaction) t)
                .withName("name")
                .withType("test")
                .withResult("success")
                .withOutcome(Outcome.SUCCESS)
                .activate();
        } else {
            //FIXME: startChildTransaction?
        }
        return t;
    }

    public static <T> Publisher<T> wrapSubscriber(Publisher<T> publisher, final String subscriberKey, final Tracer tracer,
                                                  final String prependId) {

        Function<? super Publisher<T>, ? extends Publisher<T>> lift = Operators.liftPublisher(
            new BiFunction<Publisher, CoreSubscriber<? super T>, CoreSubscriber<? super T>>() {
                @Override
                public CoreSubscriber<? super T> apply(Publisher publisher, CoreSubscriber<? super T> coreSubscriber) {

                    if (!WebfluxClientSubscriber.getLogPrefixSubscriberMap().containsKey(subscriberKey + prependId)) {
                        WebfluxClientSubscriber.getLogPrefixSubscriberMap().put(subscriberKey + prependId, true);
                        return new WebfluxClientSubscriber(coreSubscriber, subscriberKey, tracer, prependId);
                    } else {
                        return coreSubscriber;
                    }
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
