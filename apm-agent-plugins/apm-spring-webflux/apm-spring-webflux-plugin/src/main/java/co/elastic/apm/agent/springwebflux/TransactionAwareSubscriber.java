/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.context.Response;
import co.elastic.apm.agent.impl.context.web.ResultUtil;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.sdk.weakmap.WeakMapSupplier;
import co.elastic.apm.agent.util.PotentiallyMultiValuedMap;
import co.elastic.apm.agent.util.SpanConcurrentHashMap;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.MultiValueMap;
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
import static org.springframework.web.reactive.function.server.RouterFunctions.MATCHING_PATTERN_ATTRIBUTE;

/**
 * Transaction-aware subscriber that will (optionally) activate transaction and terminate it on error or completion.
 *
 * @param <T>
 */
class TransactionAwareSubscriber<T> implements CoreSubscriber<T> {

    private static final Logger log = LoggerFactory.getLogger(TransactionAwareSubscriber.class);

    private static final WeakConcurrentMap<HandlerMethod, Boolean> ignoredHandlerMethods = WeakMapSupplier.createMap();

    private static final WeakConcurrentMap<TransactionAwareSubscriber<?>, Transaction> transactionMap = SpanConcurrentHashMap.createWeakMap();

    private static final CoreConfiguration config;

    private final CoreSubscriber<? super T> subscriber;

    private final ServerWebExchange exchange;

    private final String description;

    private final Tracer tracer;

    /**
     * {@literal true} when transaction was activated on subscription
     */
    private boolean activatedOnSubscribe = false;

    static {
        config = GlobalTracer.requireTracerImpl().getConfig(CoreConfiguration.class);
    }

    /**
     * @param subscriber  subscriber to wrap
     * @param transaction transaction
     * @param exchange    server web exchange
     * @param description human-readable description to make debugging easier
     */
    TransactionAwareSubscriber(CoreSubscriber<? super T> subscriber,
                               Tracer tracer,
                               Transaction transaction,
                               ServerWebExchange exchange,
                               String description) {

        this.subscriber = subscriber;
        this.exchange = exchange;
        this.description = description;
        this.tracer = tracer;

        transactionMap.put(this, transaction);
    }

    /**
     * Wraps {@link Subscriber#onSubscribe(Subscription)} for context propagation, executed in "subscribe scheduler".
     * Might activate transaction if not already active. When activating the transaction is kept active after method execution.
     * Refer to {@link #doEnter} for details on activation.
     */
    @Override
    public void onSubscribe(Subscription s) {
        Transaction transaction = getTransaction();
        doEnter(true, "onSubscribe", transaction);
        Throwable thrown = null;
        try {
            subscriber.onSubscribe(s);
        } catch (Throwable e) {
            thrown = e;
            throw e;
        } finally {
            doExit(thrown != null, "onSubscribe", transaction);
        }
    }

    /**
     * Wraps {@link Subscriber#onNext(Object)} for context propagation, executed in "publisher scheduler".
     * Assumes the transaction is already active, will discard transaction reference if any exception is thrown.
     *
     * @param next next item
     */
    @Override
    public void onNext(T next) {
        Transaction transaction = getTransaction();
        doEnter(false, "onNext", transaction);
        Throwable thrown = null;
        try {
            subscriber.onNext(next);
        } catch (Throwable e) {
            thrown = e;
            throw e;
        } finally {
            doExit(thrown != null, "onNext", transaction);
        }
    }

    /**
     * Wraps {@link Subscriber#onError(Throwable)} for context propagation, executed in "publisher scheduler".
     * Assumes the transaction is already active, will terminate transaction and optionally deactivate if it was
     * previously activated by {@link #onSubscribe(Subscription)}.
     *
     * @param t error
     */
    @Override
    public void onError(Throwable t) {
        Transaction transaction = getTransaction();
        doEnter(false, "onError", transaction);
        try {
            subscriber.onError(t);
        } finally {
            endTransaction(t, transaction);
            doExit(true, "onError", transaction);
        }
    }

