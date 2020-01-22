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

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class OkHttp3ClientAsyncInstrumentation extends ElasticApmInstrumentation {

    @VisibleForAdvice
    public static final Logger logger = LoggerFactory.getLogger(OkHttp3ClientAsyncInstrumentation.class);

    @Override
    public Class<?> getAdviceClass() {
        return OkHttpClient3ExecuteAdvice.class;
    }

    @Nullable
    @VisibleForAdvice
    public static HelperClassManager<WrapperCreator<Callback>> callbackWrapperCreator;

    public OkHttp3ClientAsyncInstrumentation(ElasticApmTracer tracer) {
        callbackWrapperCreator = HelperClassManager.ForAnyClassLoader.of(tracer,
            OkHttp3ClientAsyncInstrumentation.class.getName() + "$CallbackWrapperCreator",
            OkHttp3ClientAsyncInstrumentation.class.getName() + "$CallbackWrapperCreator$CallbackWrapper");
    }

    @VisibleForAdvice
    public static class OkHttpClient3ExecuteAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onBeforeEnqueue(@Advice.Origin Class<? extends Call> clazz,
                                            @Advice.FieldValue(value = "originalRequest", typing = Assigner.Typing.DYNAMIC, readOnly = false) @Nullable okhttp3.Request originalRequest,
                                            @Advice.Argument(value = 0, readOnly = false) @Nullable Callback callback,
                                            @Advice.Local("span") Span span) {
            if (tracer == null || tracer.getActive() == null || callbackWrapperCreator == null) {
                return;
            }

            final WrapperCreator<Callback> wrapperCreator = callbackWrapperCreator.getForClassLoaderOfClass(clazz);
            if (originalRequest == null || callback == null || wrapperCreator == null) {
                return;
            }

            final TraceContextHolder<?> parent = tracer.getActive();

            okhttp3.Request request = originalRequest;
            HttpUrl url = request.url();
            span = HttpClientHelper.startHttpClientSpan(parent, request.method(), url.toString(), url.scheme(),
                OkHttpClientHelper.computeHostName(url.host()), url.port());
            if (span != null) {
                span.activate();
                originalRequest = originalRequest.newBuilder().addHeader(TraceContext.TRACE_PARENT_HEADER, span.getTraceContext().getOutgoingTraceParentHeader().toString()).build();
                callback = wrapperCreator.wrap(callback, span);
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class)
        private static void onAfterEnqueue(@Advice.Local("span") @Nullable Span span) {
            if (span != null) {
                span.deactivate();
            }
        }
    }

    public static class CallbackWrapperCreator implements WrapperCreator<Callback> {

        @Override
        public Callback wrap(final Callback delegate, Span span) {
            return new CallbackWrapper(span, delegate);
        }

        private static class CallbackWrapper implements Callback {
            private final Span span;
            private final Callback delegate;

            CallbackWrapper(Span span, Callback delegate) {
                this.span = span;
                this.delegate = delegate;
            }

            @Override
            public void onFailure(Call call, IOException e) {
                try {
                    span.captureException(e).end();
                } catch (Throwable t) {
                    logger.error(t.getMessage(), t);
                } finally {
                    delegate.onFailure(call, e);
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    span.getContext().getHttp().withStatusCode(response.code());
                    span.end();
                } catch (Throwable t) {
                    logger.error(t.getMessage(), t);
                } finally {
                    delegate.onResponse(call, response);
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
        return named("enqueue").and(takesArguments(1)).and(takesArgument(0, named("okhttp3.Callback"))).and(returns(void.class));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "okhttp");
    }

}
