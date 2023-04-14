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
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToFields.ToField;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import okhttp3.HttpUrl;
import okhttp3.Request;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

public class OkHttp3ClientInstrumentation extends AbstractOkHttp3ClientInstrumentation {

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.okhttp.OkHttp3ClientInstrumentation$OkHttpClient3ExecuteAdvice";
    }

    public static class OkHttpClient3ExecuteAdvice {

        @Nonnull
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        @Advice.AssignReturned.ToFields(@ToField(index = 0, value = "originalRequest", typing = Assigner.Typing.DYNAMIC))
        public static Object[] onBeforeExecute(final @Advice.FieldValue("originalRequest") @Nullable Object originalRequest) {

            final AbstractSpan<?> parent = tracer.getActive();
            if (parent == null || !(originalRequest instanceof Request)) {
                return new Object[]{originalRequest, null};
            }

            okhttp3.Request request = (okhttp3.Request) originalRequest;
            HttpUrl url = request.url();

            Span<?> span = HttpClientHelper.startHttpClientSpan(parent, request.method(), url.toString(), url.scheme(),
                OkHttpClientHelper.computeHostName(url.host()), url.port());

            if (span != null) {
                span.activate();
            }

            if (!HeaderUtils.containsAny(tracer.getTraceHeaderNames(), request, OkHttp3RequestHeaderGetter.INSTANCE)) {
                Request.Builder builder = ((Request) originalRequest).newBuilder();
                if (span != null) {
                    span.propagateTraceContext(builder, OkHttp3RequestHeaderSetter.INSTANCE);
                } else {
                    parent.propagateTraceContext(builder, OkHttp3RequestHeaderSetter.INSTANCE);
                }
                request = builder.build();
            }
            return new Object[]{request, span};
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onAfterExecute(@Advice.Return @Nullable okhttp3.Response response,
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
        return nameStartsWith("okhttp3.").and(nameEndsWith(".RealCall"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("execute").and(returns(hasSuperType(named("okhttp3.Response"))));
    }
}
