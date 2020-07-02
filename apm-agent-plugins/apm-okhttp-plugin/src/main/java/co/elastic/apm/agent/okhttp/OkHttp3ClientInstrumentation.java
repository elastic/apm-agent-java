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

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.bci.bytebuddy.postprocessor.AssignTo;
import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import co.elastic.apm.agent.threadlocal.GlobalThreadLocal;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import okhttp3.HttpUrl;
import okhttp3.Request;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

public class OkHttp3ClientInstrumentation extends AbstractOkHttp3ClientInstrumentation {

    public OkHttp3ClientInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Override
    public Class<?> getAdviceClass() {
        return OkHttpClient3ExecuteAdvice.class;
    }

    @VisibleForAdvice
    public static class OkHttpClient3ExecuteAdvice {

        @VisibleForAdvice
        public final static GlobalThreadLocal<Span> spanTls = GlobalThreadLocal.get(OkHttpClient3ExecuteAdvice.class, "spanTls");

        @Nullable
        @AssignTo.Field(value = "originalRequest", typing = Assigner.Typing.DYNAMIC)
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static Object onBeforeExecute(final @Advice.FieldValue("originalRequest") @Nullable Object originalRequest) {

            if (tracer == null || tracer.getActive() == null || !(originalRequest instanceof Request)) {
                return originalRequest;
            }

            final AbstractSpan<?> parent = tracer.getActive();

            okhttp3.Request request = (okhttp3.Request) originalRequest;
            HttpUrl url = request.url();
            Span span = HttpClientHelper.startHttpClientSpan(parent, request.method(), url.toString(), url.scheme(),
                OkHttpClientHelper.computeHostName(url.host()), url.port());
            if (span != null) {
                spanTls.set(span);
                span.activate();
                if (headerSetterHelperManager != null) {
                    TextHeaderSetter<Request.Builder> headerSetter = headerSetterHelperManager.getForClassLoaderOfClass(Request.class);
                    if (headerSetter != null) {
                        Request.Builder builder = ((okhttp3.Request) originalRequest).newBuilder();
                        span.propagateTraceContext(builder, headerSetter);
                        return builder.build();
                    }
                }
            }
            return originalRequest;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void onAfterExecute(@Advice.Return @Nullable okhttp3.Response response,
                                          @Advice.Thrown @Nullable Throwable t) {
            final Span span = spanTls.getAndRemove();
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
}
