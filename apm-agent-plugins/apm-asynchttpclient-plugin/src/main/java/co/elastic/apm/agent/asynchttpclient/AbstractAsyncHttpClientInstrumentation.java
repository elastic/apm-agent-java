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
package co.elastic.apm.agent.asynchttpclient;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.sdk.DynamicTransformer;
import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import co.elastic.apm.agent.util.SpanConcurrentHashMap;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.HttpResponseStatus;
import org.asynchttpclient.Request;
import org.asynchttpclient.handler.MaxRedirectException;
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

public abstract class AbstractAsyncHttpClientInstrumentation extends TracerAwareInstrumentation {

    static final WeakConcurrentMap<AsyncHandler<?>, Span> handlerSpanMap = SpanConcurrentHashMap.createWeakMap();

    public static final List<Class<? extends ElasticApmInstrumentation>> ASYNC_HANDLER_INSTRUMENTATIONS = Arrays.<Class<? extends ElasticApmInstrumentation>>asList(
        AsyncHandlerOnCompletedInstrumentation.class,
        AsyncHandlerOnThrowableInstrumentation.class,
        AsyncHandlerOnStatusReceivedInstrumentation.class,
        StreamedAsyncHandlerOnStreamInstrumentation.class);

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

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onBeforeExecute(@Advice.Argument(value = 0) Request request,
                                            @Advice.Argument(value = 1) AsyncHandler<?> asyncHandler) {
            final AbstractSpan<?> parent = tracer.getActive();
            if (parent == null) {
                return null;
            }
            DynamicTransformer.Accessor.get().ensureInstrumented(asyncHandler.getClass(), ASYNC_HANDLER_INSTRUMENTATIONS);

            Uri uri = request.getUri();
            Span span = HttpClientHelper.startHttpClientSpan(parent, request.getMethod(), uri.toUrl(), uri.getScheme(), uri.getHost(), uri.getPort());

            if (span != null) {
                span.activate();
                span.propagateTraceContext(request, RequestHeaderSetter.INSTANCE);
                handlerSpanMap.put(asyncHandler, span);
            } else {
                parent.propagateTraceContext(request, RequestHeaderSetter.INSTANCE);
            }
            return span;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onAfterExecute(@Advice.Enter @Nullable Object spanObj,
                                          @Advice.Argument(value = 1) AsyncHandler<?> asyncHandler,
                                          @Advice.Thrown @Nullable Throwable t) {
            if (spanObj == null) {
                return;
            }
            Span span = (Span) spanObj;
            span.deactivate();
            if (t != null) {
                handlerSpanMap.remove(asyncHandler);
                span.captureException(t).end();
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
         * Overridden in {@link DynamicTransformer#ensureInstrumented(Class, Collection)},
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

        public AsyncHandlerOnCompletedInstrumentation(ElasticApmTracer tracer) {
            super(named("onCompleted").and(takesArguments(0)));
        }

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onMethodEnter(@Advice.This AsyncHandler<?> asyncHandler) {
            return removeAndActivateSpan(asyncHandler);
        }

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static void onMethodExit(@Nullable @Advice.Enter Object spanObj) {
            Span span = (Span) spanObj;
            if (span != null) {
                span.end();
                span.deactivate();
            }
        }
    }

    public static class AsyncHandlerOnThrowableInstrumentation extends AbstractAsyncHandlerInstrumentation {

        public AsyncHandlerOnThrowableInstrumentation(ElasticApmTracer tracer) {
            super(named("onThrowable").and(takesArguments(Throwable.class)));
        }

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onMethodEnter(@Advice.This AsyncHandler<?> asyncHandler) {
            return removeAndActivateSpan(asyncHandler);
        }

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static void onMethodExit(@Nullable @Advice.Enter Object spanObj, @Advice.Argument(0) Throwable t) {
            Span span = (Span) spanObj;
            if (span != null) {
                if (t instanceof MaxRedirectException) {
                    span.withOutcome(Outcome.FAILURE);
                }
                span.captureException(t).end();
                span.deactivate();
            }
        }
    }

    public static class AsyncHandlerOnStatusReceivedInstrumentation extends AbstractAsyncHandlerInstrumentation {

        public AsyncHandlerOnStatusReceivedInstrumentation(ElasticApmTracer tracer) {
            super(named("onStatusReceived").and(takesArgument(0, named("org.asynchttpclient.HttpResponseStatus"))));
        }

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onMethodEnter(@Advice.This AsyncHandler<?> asyncHandler) {
            return getAndActivateSpan(asyncHandler);
        }

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static void onMethodExit(@Nullable @Advice.Enter Object spanObj, @Advice.Argument(0) HttpResponseStatus status) {
            Span span = (Span) spanObj;
            if (span != null) {
                span.getContext().getHttp().withStatusCode(status.getStatusCode());
                span.deactivate();
            }
        }
    }

    public static class StreamedAsyncHandlerOnStreamInstrumentation extends AbstractAsyncHandlerInstrumentation {

        public StreamedAsyncHandlerOnStreamInstrumentation(ElasticApmTracer tracer) {
            super(named("onStream").and(takesArgument(0, named("org.reactivestreams.Publisher"))));
        }

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onMethodEnter(@Advice.This AsyncHandler<?> asyncHandler) {
            return getAndActivateSpan(asyncHandler);
        }

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static void onMethodExit(@Nullable @Advice.Enter Object spanObj) {
            Span span = (Span) spanObj;
            if (span != null) {
                span.deactivate();
            }
        }
    }

    @Nullable
    static Span removeAndActivateSpan(AsyncHandler<?> asyncHandler) {
        Span span = handlerSpanMap.remove(asyncHandler);
        if (span != null) {
            span.activate();
        }
        return span;
    }

    @Nullable
    static Span getAndActivateSpan(AsyncHandler<?> asyncHandler) {
        Span span = handlerSpanMap.get(asyncHandler);
        if (span != null) {
            span.activate();
        }
        return span;
    }

}
