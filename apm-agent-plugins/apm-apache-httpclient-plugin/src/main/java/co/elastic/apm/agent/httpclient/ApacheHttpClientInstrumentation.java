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
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.HttpRequest;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.conn.routing.HttpRoute;

import javax.annotation.Nullable;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

@SuppressWarnings("Duplicates")
public class ApacheHttpClientInstrumentation extends BaseApacheHttpClientInstrumentation {

    public ApacheHttpClientInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
    }

    private static class ApacheHttpClientAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onBeforeExecute(@Advice.Argument(0) HttpRoute route,
                                            @Advice.Argument(1) HttpRequestWrapper request,
                                            @Advice.Local("span") Span span) {
            if (tracer == null || tracer.getActive() == null) {
                return;
            }
            final TraceContextHolder<?> parent = tracer.getActive();
            span = HttpClientHelper.startHttpClientSpan(parent, request.getMethod(), request.getURI(), route.getTargetHost().getHostName());
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

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void onAfterExecute(@Advice.Return @Nullable CloseableHttpResponse response,
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
        return ApacheHttpClientAdvice.class;
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader())
            .and(classLoaderCanLoadClass("org.apache.http.impl.execchain.ClientExecChain"));
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Exec").or(nameContains("Chain"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("org.apache.http.impl.execchain.ClientExecChain"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("execute")
            .and(takesArguments(4))
            .and(returns(hasSuperType(named("org.apache.http.client.methods.CloseableHttpResponse"))))
            .and(takesArgument(0, hasSuperType(named("org.apache.http.conn.routing.HttpRoute"))))
            .and(takesArgument(1, hasSuperType(named("org.apache.http.client.methods.HttpRequestWrapper"))))
            .and(takesArgument(2, hasSuperType(named("org.apache.http.client.protocol.HttpClientContext"))))
            .and(takesArgument(3, hasSuperType(named("org.apache.http.client.methods.HttpExecutionAware"))));
    }
}
