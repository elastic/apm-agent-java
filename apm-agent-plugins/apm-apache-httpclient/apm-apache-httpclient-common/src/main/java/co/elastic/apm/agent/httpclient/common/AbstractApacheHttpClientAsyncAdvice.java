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
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.TraceState;
import co.elastic.apm.agent.tracer.Tracer;

public abstract class AbstractApacheHttpClientAsyncAdvice {

    public static <PRODUCER, WRAPPER extends PRODUCER, CALLBACK, CALLBACK_WRAPPER extends CALLBACK, CONTEXT> Object[] startSpan(
        Tracer tracer, ApacheHttpClientAsyncHelper<PRODUCER, WRAPPER, CALLBACK, CALLBACK_WRAPPER, CONTEXT> asyncHelper,
        PRODUCER asyncRequestProducer, CONTEXT context, CALLBACK futureCallback) {

        TraceState<?> parentContext = tracer.currentContext();
        if (parentContext.isEmpty()) {
            // performance optimization, no need to wrap if we have nothing to propagate
            // empty context means also we will not create an exit span
            return null;
        }
        CALLBACK wrappedFutureCallback = futureCallback;
        TraceState<?> activeContext = tracer.currentContext();
        Span<?> span = activeContext.createExitSpan();
        if (span != null) {
            span.getContext().getHttp().getRequestBody().markEligibleForCapturing();
            span.withType(HttpClientHelper.EXTERNAL_TYPE)
                .withSubtype(HttpClientHelper.HTTP_SUBTYPE)
                .withSync(false)
                .activate();
            wrappedFutureCallback = asyncHelper.wrapFutureCallback(futureCallback, context, span);
        }
        PRODUCER wrappedProducer = asyncHelper.wrapRequestProducer(asyncRequestProducer, span, tracer.currentContext());
        return new Object[]{wrappedProducer, wrappedFutureCallback, span};
    }

    public static <PRODUCER, WRAPPER extends PRODUCER, CALLBACK, CALLBACK_WRAPPER extends CALLBACK, CONTEXT> void endSpan(
        ApacheHttpClientAsyncHelper<PRODUCER, WRAPPER, CALLBACK, CALLBACK_WRAPPER, CONTEXT> asyncHelper, Object[] enter, Throwable t) {
        Span<?> span = enter != null ? (Span<?>) enter[2] : null;
        if (span != null) {
            WRAPPER wrapper = (WRAPPER) enter[0];
            if (t != null) {
                // The method terminated with an exception.
                // The listener who normally does the ending will not be invoked.
                span.captureException(t);
                span.withOutcome(co.elastic.apm.agent.tracer.Outcome.FAILURE);
                span.deactivate().end();
                asyncHelper.recycle(wrapper);
            } else {
                // Deactivate in this thread, the span is continued and ended by the callback
                span.deactivate();
            }
        }
    }
}
