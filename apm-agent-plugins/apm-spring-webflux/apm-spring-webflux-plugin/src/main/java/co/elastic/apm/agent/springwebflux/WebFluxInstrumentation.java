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
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.springframework.http.server.reactive.AbstractServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.Collections;

public abstract class WebFluxInstrumentation extends TracerAwareInstrumentation {

    public static final String TRANSACTION_ATTRIBUTE = WebFluxInstrumentation.class.getName() + ".transaction";
    public static final String SERVLET_ATTRIBUTE = WebFluxInstrumentation.class.getName() + ".servlet";
    public static final String ANNOTATED_BEAN_NAME_ATTRIBUTE = WebFluxInstrumentation.class.getName() + ".bean_name";
    public static final String ANNOTATED_METHOD_NAME_ATTRIBUTE = WebFluxInstrumentation.class.getName() + ".method_name";

    @Override
    public boolean indyPlugin() {
        return true;
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("spring-webflux");
    }

    @Nullable
    @VisibleForAdvice
    public static Transaction getOrCreateTransaction(Class<?> clazz, ServerWebExchange exchange) {

        Transaction transaction = getServletTransaction(exchange);

        if (transaction == null) {
            transaction = tracer.startRootTransaction(clazz.getClassLoader());
        }

        if (transaction == null) {
            return null;
        }

        transaction.withType("request")
            .activate();

        // store transaction in exchange to make it easy to retrieve from other handlers
        exchange.getAttributes().put(TRANSACTION_ATTRIBUTE, transaction);

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

    /**
     * Activates transaction during "dispatch" phase (before handler execution)
     *
     * @param <T>         mono generic type
     * @param mono        mono to wrap
     * @param transaction transaction
     * @param exchange    exchange
     * @return wrapped mono that will activate transaction when mono is used
     */
    @VisibleForAdvice
    public static <T> Mono<T> dispatcherWrap(Mono<T> mono, Transaction transaction, ServerWebExchange exchange) {
        return mono.<T>transform(
            Operators.lift((scannable, subscriber) -> new TransactionAwareSubscriber<T>(subscriber, transaction, true, exchange, "dispatcher"))
        );
    }

    /**
     * Activates transaction during "handler" phase, where request is handled
     *
     * @param <T>         mono generic type
     * @param mono        mono to wrap
     * @param transaction transaction
     * @param exchange    exchange
     * @param name
     * @return wrapped mono that will activate transaction when mono is used and terminate it on mono is completed
     */
    @VisibleForAdvice
    public static <T> Mono<T> handlerWrap(@Nullable Mono<T> mono, Transaction transaction, ServerWebExchange exchange, String name) {
        return mono.<T>transform(
            Operators.lift((scannable, subscriber) -> new TransactionAwareSubscriber<T>(subscriber, transaction, false, exchange, name)));
    }


}