    /**
     * Wraps {@link Subscriber#onComplete()} for context propagation, executed in "publisher scheduler".
     * Assumes the transaction is already active, will terminate transaction and optionally deactivate if it was
     * previously activated by {@link #onSubscribe(Subscription)}.
     */
    @Override
    public void onComplete() {
        Transaction transaction = getTransaction();
        doEnter(false, "onComplete", transaction);
        try {
            subscriber.onComplete();
        } finally {
            endTransaction(null, transaction);
            doExit(true, "onComplete", transaction);
        }
    }

    private void doEnter(boolean isSubscribe, String method, @Nullable Transaction transaction) {
        debugTrace(true, method, transaction);

        if (!isSubscribe || transaction == null) {
            return;
        }

        if (transaction == tracer.getActive()) {
            activatedOnSubscribe = false;
            return;
        }

        transaction.activate();
        activatedOnSubscribe = true;
    }

    private void doExit(boolean discard, String method, @Nullable Transaction transaction) {
        debugTrace(false, method, transaction);

        if (transaction == null) {
            return;
        }

        if (discard) {
            if (activatedOnSubscribe && tracer.getActive() == transaction) {
                transaction.deactivate();
            }
            transactionMap.remove(this);
        }

    }

    @Nullable
    private Transaction getTransaction() {
        return transactionMap.get(this);
    }

    private void debugTrace(boolean isEnter, String method, @Nullable Transaction transaction) {
        if (!log.isTraceEnabled()) {
            return;
        }
        log.trace("{} {} {} {}", isEnter ? ">>>>" : "<<<<", description, method, transaction);
    }

    /**
     * Only for testing
     *
     * @return storage map for in-flight transactions
     */
    static WeakConcurrentMap<TransactionAwareSubscriber<?>, Transaction> getTransactionMap() {
        return transactionMap;
    }

    private void endTransaction(@Nullable Throwable thrown, @Nullable Transaction transaction) {
        if (transaction == null) {
            // already discarded
            return;
        }

        Object attribute = exchange.getAttributes().remove(WebfluxHelper.TRANSACTION_ATTRIBUTE);
        if (attribute != transaction) {
            // transaction might be already terminated due to instrumentation of more than one
            // dispatcher/handler/invocation-handler class
            return;
        }

        if (ignoreTransaction(exchange)) {
            transaction.ignoreTransaction();
            transaction.end();
            return;
        }

        StringBuilder name = transaction.getAndOverrideName(PRIO_HIGH_LEVEL_FRAMEWORK, false);
        if (name != null) {
            // set name from matching pattern & unknown
            name.append(exchange.getRequest().getMethodValue())
                .append(' ');
            PathPattern pattern = exchange.getAttribute(MATCHING_PATTERN_ATTRIBUTE);
            if (pattern != null) {
                name.append(pattern.getPatternString());
            } else {
                name.append("unknown route");
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
        if (!WebfluxHelper.isServletTransaction(exchange)) {
            transaction.end();
        }

    }

    private static boolean ignoreTransaction(ServerWebExchange exchange) {
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
            if (genReturnTypes[i].getTypeName().startsWith(WebfluxHelper.SSE_EVENT_CLASS)) {
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

        if (config.isCaptureHeaders()) {
            copyHeaders(serverRequest.getHeaders(), request.getHeaders());
            copyCookies(serverRequest.getCookies(), request.getCookies());
        }

    }


    private static void fillResponse(Transaction transaction, ServerWebExchange exchange) {
        ServerHttpResponse serverResponse = exchange.getResponse();
        HttpStatus statusCode = serverResponse.getStatusCode();
        int status = statusCode != null ? statusCode.value() : 200;

        transaction.withResultIfUnset(ResultUtil.getResultByHttpStatus(status));

        Response response = transaction.getContext().getResponse();

        if (config.isCaptureHeaders()) {
            copyHeaders(serverResponse.getHeaders(), response.getHeaders());
        }

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

    private static void copyCookies(MultiValueMap<String, HttpCookie> source, PotentiallyMultiValuedMap destination) {
        for (Map.Entry<String, List<HttpCookie>> cookie : source.entrySet()) {
            for (HttpCookie value : cookie.getValue()) {
                destination.add(value.getName(), value.getValue());
            }
        }
    }

}
