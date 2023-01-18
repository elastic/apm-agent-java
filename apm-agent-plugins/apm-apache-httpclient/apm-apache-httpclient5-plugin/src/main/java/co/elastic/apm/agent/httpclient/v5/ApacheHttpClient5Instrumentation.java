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
package co.elastic.apm.agent.httpclient.v5;

import co.elastic.apm.agent.httpclient.HttpClientHelper;
import co.elastic.apm.agent.httpclient.v5.helper.RequestHeaderAccessor;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.hc.client5.http.CircularRedirectException;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;

import javax.annotation.Nullable;
import java.net.URISyntaxException;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class ApacheHttpClient5Instrumentation extends BaseApacheHttpClient5Instrumentation {

    public static class HttpClient5Advice {

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onBeforeExecute(@Advice.Argument(0) HttpHost httpHost,
                                             @Advice.Argument(1) ClassicHttpRequest request,
                                             @Advice.Argument(2) HttpContext context) throws URISyntaxException {
            AbstractSpan<?> parent = tracer.getActive();
            if (parent == null) {
                return null;
            }
            Span span = HttpClientHelper.startHttpClientSpan(parent, request.getMethod(), request.getUri(), httpHost.getHostName());
            if (span != null) {
                span.activate();
            }
            if (!TraceContext.containsTraceContextTextHeaders(request, RequestHeaderAccessor.INSTANCE)) {
                if (span != null) {
                    span.propagateTraceContext(request, RequestHeaderAccessor.INSTANCE);
                } else if (!TraceContext.containsTraceContextTextHeaders(request, RequestHeaderAccessor.INSTANCE)) {
                    // re-adds the header on redirects
                    parent.propagateTraceContext(request, RequestHeaderAccessor.INSTANCE);
                }
            }
            return span;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onAfterExecute(@Advice.Return @Nullable CloseableHttpResponse response,
                                          @Advice.Enter @Nullable Object spanObj,
                                          @Advice.Thrown @Nullable Throwable t) {
            Span span = (Span) spanObj;
            if (span == null) {
                return;
            }
            try {
                if (response != null) {
                    int statusCode = response.getCode();
                    span.getContext().getHttp().withStatusCode(statusCode);
                }
                span.captureException(t);
            } finally {
                if (t instanceof CircularRedirectException) {
                    span.withOutcome(Outcome.FAILURE);
                }

                span.deactivate().end();
            }
        }
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.httpclient.v5.ApacheHttpClient5Instrumentation$HttpClient5Advice";
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("org.apache.hc.client5.http.impl.classic.CloseableHttpClient"));
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("HttpClient");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("doExecute")
            .and(takesArguments(3))
            .and(returns(hasSuperType(named("org.apache.hc.client5.http.impl.classic.CloseableHttpResponse"))))
            .and(takesArgument(0, hasSuperType(named("org.apache.hc.core5.http.HttpHost"))))
            .and(takesArgument(1, hasSuperType(named("org.apache.hc.core5.http.ClassicHttpRequest"))))
            .and(takesArgument(2, hasSuperType(named("org.apache.hc.core5.http.protocol.HttpContext"))));
    }
}
