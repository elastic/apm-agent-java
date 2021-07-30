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
package co.elastic.apm.agent.springwebflux;

import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;

import javax.annotation.Nullable;
import java.util.function.BiFunction;

public class WebfluxHelper {

    private static final Logger log = LoggerFactory.getLogger(WebfluxHelper.class);

    public static final String TRANSACTION_ATTRIBUTE = WebfluxHelper.class.getName() + ".transaction";
    private static final String SERVLET_TRANSACTION = WebfluxHelper.class.getName() + ".servlet_transaction";
    public static final String SSE_EVENT_CLASS = "org.springframework.http.codec.ServerSentEvent";

    private static final HeaderGetter HEADER_GETTER = new HeaderGetter();

    @Nullable
    public static Transaction getOrCreateTransaction(Tracer tracer, Class<?> clazz, ServerWebExchange exchange) {

        Transaction transaction = WebfluxServletHelper.getServletTransaction(exchange);
        boolean fromServlet = transaction != null;

        if (!fromServlet) {
            transaction = tracer.startChildTransaction(exchange.getRequest().getHeaders(), HEADER_GETTER, ServerWebExchange.class.getClassLoader());
        }

        if (transaction == null) {
            return null;
        }

        transaction.withType("request").activate();

        // store transaction in exchange to make it easy to retrieve from other handlers
        exchange.getAttributes().put(TRANSACTION_ATTRIBUTE, transaction);

        exchange.getAttributes().put(SERVLET_TRANSACTION, fromServlet);

        return transaction;
    }

    public static boolean isServletTransaction(ServerWebExchange exchange) {
        return Boolean.TRUE == exchange.getAttributes().get(SERVLET_TRANSACTION);
    }

    public static <T> Mono<T> wrapDispatcher(Tracer tracer, Mono<T> mono, Transaction transaction, ServerWebExchange exchange) {
        return doWrap(tracer, mono, transaction, exchange, "webflux-dispatcher");
    }

    private static <T> Mono<T> doWrap(final Tracer tracer, Mono<T> mono, final Transaction transaction, final ServerWebExchange exchange, final String description) {
        //noinspection Convert2Lambda,rawtypes,Convert2Diamond,ReactiveStreamsUnusedPublisher
        mono = mono.transform(Operators.liftPublisher(new BiFunction<Publisher, CoreSubscriber<? super T>, CoreSubscriber<? super T>>() {
            @Override // liftPublisher too (or whole transform param)
            public CoreSubscriber<? super T> apply(Publisher publisher, CoreSubscriber<? super T> subscriber) {
                log.trace("wrapping {} subscriber with transaction {}", description, transaction);

                // If there is already an active transaction, it's tempting to avoid wrapping as the context propagation
                // would be already provided through reactor instrumentation. However, we can't as the transaction
                // name would not be properly set to match Webflux annotated controllers/router definitions.
                return new TransactionAwareSubscriber<>(subscriber, tracer, transaction, exchange, description);
            }
        }));

        if (log.isTraceEnabled()) {
            mono = mono.log(description);
        }
        return mono;
    }

}
