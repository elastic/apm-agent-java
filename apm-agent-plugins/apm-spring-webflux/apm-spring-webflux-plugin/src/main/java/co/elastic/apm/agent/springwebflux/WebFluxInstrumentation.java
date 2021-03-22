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
package co.elastic.apm.agent.springwebflux;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.reactive.AbstractServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.CoreSubscriber;
import reactor.core.Fuseable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.Collections;
import java.util.function.BiFunction;

public abstract class WebFluxInstrumentation extends TracerAwareInstrumentation {

    private static final Logger log = LoggerFactory.getLogger(WebFluxInstrumentation.class);

    public static final String TRANSACTION_ATTRIBUTE = WebFluxInstrumentation.class.getName() + ".transaction";
    public static final String ANNOTATED_BEAN_NAME_ATTRIBUTE = WebFluxInstrumentation.class.getName() + ".bean_name";
    public static final String ANNOTATED_METHOD_NAME_ATTRIBUTE = WebFluxInstrumentation.class.getName() + ".method_name";
    public static final String SERVLET_TRANSACTION = WebFluxInstrumentation.class.getName() + ".servlet_transaction";

    public static final String SSE_EVENT_CLASS = "org.springframework.http.codec.ServerSentEvent";

    @Override
    public final Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("spring-webflux");
    }

    @Nullable
    public static Transaction getOrCreateTransaction(Class<?> clazz, ServerWebExchange exchange) {

        Transaction transaction = getServletTransaction(exchange);
        boolean fromServlet = transaction != null;

        if (!fromServlet) {
            transaction = tracer.startRootTransaction(clazz.getClassLoader());
        }

        if (transaction == null) {
            return null;
        }

        transaction.withType("request")
            // If transaction has been created by servlet, it is not active on the current thread
            // which might be an implementation detail. However, it makes activation lifecycle easier as it is
            // only managed within webflux/reactor plugins
            .activate();

        // store transaction in exchange to make it easy to retrieve from other handlers
        exchange.getAttributes().put(TRANSACTION_ATTRIBUTE, transaction);

        exchange.getAttributes().put(SERVLET_TRANSACTION, fromServlet);

        return transaction;
    }

    @Nullable
    private static Transaction getServletTransaction(ServerWebExchange exchange) {
        // see ServletHttpHandlerAdapter and sub-classes for implementation details

        // While the active transaction is the one created by Servlet, it would rely on the fact that we are on the
        // same thread as the one that created the transaction, which is an implementation detail. While not really
        // elegant, this solution seems the most reliable for now.
        Transaction transaction = null;
        try {
            ServerHttpRequest exchangeRequest = exchange.getRequest();
            if (exchangeRequest instanceof AbstractServerHttpRequest) {
                Object nativeRequest = ((AbstractServerHttpRequest) exchangeRequest).getNativeRequest();
                if (nativeRequest instanceof HttpServletRequest) {
                    transaction = (Transaction) ((HttpServletRequest) nativeRequest)
                        // adding a dependency to servlet instrumentation plugin is not worth for such a simple string
                        // but it's fine as long as we have tests for this
                        .getAttribute("co.elastic.apm.agent.servlet.ServletApiAdvice.transaction");
                }
            }
        } catch (Throwable ignored) {
            return null;
        }
        return transaction;
    }

    public static <T> Mono<T> wrapDispatcher(Mono<T> mono, Transaction transaction, ServerWebExchange exchange) {
        // we need to wrap returned mono to terminate transaction
        return doWrap(mono, transaction, exchange, true,  "webflux-dispatcher");
    }

    public static <T> Mono<T> wrapHandlerAdapter(Mono<T> mono, Transaction transaction, ServerWebExchange exchange) {
        return doWrap(mono, transaction, exchange, true,  "webflux-handler-adapter");
    }

    public static <T> Mono<T> wrapInvocableHandlerMethod(Mono<T> mono, Transaction transaction, ServerWebExchange exchange) {
        // We can keep transaction active here as execution of the whole mono is within the same thread.
        // That might be just an implementation detail, but so far it works.
        return doWrap(mono, transaction, exchange, true, "webflux-invocable-handler-method");
    }

    private static <T> Mono<T> doWrap(Mono<T> mono, final Transaction transaction, final ServerWebExchange exchange, final boolean keepActive, final String description) {
        //noinspection Convert2Lambda,rawtypes,Convert2Diamond
        mono = mono.transform(Operators.liftPublisher(new BiFunction<Publisher, CoreSubscriber<? super T>, CoreSubscriber<? super T>>() {
            @Override
            public CoreSubscriber<? super T> apply(Publisher publisher, CoreSubscriber<? super T> subscriber) {
                // don't wrap known #error #just #empty as they have instantaneous execution
                if (publisher instanceof Fuseable.ScalarCallable) {
                    log.trace("skip wrapping publisher {}", publisher);
                    return subscriber;
                }

                log.trace("wrapping {} subscriber with transaction {}", description, transaction);
                return new TransactionAwareSubscriber<T>(subscriber, tracer, transaction, exchange, true, keepActive, description);
            }
        }));
        if (log.isTraceEnabled()) {
            mono = mono.log(description);
        }
        return mono;
    }

}
