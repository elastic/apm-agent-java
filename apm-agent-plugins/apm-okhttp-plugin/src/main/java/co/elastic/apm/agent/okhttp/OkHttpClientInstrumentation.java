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
package co.elastic.apm.agent.okhttp;

import co.elastic.apm.agent.httpclient.HttpClientHelper;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.dispatch.HeaderUtils;
import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.Request;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToFields.ToField;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

public class OkHttpClientInstrumentation extends AbstractOkHttpClientInstrumentation {

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.okhttp.OkHttpClientInstrumentation$OkHttpClientExecuteAdvice";
    }

    public static class OkHttpClientExecuteAdvice {

        @Nonnull
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        @Advice.AssignReturned.ToFields(@ToField(index = 0, value = "originalRequest", typing = Assigner.Typing.DYNAMIC))
        public static Object[] onBeforeExecute(@Advice.FieldValue("originalRequest") @Nullable Object originalRequest) {

            final AbstractSpan<?> parent = tracer.getActive();
            if (parent == null || !(originalRequest instanceof Request)) {
                return new Object[]{originalRequest, null};
            }

            com.squareup.okhttp.Request request = (com.squareup.okhttp.Request) originalRequest;
            HttpUrl httpUrl = request.httpUrl();

            Span<?> span = HttpClientHelper.startHttpClientSpan(parent, request.method(), httpUrl.toString(), httpUrl.scheme(),
                OkHttpClientHelper.computeHostName(httpUrl.host()), httpUrl.port());

            if (span != null) {
                span.activate();
            }

            if (!HeaderUtils.containsAny(tracer.getTraceHeaderNames(), request, OkHttpRequestHeaderGetter.INSTANCE)) {
                Request.Builder builder = ((Request) originalRequest).newBuilder();
                if (span != null) {
                    span.propagateTraceContext(builder, OkHttpRequestHeaderSetter.INSTANCE);
                } else {
                    parent.propagateTraceContext(builder, OkHttpRequestHeaderSetter.INSTANCE);
                }
                request = builder.build();
            }

            return new Object[]{request, span};
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onAfterExecute(@Advice.Return @Nullable com.squareup.okhttp.Response response,
                                          @Advice.Thrown @Nullable Throwable t,
                                          @Advice.Enter @Nonnull Object[] enter) {
            Span<?> span = null;
            if (enter[1] instanceof Span<?>) {
                span = (Span<?>) enter[1];
            }
            if (span != null) {
                try {
                    if (response != null) {
                        int statusCode = response.code();
                        span.getContext().getHttp().withStatusCode(statusCode);
                    } else if (t != null) {
                        span.withOutcome(Outcome.FAILURE);
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
        return named("com.squareup.okhttp.Call");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("execute").and(returns(named("com.squareup.okhttp.Response")));
    }
}
