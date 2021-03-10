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

import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.context.Response;
import co.elastic.apm.agent.impl.context.web.ResultUtil;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.reactor.TracedSubscriber;
import co.elastic.apm.agent.sdk.weakmap.WeakMapSupplier;
import co.elastic.apm.agent.util.PotentiallyMultiValuedMap;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import org.reactivestreams.Subscription;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import reactor.core.CoreSubscriber;

import javax.annotation.Nullable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static co.elastic.apm.agent.impl.transaction.AbstractSpan.PRIO_HIGH_LEVEL_FRAMEWORK;
import static co.elastic.apm.agent.springwebflux.WebFluxInstrumentation.SERVLET_TRANSACTION;
import static org.springframework.web.reactive.function.server.RouterFunctions.MATCHING_PATTERN_ATTRIBUTE;

/**
 * Transaction-aware subscriber that will terminate transaction on error and optionally on completion.
 * Transaction activation is handled in two ways:
 * <ul>
 *     <li>indirectly through {@link TracedSubscriber} wrapping from reactor plugin</li>
 *     <li>directly when executed outside of {@link TracedSubscriber} wrapping</li>
 * </ul>
 *
 * @param <T>
 */
public class TransactionAwareSubscriber<T> extends TracedSubscriber<T, Transaction> {

    private static final WeakConcurrentMap<HandlerMethod, Boolean> ignoredHandlerMethods = WeakMapSupplier.createMap();

    private final ServerWebExchange exchange;
    private final boolean endOnComplete;
    private final boolean keepActive;

    /**
     * @param subscriber    subscriber to wrap
     * @param transaction   transaction
     * @param exchange      server web exchange
     * @param endOnComplete {@literal} true to terminate transaction on complete or error
     * @param keepActive    {@literal true} to keep transaction active between {@link #onSubscribe(Subscription)} and
     *                      terminal method call {@link #onError(Throwable)} or {@link #onComplete()},
     *                      use {@literal false} to activate only during wrapped method invocation.
     * @param description   human-readable description to make debugging easier
     */
    public TransactionAwareSubscriber(CoreSubscriber<? super T> subscriber,
                                      Transaction transaction,
                                      ServerWebExchange exchange,
                                      boolean endOnComplete,
                                      boolean keepActive,
                                      String description) {

        super(subscriber, transaction, description);
        this.exchange = exchange;
        this.endOnComplete = endOnComplete;
        this.keepActive = keepActive;
    }

    @Override
    public void onSubscribe(Subscription s) {
        if (!keepActive) {
            super.onSubscribe(s);
        } else {
            activate();
            subscriber.onSubscribe(s);
        }
    }

    @Override
    public void onNext(T next) {
        if (!keepActive) {
            super.onNext(next);
        } else {
            subscriber.onNext(next);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        if (!keepActive) {
            super.onError(throwable);
        } else {
            subscriber.onError(throwable);
            deactivate();
        }
        endTransaction(context, exchange, throwable);
    }

    @Override
    public void onComplete() {
        if (!keepActive) {
            super.onComplete();
        } else {
            subscriber.onComplete();
            deactivate();
        }

        if (endOnComplete) {
            endTransaction(context, exchange, null);
        }
    }

    static void endTransaction(Transaction transaction, ServerWebExchange exchange, @Nullable Throwable thrown) {
        // ensure that we don't try to end transaction twice
        // we rely on default implementation thread safety (backed by a concurrent map).
        if (transaction != exchange.getAttributes().remove(WebFluxInstrumentation.TRANSACTION_ATTRIBUTE)) {
            return;
        }

        if (ignoreTransaction(exchange, transaction)) {
            transaction.ignoreTransaction();
            transaction.end();
            return;
        }

        StringBuilder transactionName = transaction.getAndOverrideName(PRIO_HIGH_LEVEL_FRAMEWORK, true);
        if (transactionName != null) {
            String httpMethod = exchange.getRequest().getMethodValue();

            // bean name & method should be set for annotated methods
            String beanName = exchange.getAttribute(WebFluxInstrumentation.ANNOTATED_BEAN_NAME_ATTRIBUTE);
            String methodName = exchange.getAttribute(WebFluxInstrumentation.ANNOTATED_METHOD_NAME_ATTRIBUTE);

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

        // Fill request/response details if they haven't been already by another HTTP plugin (servlet or other).
        if (!transaction.getContext().getRequest().hasContent()) {
            fillRequest(transaction, exchange);
            fillResponse(transaction, exchange);
        }

        transaction.captureException(thrown);

        // In case transaction has been created by Servlet, we should not terminate it as the Servlet instrumentation
        // will take care of this.
        if (Boolean.TRUE != exchange.getAttributes().get(SERVLET_TRANSACTION)) {
            transaction.end();
        }

    }

    private static boolean ignoreTransaction(ServerWebExchange exchange, Transaction transaction) {
        // Annotated controllers have the invoked handler method available in exchange
        // thus we can rely on this to ignore methods that return ServerSideEvents which should not report transactions
        Object attribute = exchange.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (!(attribute instanceof HandlerMethod)) {
            return false;
        }

        HandlerMethod handlerMethod = (HandlerMethod) attribute;
        Boolean ignoredCache = ignoredHandlerMethods.get(handlerMethod);
        if (ignoredCache != null) {
            return ignoredCache;
        }

        Type returnType = handlerMethod.getMethod().getGenericReturnType();
        if (!(returnType instanceof ParameterizedType)) {
            ignoredHandlerMethods.put(handlerMethod, false);
            return false;
        }

        Type[] genReturnTypes = ((ParameterizedType) returnType).getActualTypeArguments();
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < genReturnTypes.length; i++) {
            if (genReturnTypes[i].getTypeName().startsWith(WebFluxInstrumentation.SSE_EVENT_CLASS)) {
                ignoredHandlerMethods.put(handlerMethod, true);
                return true;
            }
        }

        ignoredHandlerMethods.put(handlerMethod, false);
        return false;
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
