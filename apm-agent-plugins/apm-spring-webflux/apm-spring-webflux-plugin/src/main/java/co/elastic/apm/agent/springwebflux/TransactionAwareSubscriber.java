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

import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.context.Response;
import co.elastic.apm.agent.impl.context.web.ResultUtil;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.sdk.weakmap.WeakMapSupplier;
import co.elastic.apm.agent.util.PotentiallyMultiValuedMap;
import co.elastic.apm.agent.util.SpanConcurrentHashMap;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import static co.elastic.apm.agent.springwebflux.WebFluxInstrumentation.isServletTransaction;
import static org.springframework.web.reactive.function.server.RouterFunctions.MATCHING_PATTERN_ATTRIBUTE;

/**
 * Transaction-aware subscriber that will activate transaction and terminate it on error and optionally on completion.
 *
 * @param <T>
 */
public class TransactionAwareSubscriber<T> implements CoreSubscriber<T> {

    private static final Logger log = LoggerFactory.getLogger(TransactionAwareSubscriber.class);

    private static final WeakConcurrentMap<HandlerMethod, Boolean> ignoredHandlerMethods = WeakMapSupplier.createMap();

    private static final WeakConcurrentMap<TransactionAwareSubscriber<?>, Transaction> transactionMap = SpanConcurrentHashMap.createWeakMap();

    protected final CoreSubscriber<? super T> subscriber;

    private final ServerWebExchange exchange;
    private final boolean endOnComplete;
    private final boolean keepActive;

    private final String description;

    private final Tracer tracer;

    /**
     * {@literal true} when a 'keep active' and the 'onSubscribe' method activated transaction
     */
    private boolean keepActiveActivation = false;

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
                                      Tracer tracer,
                                      Transaction transaction,
                                      ServerWebExchange exchange,
                                      boolean endOnComplete,
                                      boolean keepActive,
                                      String description) {

        this.subscriber = subscriber;
        this.exchange = exchange;
        this.endOnComplete = endOnComplete;
        this.description = description;
        this.keepActive = keepActive;
        this.tracer = tracer;

        transactionMap.put(this, transaction);
    }

    @Override
    public void onSubscribe(Subscription s) {
        boolean hasActivated = doEnter(true, "onSubscribe");
        Throwable thrown = null;
        try {
            subscriber.onSubscribe(s);
        } catch (Throwable e) {
            thrown = e;
            throw e;
        } finally {
            keepActiveActivation = hasActivated && keepActive;
            doExit(hasActivated && !keepActive, "onSubscribe");
            discardIf(thrown != null, keepActiveActivation);
        }
    }

    @Override
    public void onNext(T next) {
        boolean hasActivated = doEnter(!keepActive, "onNext");
        Throwable thrown = null;
        try {
            subscriber.onNext(next);
        } catch (Throwable e) {
            thrown = e;
            throw e;
        } finally {
            doExit(hasActivated, "onNext");
            discardIf(thrown != null, keepActiveActivation);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        boolean hasActivated = doEnter(!keepActive, "onError");
        try {
            subscriber.onError(throwable);
        } finally {
            doExit(hasActivated, "onError");
            endTransaction(throwable);
            discardIf(true, keepActiveActivation);
        }
    }

    @Override
    public void onComplete() {
        boolean hasActivated = doEnter(!keepActive, "onComplete");
        try {
            subscriber.onComplete();
        } finally {
            doExit(hasActivated, "onComplete");
            if (endOnComplete) {
                endTransaction(null);
            }
            discardIf(true, keepActiveActivation);
        }
    }

    private boolean doEnter(boolean activate, String method) {
        debugTrace(true, method);
        Transaction transaction = getTransaction();

        if (!activate || transaction == null) {
            return false;
        }

        if (transaction == tracer.getActive()) {
            return false;
        }

        transaction.activate();
        return true;
    }

    private void doExit(boolean deactivate, String method) {
        debugTrace(false, method);
        Transaction transaction = getTransaction();

        if (!deactivate || transaction == null) {
            return;
        }

        transaction.deactivate();
    }

    private void discardIf(boolean condition, boolean deactivateOnDiscard) {
        Transaction transaction = getTransaction();
        if (!condition || transaction == null) {
            return;
        }
        if (deactivateOnDiscard) {
            transaction.deactivate();
        }
        transactionMap.remove(this);
    }

    @Nullable
    private Transaction getTransaction() {
        return transactionMap.get(this);
    }

    private void debugTrace(boolean isEnter, String method) { // TODO : migrate to 'trace'
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug("{} {} {}", isEnter ? ">>>>" : "<<<<", description, method);
    }

    /**
     * Only for testing
     *
     * @return storage map for in-flight transactions
     */
    static WeakConcurrentMap<TransactionAwareSubscriber<?>, Transaction> getTransactionMap() {
        return transactionMap;
    }

    private void endTransaction(@Nullable Throwable thrown) {
        Transaction transaction = getTransaction();
        if (transaction == null) {
            // already discarded
            return;
        }

        Object attribute = exchange.getAttributes().remove(WebFluxInstrumentation.TRANSACTION_ATTRIBUTE);
        if (attribute != transaction) {
            // transaction might be already terminated due to instrumentation of more than one
            // dispatcher/handler/invocation-handler class
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
        if (!isServletTransaction(exchange)) {
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
