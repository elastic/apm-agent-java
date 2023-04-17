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
package co.elastic.apm.agent.finaglehttpclient;


import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.finaglehttpclient.helper.RequestHeaderAccessor;
import co.elastic.apm.agent.httpclient.HttpClientHelper;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import co.elastic.apm.agent.tracer.dispatch.HeaderUtils;
import com.twitter.finagle.http.Request;
import com.twitter.finagle.http.Response;
import com.twitter.finagle.tracing.Trace;
import com.twitter.util.Future;
import com.twitter.util.FutureEventListener;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Finagle HTTP client instrumentation responsible for creating, activating and deactivating spans.
 * This instrumentation targets {@link com.twitter.finagle.http.filter.PayloadSizeFilter}.
 * In order to distinguish between HTTP and HTTPS requests, we use {@link FinagleTlsFilterInstrumentation}.
 * Sometimes the target host is not available within the PayloadSizeFilter.
 * In this case we try to enrich the span via the {@link FinagleExceptionSourceFilterInstrumentation}.
 */
@SuppressWarnings("JavadocReference")
public class FinaglePayloadSizeFilterInstrumentation extends TracerAwareInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("com.twitter.finagle.http.filter.PayloadSizeFilter");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("apply")
            .and(returns(named("com.twitter.util.Future")))
            .and(takesArguments(2))
            .and(takesArgument(0, named("com.twitter.finagle.http.Request")))
            .and(takesArgument(1, named("com.twitter.finagle.Service")));
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return classLoaderCanLoadClass("com.twitter.finagle.http.Request$Inbound");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "finagle-httpclient");
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.finaglehttpclient.FinaglePayloadSizeFilterInstrumentation$PayloadSizeFilterAdvice";
    }

    public static class PayloadSizeFilterAdvice {

        private static final WeakMap<Request, Span> inflightSpansWithUnknownHost = WeakConcurrent.buildMap();

        /**
         * The PayloadSizeFilterAdvice is executed both for server and client requests.
         * We distinguish server from client requests based on that server-requests have the Request$Inbound as implementation.
         */
        private static final Class<?> INBOUND_REQUEST_CLASS;

        static {
            try {
                INBOUND_REQUEST_CLASS = Class.forName("com.twitter.finagle.http.Request$Inbound");
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e); //should never occur due to the classloader check in the instrumentation
            }
        }

        @Nullable
        public static Span<?> getAndRemoveSpanWithUnknownHostForRequest(Request forRequest) {
            return inflightSpansWithUnknownHost.remove(forRequest);
        }

        private static final Logger logger = LoggerFactory.getLogger(PayloadSizeFilterAdvice.class);

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onBeforeExecute(@Nullable @Advice.Argument(0) Request request) {
            //The PayloadSizeFilter is applied to both server and client requests
            //We manually exclude server-requests here via the INBOUND_REQUEST_CLASS check
            if (request == null || INBOUND_REQUEST_CLASS.isInstance(request)) {
                return null;
            }
            AbstractSpan<?> parent = tracer.getActive();
            if (parent == null) {
                return null;
            }

            Trace.apply().recordClientSend();

            boolean hostUnknown = true;
            String host = "unknown-host";
            if (request.host().nonEmpty()) {
                //The host should usually be not empty, as it is forbidden by HTTP standards
                //However, experiments showed that finagle can actually omit this header, e.g. in the zipkin demo app.
                host = request.host().get();
                hostUnknown = false;
            }

            URI uri = resolveURI(request, host);
            Span<?> span = HttpClientHelper.startHttpClientSpan(parent, request.method().name(), uri, null);

            if (span != null) {
                span.activate();
                if (hostUnknown) {
                    inflightSpansWithUnknownHost.put(request, span);
                }
            }

            if (!HeaderUtils.containsAny(tracer.getTraceHeaderNames(), request, RequestHeaderAccessor.INSTANCE)) {
                if (span != null) {
                    span.propagateTraceContext(request, RequestHeaderAccessor.INSTANCE);
                } else if (!HeaderUtils.containsAny(tracer.getTraceHeaderNames(), request, RequestHeaderAccessor.INSTANCE)) {
                    // adds headers of potential parent exit-spans
                    parent.propagateTraceContext(request, RequestHeaderAccessor.INSTANCE);
                }
            }

            return span;
        }

        @Nonnull
        private static URI resolveURI(@Nonnull Request request, String host) {

            StringBuilder uriStr = new StringBuilder();
            if (FinagleTlsFilterInstrumentation.TlsFilterAdvice.isTlsRequest(request)) {
                uriStr.append("https://");
            } else {
                uriStr.append("http://");
            }
            uriStr.append(host).append(request.uri());
            return URI.create(uriStr.toString());
        }


        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onExit(
            final @Nullable @Advice.Argument(0) Request request,
            @Advice.Enter @Nullable Object spanObj,
            @Advice.Thrown @Nullable Throwable thrown,
            @Advice.Return @Nullable Future<Response> response
        ) {
            if (spanObj == null) {
                return;
            }
            final Span<?> span = (Span<?>) spanObj;
            span.deactivate();
            if (thrown != null) {
                span.captureException(thrown);
                endSpanForRequest(request, span);
            } else if (response == null) {
                logger.error("Expected response-future to be not null", new Throwable());
                endSpanForRequest(request, span);
            } else {
                response.addEventListener(new FutureEventListener<Response>() {
                    @Override
                    public void onSuccess(Response responseVal) {
                        span.getContext().getHttp().withStatusCode(responseVal.statusCode());
                        endSpanForRequest(request, span);
                    }

                    @Override
                    public void onFailure(Throwable cause) {
                        span.captureException(cause);
                        endSpanForRequest(request, span);
                    }
                });
            }
        }

        private static void endSpanForRequest(@Nullable Request request, Span<?> span) {
            if (request != null) { // should always be true because otherwise no Span<?> is created
                inflightSpansWithUnknownHost.remove(request);
            }
            span.end();
        }
    }

}
