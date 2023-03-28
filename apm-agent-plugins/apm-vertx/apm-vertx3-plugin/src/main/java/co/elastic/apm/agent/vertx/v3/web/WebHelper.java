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
package co.elastic.apm.agent.vertx.v3.web;

import co.elastic.apm.agent.collections.WeakConcurrentProviderImpl;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import co.elastic.apm.agent.vertx.AbstractVertxWebHelper;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;

import javax.annotation.Nullable;

public class WebHelper extends AbstractVertxWebHelper {

    private static final Logger log = LoggerFactory.getLogger(WebHelper.class);

    private static final WebHelper INSTANCE = new WebHelper(GlobalTracer.get());

    static final WeakMap<Object, Transaction<?>> transactionMap = WeakConcurrentProviderImpl.createWeakSpanMap();

    public static WebHelper getInstance() {
        return INSTANCE;
    }

    // this handler is used to mark the instrumented call of HttpServerRequestWrapper.endHandler(...) as a call
    // that should do nothing other than returning the wrapped delegate instance of type HttpServerRequestImpl.
    private final NoopHandler noopHandler = new NoopHandler();

    WebHelper(Tracer tracer) {
        super(tracer);
    }

    @Nullable
    public Transaction<?> startOrGetTransaction(HttpServerRequest httpServerRequest) {
        Transaction<?> transaction = super.startOrGetTransaction(httpServerRequest);

        if (transaction != null) {
            mapTransaction(httpServerRequest, transaction);
            enrichRequest(httpServerRequest, transaction);
        }

        return transaction;
    }

    @Override
    public Transaction<?> setRouteBasedNameForCurrentTransaction(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        Transaction<?> transaction = getTransactionForRequest(request);

        if (transaction != null) {
            setRouteBasedTransactionName(transaction, routingContext);
        }

        return transaction;
    }

    public void mapTransaction(Object key, Transaction<?> transaction) {
        transactionMap.put(key, transaction);
    }

    @Nullable
    public Transaction<?> lookupTransaction(Object key) {
        return transactionMap.get(key);
    }

    @Nullable
    public Transaction<?> removeTransactionMapping(Object key) {
        return transactionMap.remove(key);
    }

    @Nullable
    public Transaction<?> getTransactionForRequest(HttpServerRequest request) {
        if (request.getClass().getName().equals("io.vertx.ext.web.impl.HttpServerRequestWrapper")) {
            request = request.endHandler(noopHandler);
            log.debug("VERTX-DEBUG: Vert.x request obtained through endHandler instrumentation: {}", request);
        }
        Transaction<?> transaction = lookupTransaction(request);
        if (transaction != null) {
            log.debug("VERTX-DEBUG: transaction {} is mapped to the Vert.x request: {}", transaction, request);
        } else {
            // If transaction already ended and removed, there may still be handling in HTTP 2
            transaction = lookupTransaction(request.response());
        }
        return transaction;
    }

    public static class NoopHandler implements Handler<Void> {

        @Override
        public void handle(Void event) {
            // this method should never be called
        }
    }
}
