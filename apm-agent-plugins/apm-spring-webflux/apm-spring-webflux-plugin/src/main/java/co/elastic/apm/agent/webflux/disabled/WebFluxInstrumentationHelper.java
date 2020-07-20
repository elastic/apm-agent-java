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
package co.elastic.apm.agent.webflux.disabled;

import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.server.ServerRequest;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

public class WebFluxInstrumentationHelper {

    public static final String ELASTIC_APM_AGENT_TRANSACTION = Transaction.class.getName();
    public static final String TRANSACTION_TYPE = "request";
    public static final String CONTENT_LENGTH = "Content-Length";

    public static Transaction createAndActivateTransaction(final Tracer tracer, final ServerRequest serverRequest) {
        final HttpMethod method = serverRequest.method();
        final String name = method == null ? serverRequest.path() : method.name() + " " + serverRequest.path();
        final Transaction transaction = tracer.startRootTransaction(serverRequest.getClass().getClassLoader())
            .withName(name)
            .withType(TRANSACTION_TYPE);
        final List<String> values = serverRequest.headers().header(CONTENT_LENGTH);
        if (values.size() == 1) {
            transaction.getContext().addCustom(WebFluxInstrumentationHelper.CONTENT_LENGTH, values.get(0));
        }
        transaction.activate();
        return transaction;
    }

    // TODO: 23/06/2020 not sure if it's required as serlvet plugin should already take care of transaction
    public static Transaction createAndActivateTransaction(Tracer tracer, ServletRequest servletRequest) {
        if (!(servletRequest instanceof HttpServletRequest)) {
            return null;
        }

        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        final String method = httpServletRequest.getMethod();
        final String path = httpServletRequest.getRequestURI();
        final String name = method + " " + path;
        final Transaction transaction = tracer.startRootTransaction(servletRequest.getClass().getClassLoader())
            .withName(name)
            .withType(TRANSACTION_TYPE);
        final long contentLength = servletRequest.getContentLength();
        transaction.getContext().addCustom(WebFluxInstrumentationHelper.CONTENT_LENGTH, contentLength);
        transaction.activate();
        return transaction;
    }

}
