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
package co.elastic.apm.agent.okhttp;

import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.Request;
import net.bytebuddy.asm.Advice;
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
    public Class<?> getAdviceClass() {
        return OkHttpClientExecuteAdvice.class;
    }

    public static class OkHttpClientExecuteAdvice {

        @Nonnull
        @AssignTo(fields = @AssignTo.Field(index = 0, value = "originalRequest", typing = Assigner.Typing.DYNAMIC))
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object[] onBeforeExecute(@Advice.FieldValue("originalRequest") @Nullable Object originalRequest) {
            if (tracer.getActive() == null || !(originalRequest instanceof Request)) {
                return new Object[]{originalRequest, null};
            }
            final AbstractSpan<?> parent = tracer.getActive();

            com.squareup.okhttp.Request request = (com.squareup.okhttp.Request) originalRequest;
            HttpUrl httpUrl = request.httpUrl();
            Span span = HttpClientHelper.startHttpClientSpan(parent, request.method(), httpUrl.toString(), httpUrl.scheme(),
                    OkHttpClientHelper.computeHostName(httpUrl.host()), httpUrl.port());
            if (span != null) {
                span.activate();
                Request.Builder builder = ((com.squareup.okhttp.Request) originalRequest).newBuilder();
                span.propagateTraceContext(builder, OkHttpRequestHeaderSetter.INSTANCE);
                return new Object[]{builder.build(), span};
            }
            return new Object[]{originalRequest, null};
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onAfterExecute(@Advice.Return @Nullable com.squareup.okhttp.Response response,
                                          @Advice.Thrown @Nullable Throwable t,
                                          @Advice.Enter @Nonnull Object[] enter) {
            Span span = null;
            if (enter[1] instanceof Span) {
                span = (Span) enter[1];
            }
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
        return named("com.squareup.okhttp.Call");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("execute").and(returns(named("com.squareup.okhttp.Response")));
    }
}
