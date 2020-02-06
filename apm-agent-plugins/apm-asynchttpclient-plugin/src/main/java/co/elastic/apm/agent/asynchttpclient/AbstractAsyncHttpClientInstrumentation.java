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
package co.elastic.apm.agent.asynchttpclient;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.HttpResponseStatus;
import org.asynchttpclient.Request;
import org.asynchttpclient.uri.Uri;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public abstract class AbstractAsyncHttpClientInstrumentation extends ElasticApmInstrumentation {

    // Referencing specific AsyncHttpClient classes are allowed due to type erasure
    @VisibleForAdvice
    @Nullable
    public static HelperClassManager<TextHeaderSetter<Request>> headerSetterManager;

    @VisibleForAdvice
    public static final WeakConcurrentMap<AsyncHandler<?>, Span> handlerSpanMap = new WeakConcurrentMap.WithInlinedExpunction<>();

    public AbstractAsyncHttpClientInstrumentation(ElasticApmTracer tracer) {
        if (headerSetterManager == null) {
            synchronized (AbstractAsyncHandlerInstrumentation.class) {
                if (headerSetterManager == null) {
                    headerSetterManager = HelperClassManager.ForAnyClassLoader.of(tracer,
                        "co.elastic.apm.agent.asynchttpclient.helper.RequestHeaderAccessor"
                    );
                }
            }
        }
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "asynchttpclient");
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader())
            .and(classLoaderCanLoadClass("org.asynchttpclient.AsyncHandler"));
    }

    public static class AsyncHttpClientInstrumentation extends AbstractAsyncHttpClientInstrumentation {
        public AsyncHttpClientInstrumentation(ElasticApmTracer tracer) {
            super(tracer);
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onBeforeExecute(@Advice.Argument(value = 0) Request request,
                                            @Advice.Argument(value = 1) AsyncHandler<?> asyncHandler,
                                            @Advice.Local("span") Span span) {
            if (tracer == null || tracer.getActive() == null) {
                return;
            }

            final TraceContextHolder<?> parent = tracer.getActive();
            Uri uri = request.getUri();
            span = HttpClientHelper.startHttpClientSpan(parent, request.getMethod(), uri.toUrl(), uri.getScheme(), uri.getHost(), uri.getPort());

            if (span != null) {
                span.activate();
                TextHeaderSetter<Request> headerSetter = null;
                if (headerSetterManager != null) {
                    headerSetter = headerSetterManager.getForClassLoaderOfClass(Request.class);
                }
                if (headerSetter != null) {
                    span.getTraceContext().setOutgoingTraceContextHeaders(request, headerSetter);
                }
                handlerSpanMap.put(asyncHandler, span);
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        private static void onAfterExecute(@Advice.Local("span") @Nullable Span span,
                                           @Advice.Argument(value = 1) AsyncHandler<?> asyncHandler,
                                           @Advice.Thrown @Nullable Throwable t) {
            if (span != null) {
                span.deactivate();
                if (t != null) {
                    handlerSpanMap.remove(asyncHandler);
                    span.captureException(t).end();
                }
            }
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("org.asynchttpclient.DefaultAsyncHttpClient");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("executeRequest")
                .and(takesArgument(0, named("org.asynchttpclient.Request")))
                .and(takesArgument(1, named("org.asynchttpclient.AsyncHandler")));
        }
    }

    public abstract static class AbstractAsyncHandlerInstrumentation extends AbstractAsyncHttpClientInstrumentation {

        private final ElementMatcher<? super MethodDescription> methodMatcher;

        protected AbstractAsyncHandlerInstrumentation(ElasticApmTracer tracer, ElementMatcher<? super MethodDescription> methodMatcher) {
            super(tracer);
            this.methodMatcher = methodMatcher;
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return hasSuperType(named("org.asynchttpclient.AsyncHandler"));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return methodMatcher;
        }
    }

    public static class AsyncHandlerOnCompletedInstrumentation extends AbstractAsyncHandlerInstrumentation {

        public AsyncHandlerOnCompletedInstrumentation(ElasticApmTracer tracer) {
            super(tracer, named("onCompleted").and(takesArguments(0)));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onMethodEnter(@Advice.This AsyncHandler<?> asyncHandler) {
            final Span span = handlerSpanMap.remove(asyncHandler);
            if (span != null) {
                span.end();
            }
        }
    }

    public static class AsyncHandlerOnThrowableInstrumentation extends AbstractAsyncHandlerInstrumentation {

        public AsyncHandlerOnThrowableInstrumentation(ElasticApmTracer tracer) {
            super(tracer, named("onThrowable").and(takesArguments(Throwable.class)));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onMethodEnter(@Advice.This AsyncHandler<?> asyncHandler, @Advice.Argument(0) Throwable t) {
            final Span span = handlerSpanMap.remove(asyncHandler);
            if (span != null) {
                span.captureException(t).end();
            }
        }
    }

    public static class AsyncHandlerOnStatusReceivedInstrumentation extends AbstractAsyncHandlerInstrumentation {

        public AsyncHandlerOnStatusReceivedInstrumentation(ElasticApmTracer tracer) {
            super(tracer, named("onStatusReceived").and(takesArgument(0, named("org.asynchttpclient.HttpResponseStatus"))));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onMethodEnter(@Advice.This AsyncHandler<?> asyncHandler, @Advice.Argument(0) HttpResponseStatus status) {
            final Span span = handlerSpanMap.get(asyncHandler);
            if (span != null) {
                span.getContext().getHttp().withStatusCode(status.getStatusCode());
            }
        }
    }

}
