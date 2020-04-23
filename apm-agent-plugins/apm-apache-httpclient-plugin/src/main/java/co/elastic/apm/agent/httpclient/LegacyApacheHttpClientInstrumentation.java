/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent.httpclient;

import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
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

    public LegacyApacheHttpClientInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
    }

    private static class LegacyApacheHttpClientAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onBeforeExecute(@Advice.Argument(0) HttpHost host,
                                            @Advice.Argument(1) HttpRequest request,
                                            @Advice.Local("span") Span span) {
            if (tracer == null || tracer.getActive() == null) {
                return;
            }
            final AbstractSpan<?> parent = tracer.getActive();
            String method;
            if (request instanceof HttpUriRequest) {
                HttpUriRequest uriRequest = (HttpUriRequest) request;
                span = HttpClientHelper.startHttpClientSpan(parent, uriRequest.getMethod(), uriRequest.getURI(), host.getHostName());
                TextHeaderSetter<HttpRequest> headerSetter = headerSetterHelperClassManager.getForClassLoaderOfClass(HttpRequest.class);
                TextHeaderGetter<HttpRequest> headerGetter = headerGetterHelperClassManager.getForClassLoaderOfClass(HttpRequest.class);
                if (span != null) {
                    span.activate();
                    if (headerSetter != null) {
                        span.setOutgoingTraceContextHeaders(request, headerSetter);
                    }
                } else if (headerGetter != null && !TraceContext.containsTraceContextTextHeaders(request, headerGetter)
                    && headerSetter != null && parent != null) {
                    // re-adds the header on redirects
                    parent.setOutgoingTraceContextHeaders(request, headerSetter);
                }

            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void onAfterExecute(@Advice.Return @Nullable HttpResponse response,
                                          @Advice.Local("span") @Nullable Span span,
                                          @Advice.Thrown @Nullable Throwable t) {
            if (span != null) {
                try {
                    if (response != null && response.getStatusLine() != null) {
                        int statusCode = response.getStatusLine().getStatusCode();
                        span.getContext().getHttp().withStatusCode(statusCode);
                    }
                    span.captureException(t);
                } finally {
                    span.deactivate().end();
                }
            }
        }
    }

    @Override
    public Class<?> getAdviceClass() {
        return LegacyApacheHttpClientAdvice.class;
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
