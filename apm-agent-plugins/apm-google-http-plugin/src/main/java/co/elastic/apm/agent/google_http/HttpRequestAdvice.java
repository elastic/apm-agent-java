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
package co.elastic.apm.agent.google_http;

import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;
import java.net.URI;

public class HttpRequestAdvice {

    private static final Tracer tracer = GlobalTracer.get();

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object onBeforeExecute(@Advice.This HttpRequest httpRequest) {
        AbstractSpan<?> parent = tracer.getActive();
        if (parent == null) {
            return null;
        }

        URI uri = httpRequest.getUrl().toURI();

        Span span = HttpClientHelper.startHttpClientSpan(parent, httpRequest.getRequestMethod(), uri, uri.getHost());
        if (span != null) {
            span.activate();
            span.propagateTraceContext(httpRequest, HttpRequestHeaderSetter.INSTANCE);
        } else {
            parent.propagateTraceContext(httpRequest, HttpRequestHeaderSetter.INSTANCE);
        }

        return span;
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onAfterExecute(@Advice.Return @Nullable HttpResponse httpResponse,
                                      @Advice.Enter @Nullable Object spanObj,
                                      @Advice.Thrown @Nullable Throwable t) {
        if (spanObj instanceof Span) {
            Span span = (Span) spanObj;
            try {
                if (httpResponse != null) {
                    span.getContext().getHttp().withStatusCode(httpResponse.getStatusCode());
                }
                if (t != null) {
                    if (t instanceof HttpResponseException) {
                        span.getContext().getHttp().withStatusCode(((HttpResponseException) t).getStatusCode());
                    } else {
                        span.captureException(t).withOutcome(Outcome.FAILURE);
                    }
                }
            } finally {
                span.deactivate().end();
            }
        }
    }
}
