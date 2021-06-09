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
package co.elastic.apm.agent.vertx;

import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.ext.web.client.impl.HttpContext;

import javax.annotation.Nullable;


public abstract class AbstractVertxWebClientHelper {

    private static final String WEB_CLIENT_SPAN_KEY = AbstractVertxWebClientHelper.class.getName() + ".span";

    static class HeaderSetter implements TextHeaderSetter<HttpClientRequest> {

        public static final HeaderSetter INSTANCE = new HeaderSetter();

        @Override
        public void setHeader(String headerName, String headerValue, HttpClientRequest carrier) {
            carrier.putHeader(headerName, headerValue);
        }
    }

    public void startSpan(AbstractSpan<?> parent, HttpContext<?> httpContext, HttpClientRequest httpRequest) {
        Object existingSpanObj = httpContext.get(WEB_CLIENT_SPAN_KEY);
        if (existingSpanObj != null) {
            // there is already an active span for this HTTP request,
            // don't create a new span but propagate tracing headers
            ((Span) existingSpanObj).propagateTraceContext(httpRequest, HeaderSetter.INSTANCE);
        } else {
            String fullURI = httpRequest.absoluteURI();

            int schemeSeparatorIdx = fullURI.indexOf("://");
            int authoritySeparatorIdx = fullURI.indexOf('/', schemeSeparatorIdx + 3);
            authoritySeparatorIdx = authoritySeparatorIdx >= 0 ? authoritySeparatorIdx : fullURI.length();

            String scheme = fullURI.substring(0, schemeSeparatorIdx);
            String host = fullURI.substring(schemeSeparatorIdx + 3, authoritySeparatorIdx);

            int hostPortSeparatorIdx = host.indexOf(':');

            int port = scheme.equals("https") ? 443 : 80;

            if (hostPortSeparatorIdx > 0) {
                port = Integer.parseInt(host.substring(hostPortSeparatorIdx + 1));
                host = host.substring(0, hostPortSeparatorIdx);
            }
            Span span = HttpClientHelper.startHttpClientSpan(parent, getMethod(httpRequest), fullURI, scheme, host, port);

            if (span != null) {
                span.propagateTraceContext(httpRequest, HeaderSetter.INSTANCE);
                span.incrementReferences();
                httpContext.set(WEB_CLIENT_SPAN_KEY, span);
            }
        }
    }

    public void followRedirect(HttpContext<?> httpContext, HttpClientRequest httpRequest) {
        Object existingSpanObj = httpContext.get(WEB_CLIENT_SPAN_KEY);
        if (existingSpanObj != null) {
            Span existingSpan = (Span) existingSpanObj;
            existingSpan.propagateTraceContext(httpRequest, HeaderSetter.INSTANCE);
        }
    }

    public void endSpan(HttpContext<?> httpContext, HttpClientResponse httpResponse) {
        if (httpResponse.statusCode() < 300 || httpResponse.statusCode() >= 400) {
            finalizeSpan(httpContext, httpResponse.statusCode(), null);
        }
    }

    public void failSpan(HttpContext<?> httpContext, Throwable thrown) {
        finalizeSpan(httpContext, 0, thrown);
    }

    private void finalizeSpan(HttpContext<?> httpContext, int statusCode, @Nullable Throwable thrown) {
        Object spanObj = httpContext.get(WEB_CLIENT_SPAN_KEY);

        if (spanObj != null) {
            // Setting to null removes from the attributes map
            httpContext.set(WEB_CLIENT_SPAN_KEY, null);

            Span span = (Span) spanObj;
            span.decrementReferences();

            if (thrown != null) {
                span.captureException(thrown).withOutcome(Outcome.FAILURE);
            }

            if (statusCode > 0) {
                span.getContext().getHttp().withStatusCode(statusCode);
            }

            span.end();

        }
    }

    protected abstract String getMethod(HttpClientRequest request);
}
