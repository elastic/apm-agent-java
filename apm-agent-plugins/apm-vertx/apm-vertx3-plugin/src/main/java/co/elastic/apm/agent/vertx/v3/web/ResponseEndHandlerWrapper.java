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
package co.elastic.apm.agent.vertx.v3.web;

import co.elastic.apm.agent.impl.transaction.Transaction;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;

import javax.annotation.Nullable;

public class ResponseEndHandlerWrapper implements Handler<Void> {

    private static final WebHelper helper = WebHelper.getInstance();

    @Nullable
    private Handler<Void> actualHandler;
    private final Transaction transaction;
    private final HttpServerResponse response;
    @Nullable
    private final HttpServerRequest request;

    public ResponseEndHandlerWrapper(Transaction transaction, HttpServerResponse response, @Nullable HttpServerRequest request) {
        this.transaction = transaction;
        this.response = response;
        this.request = request;
    }

    public ResponseEndHandlerWrapper(Transaction transaction, HttpServerResponse response) {
        this(transaction, response, null);
    }

    public void setActualHandler(Handler<Void> actualHandler) {
        this.actualHandler = actualHandler;
    }

    @Override
    public void handle(Void event) {
        try {
            if (actualHandler != null) {
                actualHandler.handle(event);
            }
        } finally {
            helper.finalizeTransaction(response, transaction);
            if (request != null) {
                helper.removeTransactionFromContext(request);
            }
        }
    }
}
