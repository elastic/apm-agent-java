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
package co.elastic.apm.agent.apachehttpclient;

import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.conn.routing.HttpRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger logger = LoggerFactory.getLogger(ApacheHttpClientInstrumentation.class);

    public static class ApacheHttpClientAdvice {
        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onBeforeExecute(@Advice.Argument(0) HttpRoute route,
                                             @Advice.Argument(1) HttpRequestWrapper request,
                                             @Advice.Origin String signature) {

            logger.debug("Enter advice signature = {}", signature);

            AbstractSpan<?> parent = tracer.getActive();
            if (parent == null) {
                logger.debug("Enter advice without parent for method {}#execute() {} {}", request.getClass().getName(), request.getMethod(), request.getURI());
                return null;
            }
            Span span = HttpClientHelper.startHttpClientSpan(parent, request.getMethod(), request.getURI(), route.getTargetHost().getHostName());
            if (span != null) {
                logger.debug("activate and propagate context");
                span.activate();
                span.propagateTraceContext(request, RequestHeaderAccessor.INSTANCE);
            } else if (!TraceContext.containsTraceContextTextHeaders(request, RequestHeaderAccessor.INSTANCE)) {
                logger.debug("propagate parent context for redirect");
                // re-adds the header on redirects
                parent.propagateTraceContext(request, RequestHeaderAccessor.INSTANCE);
            }
            return span;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onAfterExecute(@Advice.Return @Nullable CloseableHttpResponse response,
                                          @Advice.Enter @Nullable Object spanObj,
                                          @Advice.Thrown @Nullable Throwable t,
                                          @Advice.Origin String signature) {

            logger.debug("Exit advice signature = {}", signature);
            Span span = (Span) spanObj;
            if (span == null) {
                logger.debug("early exit, no span");
                return;
            }
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
