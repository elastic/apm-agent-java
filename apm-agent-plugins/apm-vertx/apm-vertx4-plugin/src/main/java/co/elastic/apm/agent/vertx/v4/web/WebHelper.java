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
package co.elastic.apm.agent.vertx.v4.web;

import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.vertx.AbstractVertxWebHelper;
import io.vertx.core.Context;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;

import javax.annotation.Nullable;

public class WebHelper extends AbstractVertxWebHelper {

    private static final WebHelper INSTANCE = new WebHelper(GlobalTracer.get());

    public static WebHelper getInstance() {
        return INSTANCE;
    }

    private WebHelper(Tracer tracer) {
        super(tracer);
    }

    @Nullable
    public Transaction<?> startOrGetTransaction(Context context, HttpServerRequest httpServerRequest) {
        Transaction<?> transaction = super.startOrGetTransaction(httpServerRequest);

        if (transaction != null) {
            enrichRequest(httpServerRequest, transaction);
            context.putLocal(CONTEXT_TRANSACTION_KEY, transaction);
        }

        return transaction;
    }

    @Nullable
    @Override
    public Transaction<?> setRouteBasedNameForCurrentTransaction(RoutingContext routingContext) {
        Context context = routingContext.vertx().getOrCreateContext();
        Transaction<?> transaction = context.getLocal(CONTEXT_TRANSACTION_KEY);
        if (transaction != null) {
            setRouteBasedTransactionName(transaction, routingContext);
        }
        return transaction;
    }

}
