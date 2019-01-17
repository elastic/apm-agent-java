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
package co.elastic.apm.agent.okhttp;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static co.elastic.apm.agent.http.client.HttpClientHelper.HTTP_CLIENT_SPAN_TYPE_PREFIX;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

public class OkHttp3ClientInstrumentation extends ElasticApmInstrumentation {

    private static final String SPAN_TYPE_OK_HTTP_CLIENT = HTTP_CLIENT_SPAN_TYPE_PREFIX + "okhttp";

    @Override
    public Class<?> getAdviceClass() {
        return OkHttpClient3ExecuteAdvice.class;
    }

    @VisibleForAdvice
    public static class OkHttpClient3ExecuteAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onBeforeExecute( @Advice.FieldValue(value = "originalRequest", typing = Assigner.Typing.DYNAMIC, readOnly = false) @Nullable Object originalRequest,
                                             @Advice.Local("span") Span span) {

            if (tracer == null || tracer.getActive() == null) {
                return;
            }

            if (originalRequest == null) {
                return;
            }

            final TraceContextHolder<?> parent = tracer.getActive();

            if (originalRequest instanceof okhttp3.Request) {
                okhttp3.Request request = (okhttp3.Request) originalRequest;
                span = HttpClientHelper.startHttpClientSpan(parent, request.method(), request.url().uri(), request.url().host(), SPAN_TYPE_OK_HTTP_CLIENT);
                if (span != null) {
                    originalRequest = ((okhttp3.Request) originalRequest).newBuilder().addHeader(TraceContext.TRACE_PARENT_HEADER, span.getTraceContext().getOutgoingTraceParentHeader().toString()).build();
                }
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void onAfterExecute(@Advice.Return @Nullable okhttp3.Response response,
                                          @Advice.Local("span") @Nullable Span span,
                                          @Advice.Thrown @Nullable Throwable t) {
            if (span != null) {
                try {
                    if (response != null) {
                        int statusCode = response.code();
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
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("okhttp3.RealCall");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("execute").and(returns(named("okhttp3.Response")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "okhttp");
    }

}
