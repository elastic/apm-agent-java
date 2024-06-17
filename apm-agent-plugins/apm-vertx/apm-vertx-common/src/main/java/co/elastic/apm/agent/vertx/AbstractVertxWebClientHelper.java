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
package co.elastic.apm.agent.vertx;

import co.elastic.apm.agent.httpclient.HttpClientHelper;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.TraceState;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderSetter;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.impl.HttpContext;

import javax.annotation.Nullable;
import java.net.URI;


public abstract class AbstractVertxWebClientHelper {

    private static final String WEB_CLIENT_SPAN_KEY = AbstractVertxWebClientHelper.class.getName() + ".span";
    private static final String PROPAGATION_CONTEXT_KEY = AbstractVertxWebClientHelper.class.getName() + ".propCtx";

    private final Tracer tracer = GlobalTracer.get();

    static class HeaderSetter implements TextHeaderSetter<HttpClientRequest> {

        public static final HeaderSetter INSTANCE = new HeaderSetter();

        @Override
        public void setHeader(String headerName, String headerValue, HttpClientRequest carrier) {
            carrier.putHeader(headerName, headerValue);
        }
    }

    public void startSpanOrFollowRedirect(TraceState<?> activeContext, HttpContext<?> httpContext, HttpClientRequest httpRequest) {
        TraceState<?> existingPropagationCtx = httpContext.get(PROPAGATION_CONTEXT_KEY);

        if (existingPropagationCtx != null) {
            // Repropagate headers in case of redirects
            existingPropagationCtx.propagateContext(httpRequest, HeaderSetter.INSTANCE, null);
            return;
        }
        if (activeContext.isEmpty()) {
            return; //Nothing to propagate and we'll never start an exit span due to missing transaction
        }

        URI requestUri = URI.create(httpRequest.absoluteURI());
        Span<?> span = HttpClientHelper.startHttpClientSpan(activeContext, getMethod(httpRequest), requestUri, null);
        TraceState<?> toPropagate = activeContext;
        if (span != null) {
            //no need to increment references of the span, the span will be kept alive by the incrementReferences() on the context below
            httpContext.set(WEB_CLIENT_SPAN_KEY, span);
            span.activate();
            toPropagate = tracer.currentContext();
            span.deactivate();
        }

        toPropagate.incrementReferences();
        httpContext.set(PROPAGATION_CONTEXT_KEY, toPropagate);
        toPropagate.propagateContext(httpRequest, HeaderSetter.INSTANCE, null);
    }

    public void followRedirect(HttpContext<?> httpContext, HttpClientRequest httpRequest) {
        TraceState<?> existingPropagationCtx = httpContext.get(PROPAGATION_CONTEXT_KEY);
        if (existingPropagationCtx != null) {
            existingPropagationCtx.propagateContext(httpRequest, HeaderSetter.INSTANCE, null);
        }
    }

    public void endSpan(HttpContext<?> httpContext, HttpResponse<?> httpResponse) {
        finalizeSpan(httpContext, httpResponse.statusCode(), null, null);
    }

    public void failSpan(HttpContext<?> httpContext, Throwable thrown, @Nullable AbstractSpan<?> parent) {
        finalizeSpan(httpContext, 0, thrown, parent);
    }

    private void finalizeSpan(HttpContext<?> httpContext, int statusCode, @Nullable Throwable thrown, @Nullable AbstractSpan<?> parent) {
        Span<?> span = httpContext.get(WEB_CLIENT_SPAN_KEY);
        TraceState<?> propagationCtx = httpContext.get(PROPAGATION_CONTEXT_KEY);
        if (propagationCtx != null) {
            // Setting to null removes from the attributes map
            httpContext.set(WEB_CLIENT_SPAN_KEY, null);
            httpContext.set(PROPAGATION_CONTEXT_KEY, null);
            try {
                if (span != null) {
                    if (thrown != null) {
                        span.captureException(thrown).withOutcome(Outcome.FAILURE);
                    }
                    if (statusCode > 0) {
                        span.getContext().getHttp().withStatusCode(statusCode);
                    }
                    span.end();
                }
            } finally {
                propagationCtx.decrementReferences();
            }
        } else if (parent != null) {
            parent.captureException(thrown);
        }
    }

    protected abstract String getMethod(HttpClientRequest request);
}
