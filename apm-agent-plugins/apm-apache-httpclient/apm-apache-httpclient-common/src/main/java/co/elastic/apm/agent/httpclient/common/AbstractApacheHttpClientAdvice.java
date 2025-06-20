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
package co.elastic.apm.agent.httpclient.common;


import co.elastic.apm.agent.httpclient.HttpClientHelper;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.TraceState;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderSetter;

import javax.annotation.Nullable;
import java.net.URISyntaxException;

public abstract class AbstractApacheHttpClientAdvice {

    public static <REQUEST, WRAPPER extends REQUEST, HTTPHOST, RESPONSE,
        HeaderAccessor extends TextHeaderSetter<REQUEST> &
            TextHeaderGetter<REQUEST>, HTTPENTITY> Span<?> startSpan(final Tracer tracer,
                                                                     final ApacheHttpClientApiAdapter<REQUEST, WRAPPER, HTTPHOST, RESPONSE, HTTPENTITY> adapter,
                                                                     final WRAPPER request,
                                                                     @Nullable final HTTPHOST httpHost,
                                                                     final HeaderAccessor headerAccessor) throws URISyntaxException {
        TraceState<?> traceState = tracer.currentContext();
        Span<?> span = null;
        if (traceState.getSpan() != null) {
            span = HttpClientHelper.startHttpClientSpan(traceState, adapter.getMethod(request), adapter.getUri(request), adapter.getHostName(httpHost, request));
            if (span != null) {
                span.activate();
            }
        }
        tracer.currentContext().propagateContext(request, headerAccessor, headerAccessor);
        return span;
    }

    public static <REQUEST, WRAPPER extends REQUEST, HTTPHOST, RESPONSE, HTTPENTITY>
    void endSpan(ApacheHttpClientApiAdapter<REQUEST, WRAPPER, HTTPHOST, RESPONSE, HTTPENTITY> adapter,
                 Object spanObj,
                 Throwable t,
                 RESPONSE response) {
        Span<?> span = (Span<?>) spanObj;
        if (span == null) {
            return;
        }
        try {
            if (response != null && adapter.isNotNullStatusLine(response)) {
                int statusCode = adapter.getResponseCode(response);
                span.getContext().getHttp().withStatusCode(statusCode);
            }
            span.captureException(t);
        } finally {
            // in case of circular redirect, we get an exception but status code won't be available without response
            // thus we have to deal with span outcome directly
            if (adapter.isCircularRedirectException(t)) {
                span.withOutcome(Outcome.FAILURE);
            }
            span.deactivate().end();
        }
    }
}
