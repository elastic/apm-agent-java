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

import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.bci.bytebuddy.postprocessor.AssignTo;
import co.elastic.apm.agent.bci.bytebuddy.postprocessor.AssignToArgument;
import co.elastic.apm.agent.bci.bytebuddy.postprocessor.AssignToField;
import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URL;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

public class OkHttpClientAsyncInstrumentation extends AbstractOkHttpClientInstrumentation {

    @VisibleForAdvice
    public static final Logger logger = LoggerFactory.getLogger(OkHttpClientAsyncInstrumentation.class);

    @Override
    public Class<?> getAdviceClass() {
        return OkHttpClient3ExecuteAdvice.class;
    }

    @Nullable
    @VisibleForAdvice
    public static HelperClassManager<WrapperCreator<Callback>> callbackWrapperCreator;

    public OkHttpClientAsyncInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
        synchronized (OkHttpClientAsyncInstrumentation.class) {
            if (callbackWrapperCreator == null) {
                callbackWrapperCreator = HelperClassManager.ForAnyClassLoader.of(tracer,
                    OkHttpClientAsyncInstrumentation.class.getName() + "$CallbackWrapperCreator",
                    OkHttpClientAsyncInstrumentation.class.getName() + "$CallbackWrapperCreator$CallbackWrapper");
            }
        }
    }

    @VisibleForAdvice
    public static class OkHttpClient3ExecuteAdvice {

        @AssignTo(
            fields = @AssignToField(index = 0, value = "originalRequest"),
            arguments = @AssignToArgument(index = 1, value = 0)
        )
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static Object[] onBeforeEnqueue(@Advice.Origin Class<? extends Call> clazz,
                                               @Advice.FieldValue("originalRequest") @Nullable Request originalRequest,
                                               @Advice.Argument(0) @Nullable Callback callback) {
            if (tracer == null || tracer.getActive() == null || callbackWrapperCreator == null) {
                return new Object[] {originalRequest, callback, null};
            }

            final WrapperCreator<Callback> wrapperCreator = callbackWrapperCreator.getForClassLoaderOfClass(clazz);
            if (originalRequest == null || callback == null || wrapperCreator == null) {
                return new Object[] {originalRequest, callback, null};
            }

            final AbstractSpan<?> parent = tracer.getActive();

            Request request = originalRequest;
            URL url = request.url();
            Span span = HttpClientHelper.startHttpClientSpan(parent, request.method(), url.toString(), url.getProtocol(),
                OkHttpClientHelper.computeHostName(url.getHost()), url.getPort());
            if (span != null) {
                span.activate();
                if (headerSetterHelperManager != null) {
                    TextHeaderSetter<Request.Builder> headerSetter = headerSetterHelperManager.getForClassLoaderOfClass(Request.class);
                    if (headerSetter != null) {
                        Request.Builder builder = originalRequest.newBuilder();
                        span.propagateTraceContext(builder, headerSetter);
                        originalRequest = builder.build();
                    }
                }
                callback = wrapperCreator.wrap(callback, span);
            }
            return new Object[] {originalRequest, callback, span};
        }

        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void onAfterEnqueue(@Advice.Enter @Nullable Object[] enter) {
            Span span = enter != null ? (Span) enter[2] : null;
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
            public void onFailure(Request req, IOException e) {
                try {
                    span.captureException(e).end();
                } catch (Throwable t) {
                    logger.error(t.getMessage(), t);
                } finally {
                    delegate.onFailure(req, e);
                }
            }

            @Override
            public void onResponse(Response response) throws IOException {
                try {
                    span.getContext().getHttp().withStatusCode(response.code());
                    span.end();
                } catch (Throwable t) {
                    logger.error(t.getMessage(), t);
                } finally {
                    delegate.onResponse(response);
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
        return named("enqueue").and(returns(void.class));
    }
}
