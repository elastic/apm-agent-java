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

import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
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
import java.util.List;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.any;
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

    @VisibleForAdvice
    public static final List<Class<? extends ElasticApmInstrumentation>> ASYNC_HANDLER_INSTRUMENTATIONS = Arrays.<Class<? extends ElasticApmInstrumentation>>asList(
        AsyncHandlerOnCompletedInstrumentation.class,
        AsyncHandlerOnThrowableInstrumentation.class,
        AsyncHandlerOnStatusReceivedInstrumentation.class,
        StreamedAsyncHandlerOnStreamInstrumentation.class);

    public AbstractAsyncHttpClientInstrumentation() {
        if (headerSetterManager == null) {
            synchronized (AbstractAsyncHandlerInstrumentation.class) {
                if (headerSetterManager == null) {
                    headerSetterManager = HelperClassManager.ForAnyClassLoader.of(tracer,
                        "co.elastic.apm.agent.asynchttpclient.helper.RequestHeaderSetter"
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

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onBeforeExecute(@Advice.Argument(value = 0) Request request,
                                            @Advice.Argument(value = 1) AsyncHandler<?> asyncHandler,
                                            @Advice.Local("span") Span span) {
            if (tracer == null || tracer.getActive() == null) {
                return;
            }
            ElasticApmAgent.ensureInstrumented(asyncHandler.getClass(), ASYNC_HANDLER_INSTRUMENTATIONS);

            final AbstractSpan<?> parent = tracer.getActive();
            Uri uri = request.getUri();
            span = HttpClientHelper.startHttpClientSpan(parent, request.getMethod(), uri.toUrl(), uri.getScheme(), uri.getHost(), uri.getPort());

            if (span != null) {
                span.activate();
                TextHeaderSetter<Request> headerSetter = null;
                if (headerSetterManager != null) {
                    headerSetter = headerSetterManager.getForClassLoaderOfClass(Request.class);
                }
                if (headerSetter != null) {
                    span.propagateTraceContext(request, headerSetter);
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

        protected AbstractAsyncHandlerInstrumentation(ElementMatcher<? super MethodDescription> methodMatcher) {
            this.methodMatcher = methodMatcher;
        }

        /**
         * Overridden in {@link ElasticApmAgent#ensureInstrumented(Class, Collection)},
         * based on the type of the {@linkplain AsyncHandler} implementation class.
         */
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return any();
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return methodMatcher;
        }
    }

    public static class AsyncHandlerOnCompletedInstrumentation extends AbstractAsyncHandlerInstrumentation {

        public AsyncHandlerOnCompletedInstrumentation() {
            super(named("onCompleted").and(takesArguments(0)));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onMethodEnter(@Advice.This AsyncHandler<?> asyncHandler, @Advice.Local("span") Span span) {
            span = handlerSpanMap.remove(asyncHandler);
            if (span != null) {
                span.activate();
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class)
        private static void onMethodExit(@Nullable @Advice.Local("span") Span span) {
            if (span != null) {
                span.end();
                span.deactivate();
            }
        }
    }

    public static class AsyncHandlerOnThrowableInstrumentation extends AbstractAsyncHandlerInstrumentation {

        public AsyncHandlerOnThrowableInstrumentation() {
            super(named("onThrowable").and(takesArguments(Throwable.class)));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onMethodEnter(@Advice.This AsyncHandler<?> asyncHandler, @Advice.Local("span") Span span) {
            span = handlerSpanMap.remove(asyncHandler);
            if (span != null) {
                span.activate();
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class)
        private static void onMethodExit(@Nullable @Advice.Local("span") Span span, @Advice.Argument(0) Throwable t) {
            if (span != null) {
                span.captureException(t).end();
                span.deactivate();
            }
        }
    }

    public static class AsyncHandlerOnStatusReceivedInstrumentation extends AbstractAsyncHandlerInstrumentation {

        public AsyncHandlerOnStatusReceivedInstrumentation() {
            super(named("onStatusReceived").and(takesArgument(0, named("org.asynchttpclient.HttpResponseStatus"))));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onMethodEnter(@Advice.This AsyncHandler<?> asyncHandler, @Advice.Local("span") Span span, @Advice.Argument(0) HttpResponseStatus status) {
            span = handlerSpanMap.get(asyncHandler);
            if (span != null) {
                span.activate();
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class)
        private static void onMethodExit(@Nullable @Advice.Local("span") Span span, @Advice.Argument(0) HttpResponseStatus status) {
            if (span != null) {
                span.getContext().getHttp().withStatusCode(status.getStatusCode());
                span.deactivate();
            }
        }
    }

    public static class StreamedAsyncHandlerOnStreamInstrumentation extends AbstractAsyncHandlerInstrumentation {

        public StreamedAsyncHandlerOnStreamInstrumentation() {
            super(named("onStream").and(takesArgument(0, named("org.reactivestreams.Publisher"))));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onMethodEnter(@Advice.This AsyncHandler<?> asyncHandler, @Advice.Local("span") Span span) {
            span = handlerSpanMap.get(asyncHandler);
            if (span != null) {
                span.activate();
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class)
        private static void onMethodExit(@Nullable @Advice.Local("span") Span span) {
            if (span != null) {
                span.deactivate();
            }
        }

    }

}
