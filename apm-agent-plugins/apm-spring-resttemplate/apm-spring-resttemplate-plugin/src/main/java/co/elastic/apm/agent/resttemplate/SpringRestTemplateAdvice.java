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
package co.elastic.apm.agent.resttemplate;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import net.bytebuddy.asm.Advice;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.util.Objects;

public class SpringRestTemplateAdvice {

    private static final Logger logger = LoggerFactory.getLogger(SpringRestTemplateAdvice.class);

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object beforeExecute(@Advice.This ClientHttpRequest request) {
        logger.trace("Enter advice for method {}#execute()", request.getClass().getName());

        final AbstractSpan<?> parent = TracerAwareInstrumentation.tracer.getActive();
        if (parent == null) {
            return null;
        }
        URI uri = request.getURI();
        Span span = HttpClientHelper.startHttpClientSpan(parent, Objects.toString(request.getMethod()), uri, uri.getHost());
        if (span != null) {
            span.activate();
            span.propagateTraceContext(request, SpringRestRequestHeaderSetter.INSTANCE);
        } else {
            parent.propagateTraceContext(request, SpringRestRequestHeaderSetter.INSTANCE);
        }
        return span;
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void afterExecute(@Advice.Return @Nullable ClientHttpResponse clientHttpResponse,
                                    @Advice.Enter @Nullable Object spanObj,
                                    @Advice.Thrown @Nullable Throwable t) throws IOException {
        logger.trace("Exit advice for RestTemplate client execute() method, span object: {}", spanObj);
        if (spanObj instanceof Span) {
            Span span = (Span) spanObj;
            try {
                if (clientHttpResponse != null) {
                    // getRawStatusCode has been introduced in 3.1.1
                    span.getContext().getHttp().withStatusCode(clientHttpResponse.getRawStatusCode());
                }
                if (t != null) {
                    span.withOutcome(Outcome.FAILURE);
                }
                span.captureException(t);
            } finally {
                span.deactivate().end();
            }
        }
    }
}
