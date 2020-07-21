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
import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.context.Response;
import co.elastic.apm.agent.impl.context.web.ResultUtil;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.util.PotentiallyMultiValuedMap;
import org.reactivestreams.Subscription;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.AbstractServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static co.elastic.apm.agent.impl.transaction.AbstractSpan.PRIO_HIGH_LEVEL_FRAMEWORK;
import static org.springframework.web.reactive.function.server.RouterFunctions.MATCHING_PATTERN_ATTRIBUTE;

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
        boolean isServlet = transaction != null;

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
        exchange.getAttributes().put(SERVLET_ATTRIBUTE, isServlet);

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

    private static class SubscriberWrapper<T> implements CoreSubscriber<T> {
        private final CoreSubscriber<? super T> subscriber;
        protected String name;

        public SubscriberWrapper(CoreSubscriber<? super T> subscriber, String name) {
            this.subscriber = subscriber;
            this.name = name;
        }

        private void debug(Runnable task, String method) {
            System.out.println(String.format("%s [enter] %s()", name, method));
            try {
                task.run();
            } finally {
                System.out.println(String.format("%s [exit]  %s()", name, method));
            }
        }

        @Override
        public void onSubscribe(Subscription s) {
            debug(() -> subscriber.onSubscribe(s), "onSubscribe");

        }

        @Override
        public void onNext(T t) {
            debug(() -> subscriber.onNext(t), "onNext");
        }

        @Override
        public void onError(Throwable t) {
            debug(() -> subscriber.onError(t), "onError");
        }

        @Override
        public void onComplete() {
            debug(subscriber::onComplete, "onComplete");
        }
    }

    private static class TransactionAwareSubscriber<T> extends SubscriberWrapper<T> {
        private final Transaction transaction;
        private final boolean terminateTransactionOnComplete;
        private final ServerWebExchange exchange;

        public TransactionAwareSubscriber(CoreSubscriber<? super T> subscriber,
                                          Transaction transaction,
                                          boolean terminateTransactionOnComplete,
                                          ServerWebExchange exchange, String name) {
            super(subscriber, name);
            this.transaction = transaction;
            this.terminateTransactionOnComplete = terminateTransactionOnComplete;
            this.exchange = exchange;
        }

        @Override
        public void onSubscribe(Subscription s) {
            transaction.activate();
            try {
                super.onSubscribe(s);
            } finally {
                transaction.deactivate();
            }
        }

        @Override
        public void onNext(T next) {
            transaction.activate();
            try {
                super.onNext(next);
            } finally {
                transaction.deactivate();
            }
        }

        @Override
        public void onError(Throwable t) {
            transaction.activate();
            try {
                super.onError(t);
            } finally {
                transaction.deactivate();

                endTransaction(t);
            }
        }

        @Override
        public void onComplete() {
            transaction.activate();
            try {
                super.onComplete();
            } finally {
                transaction.deactivate();
                if (terminateTransactionOnComplete) {
                    endTransaction(null);
                }
            }
        }

        private void endTransaction(@Nullable Throwable thrown) {
            StringBuilder transactionName = transaction.getAndOverrideName(PRIO_HIGH_LEVEL_FRAMEWORK, true);
            if (transactionName != null) {
                String httpMethod = exchange.getRequest().getMethodValue();

                // bean name & method should be set for annotated methods
                String beanName = exchange.getAttribute(ANNOTATED_BEAN_NAME_ATTRIBUTE);
                String methodName = exchange.getAttribute(ANNOTATED_METHOD_NAME_ATTRIBUTE);

                PathPattern pattern = exchange.getAttribute(MATCHING_PATTERN_ATTRIBUTE);

                if (beanName != null && methodName != null) {
                    transactionName.append(beanName)
                        .append('#')
                        .append(methodName);
                } else {
                    transactionName.append(httpMethod).append(' ');
                    if (pattern != null) {
                        transactionName.append(pattern.getPatternString());
                    } else {
                        transactionName.append("unknown route");
                    }
                }
            }

            transaction.captureException(thrown);

            // when transaction has been created by servlet, we let servlet instrumentation handle request/response
            // and ending the transaction properly
            if (!Boolean.TRUE.equals(exchange.getAttribute(SERVLET_ATTRIBUTE))) {
                fillRequest(transaction, exchange);
                fillResponse(transaction, exchange);

                transaction.end();
            }


        }
    }

    private static void fillRequest(Transaction transaction, ServerWebExchange exchange) {
        ServerHttpRequest serverRequest = exchange.getRequest();
        Request request = transaction.getContext().getRequest();

        request.withMethod(serverRequest.getMethodValue());

        InetSocketAddress remoteAddress = serverRequest.getRemoteAddress();
        request.getSocket()
            .withRemoteAddress(remoteAddress == null ? null : remoteAddress.getAddress().getHostAddress())
            .withEncrypted(serverRequest.getSslInfo() != null);

        URI uri = serverRequest.getURI();
        request.getUrl()
            .withProtocol(uri.getScheme())
            .withHostname(uri.getHost())
            .withPort(uri.getPort())
            .withPathname(uri.getPath())
            .withSearch(uri.getQuery())
            .updateFull();

        copyHeaders(serverRequest.getHeaders(), request.getHeaders());

        for (Map.Entry<String, List<HttpCookie>> cookie : serverRequest.getCookies().entrySet()) {
            for (HttpCookie value : cookie.getValue()) {
                request.getCookies().add(cookie.getKey(), value.getValue());
            }
        }

    }

    private static void fillResponse(Transaction transaction, ServerWebExchange exchange) {
        ServerHttpResponse serverResponse = exchange.getResponse();
        HttpStatus statusCode = serverResponse.getStatusCode();
        int status = statusCode != null ? statusCode.value() : 200;

        transaction.withResultIfUnset(ResultUtil.getResultByHttpStatus(status));

        Response response = transaction.getContext().getResponse();

        copyHeaders(serverResponse.getHeaders(), response.getHeaders());

        response
            .withFinished(true)
            .withStatusCode(status);

    }

    private static void copyHeaders(HttpHeaders source, PotentiallyMultiValuedMap destination) {
        for (Map.Entry<String, List<String>> header : source.entrySet()) {
            for (String value : header.getValue()) {
                destination.add(header.getKey(), value);
            }
        }
    }

}
