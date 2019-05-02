/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.asynchttpclient;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import io.netty.handler.codec.http.HttpHeaders;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.asynchttpclient.*;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class AsyncHttpClientInstrumentation extends ElasticApmInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("org.asynchttpclient.AsyncHttpClient"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("executeRequest")
            .and(takesArgument(0, named("org.asynchttpclient.Request")))
            .and(takesArgument(1, named("org.asynchttpclient.AsyncHandler")));

    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "asynchttpclient");
    }

    @Override
    public Class<?> getAdviceClass() {
        return AsyncHttpClientInstrumentationAdvice.class;
    }

    public static class AsyncHttpClientInstrumentationAdvice {
        @Nullable
        @VisibleForAdvice
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onBeforeExecute(@Advice.Argument(value = 0, readOnly = true) Request request,
                                           @Advice.Argument(value = 1, readOnly = false) AsyncHandler asyncHandler,
                                           @Advice.Local("span") Span span) {
            if(tracer == null || tracer.getActive() == null) {
                return;
            }

            final TraceContextHolder<?> parent = tracer.getActive();
            span = HttpClientHelper.startHttpClientSpan(parent, request.getMethod(), request.getUri().toFullUrl(), request.getUri().getHost());

            if(span != null) {
                span.activate();
                request.getHeaders().add(TraceContext.TRACE_PARENT_HEADER, span.getTraceContext().getOutgoingTraceParentHeader().toString());
            }
            if(!request.getHeaders().contains(TraceContext.TRACE_PARENT_HEADER) && parent != null) {
                request.getHeaders().add(TraceContext.TRACE_PARENT_HEADER, parent.getTraceContext().getOutgoingTraceParentHeader().toString());
            }

            // TODO: Optimize the new call away
            asyncHandler = new AsyncHandlerWrapperCreator().wrap(asyncHandler, span, request);
        }

        @Nullable
        @VisibleForAdvice
        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void onAfterExecute(@Advice.Local("span") Span span,
                                          @Advice.Thrown Throwable t) {
            if(span != null) {
                span.deactivate();
            }
        }
    }

    public static class AsyncHandlerWrapperCreator {
        public AsyncHandler<Object> wrap(final AsyncHandler asyncHandler, Span span, Request originalRequest) {
            return new AsyncHandlerWrapper(asyncHandler, span, originalRequest);
        }

        private static class AsyncHandlerWrapper implements AsyncHandler {
            private Span span;
            private AsyncHandler delegate;
            private Request originalRequest;

            public AsyncHandlerWrapper(AsyncHandler delegate, Span span, Request originalRequest) {
                this.span = span;
                this.delegate = delegate;
                this.originalRequest = originalRequest;
            }

            @Override
            public State onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
                span.getContext().getHttp().withStatusCode(responseStatus.getStatusCode());
                return delegate.onStatusReceived(responseStatus);
            }

            @Override
            public State onHeadersReceived(HttpHeaders headers) throws Exception {
                if(!headers.contains(TraceContext.TRACE_PARENT_HEADER)) {
                    headers.add(TraceContext.TRACE_PARENT_HEADER, "");
                }
                return delegate.onHeadersReceived(headers);
            }

            @Override
            public State onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
                return delegate.onBodyPartReceived(bodyPart);
            }

            @Override
            public void onThrowable(Throwable t) {
                span.captureException(t).end();
                delegate.onThrowable(t);
            }

            @Override
            public Object onCompleted() throws Exception {
                span.end();
                return delegate.onCompleted();
            }
        }

    }

}
