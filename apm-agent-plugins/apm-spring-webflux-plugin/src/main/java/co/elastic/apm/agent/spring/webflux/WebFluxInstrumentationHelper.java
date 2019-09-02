/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.spring.webflux;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;

public class WebFluxInstrumentationHelper {

    public static final String TRANSACTION_TYPE = "serverRequest";

    public static final String CONTENT_LENGTH = "Content-Length";

    public static ServerWebExchange findServerWebExchange(final Object[] args) {
        for (Object arg : args) {
            if (arg instanceof ServerWebExchange) {
                return ((ServerWebExchange) arg);
            }
        }
        return null;
    }

    public static ServerHttpRequest findServerHttpRequest(final Object[] args) {
        for (Object arg : args) {
            if (arg instanceof ServerHttpRequest) {
                return ((ServerHttpRequest) arg);
            }
        }
        return null;
    }

    public static Transaction createAndActivateTransaction(final ElasticApmTracer tracer, final ServerRequest serverRequest) {
        final HttpMethod method = serverRequest.method();
        final String name = method == null ? serverRequest.path() : method.name() + " " + serverRequest.path();
        final Transaction transaction = tracer.startTransaction(TraceContext.fromTraceparentHeader(), null, null)
            .withName(name)
            .withType(TRANSACTION_TYPE);
        transaction.getContext().addCustom(WebFluxInstrumentationHelper.CONTENT_LENGTH,
            serverRequest.headers().contentLength().orElse(0L));
        transaction.activate();
        return transaction;
    }

    public static Transaction createAndActivateTransaction(final ElasticApmTracer tracer, final ServerWebExchange serverWebExchange) {
        return createAndActivateTransaction(tracer, serverWebExchange.getRequest());
    }

    public static Transaction createAndActivateTransaction(final ElasticApmTracer tracer, final ServerHttpRequest serverHttpRequest) {
        final HttpMethod method = serverHttpRequest.getMethod();
        final String path = serverHttpRequest.getURI().getPath();
        final String name = method == null ? path : method.name() + " " + path;
        final Transaction transaction = tracer.startTransaction(TraceContext.fromTraceparentHeader(), null, null)
            .withName(name)
            .withType(TRANSACTION_TYPE);
        final HttpHeaders headers = serverHttpRequest.getHeaders();
        final long contentLength = headers.getContentLength();
        transaction.getContext().addCustom(WebFluxInstrumentationHelper.CONTENT_LENGTH, contentLength);
        transaction.activate();
        return transaction;
    }
}
