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
import co.elastic.apm.agent.sdk.state.CallDepth;
import co.elastic.apm.agent.util.PotentiallyMultiValuedMap;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import reactor.core.CoreSubscriber;

import javax.annotation.Nullable;
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
public class TransactionAwareSubscriber<T> extends TracedSubscriber<T,Transaction> {

    private final ServerWebExchange exchange;
    private final boolean endOnComplete;

    public TransactionAwareSubscriber(CoreSubscriber<? super T> subscriber,
                                      Transaction transaction,
                                      ServerWebExchange exchange,
                                      boolean endOnComplete) {

        super(subscriber, transaction);
        this.exchange = exchange;
        this.endOnComplete = endOnComplete;
    }

    @Override
    public void onError(Throwable throwable) {
        super.onError(throwable);
        endTransaction(getContext(), exchange, throwable);
    }

    @Override
    public void onComplete() {
        super.onComplete();

        if (endOnComplete) {
            endTransaction(getContext(), exchange, null);
        }
    }

    static void endTransaction(Transaction transaction, ServerWebExchange exchange, @Nullable Throwable thrown) {
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

        // when transaction has been created by servlet (or any other low-level HTTP instrumentation), we let
        // this other instrumentation handle request/response capture and end the transaction lifecycle
        //
        // This assumes that request details have been already captured, if not, we might terminate transaction
        // a bit early as if it was created by webflux instrumentation.
        if (!transaction.getContext().getRequest().hasContent()) {
            fillRequest(transaction, exchange);
            fillResponse(transaction, exchange);
        }

        transaction.captureException(thrown);

        // in case transaction has been created by servlet, we should not terminate it
        if (Boolean.TRUE != exchange.getAttributes().get(SERVLET_TRANSACTION)) {
            transaction.end();
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
