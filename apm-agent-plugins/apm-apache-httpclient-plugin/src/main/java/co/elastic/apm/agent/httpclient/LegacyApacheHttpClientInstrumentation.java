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
package co.elastic.apm.agent.httpclient;

import co.elastic.apm.agent.httpclient.helper.RequestHeaderAccessor;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.CircularRedirectException;
import org.apache.http.client.methods.HttpUriRequest;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

@SuppressWarnings("Duplicates")
public class LegacyApacheHttpClientInstrumentation extends BaseApacheHttpClientInstrumentation {

    public static class LegacyApacheHttpClientAdvice {
        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onBeforeExecute(@Advice.Argument(0) @Nullable HttpHost host,
                                             @Advice.Argument(1) HttpRequest request) {
            final AbstractSpan<?> parent = tracer.getActive();
            if (parent == null) {
                return null;
            }
            if (!(request instanceof HttpUriRequest)) {
                return null;
            }
            HttpUriRequest uriRequest = (HttpUriRequest) request;
            String hostName = (host != null) ? host.getHostName() : null;
            Span span = HttpClientHelper.startHttpClientSpan(parent, uriRequest.getMethod(), uriRequest.getURI(), hostName);

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
        public static void onAfterExecute(@Advice.Return @Nullable HttpResponse response,
                                          @Advice.Enter @Nullable Object spanObj,
                                          @Advice.Thrown @Nullable Throwable t) {
            Span span = (Span) spanObj;
            if (span == null) {
                return;
            }
            try {
                if (response != null && response.getStatusLine() != null) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    span.getContext().getHttp().withStatusCode(statusCode);
                }
                span.captureException(t);
            } finally {
                // in case of circular redirect, we get an exception but status code won't be available without response
                // thus we have to deal with span outcome directly
                if (t instanceof CircularRedirectException) {
                    span.withOutcome(Outcome.FAILURE);
                }

                span.deactivate().end();
            }
        }
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.httpclient.LegacyApacheHttpClientInstrumentation$LegacyApacheHttpClientAdvice";
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Director");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("org.apache.http.client.RequestDirector"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("execute")
            .and(takesArguments(3))
            .and(returns(hasSuperType(named("org.apache.http.HttpResponse"))))
            .and(takesArgument(0, hasSuperType(named("org.apache.http.HttpHost"))))
            .and(takesArgument(1, hasSuperType(named("org.apache.http.HttpRequest"))))
            .and(takesArgument(2, hasSuperType(named("org.apache.http.protocol.HttpContext"))));
    }
}
