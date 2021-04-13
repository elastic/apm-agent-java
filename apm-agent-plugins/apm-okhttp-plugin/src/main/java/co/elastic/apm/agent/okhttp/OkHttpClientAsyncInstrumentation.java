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
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.sdk.advice.AssignTo;
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

    public static final Logger logger = LoggerFactory.getLogger(OkHttpClientAsyncInstrumentation.class);

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.okhttp.OkHttpClientAsyncInstrumentation$OkHttpClient3ExecuteAdvice";
    }

    public static class OkHttpClient3ExecuteAdvice {

        @AssignTo(
            fields = @AssignTo.Field(index = 0, value = "originalRequest"),
            arguments = @AssignTo.Argument(index = 1, value = 0)
        )
        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object[] onBeforeEnqueue(final @Advice.Origin Class<? extends Call> clazz,
                                               final @Advice.FieldValue("originalRequest") @Nullable Request originalRequest,
                                               final @Advice.Argument(0) @Nullable Callback originalCallback) {
            if (tracer.getActive() == null) {
                return null;
            }

            if (originalRequest == null || originalCallback == null) {
                return null;
            }

            final AbstractSpan<?> parent = tracer.getActive();

            Request request = originalRequest;
            Callback callback = originalCallback;
            URL url = request.url();
            Span span = HttpClientHelper.startHttpClientSpan(parent, request.method(), url.toString(), url.getProtocol(),
                OkHttpClientHelper.computeHostName(url.getHost()), url.getPort());
            if (span != null) {
                span.activate();
                Request.Builder builder = originalRequest.newBuilder();
                span.propagateTraceContext(builder, OkHttpRequestHeaderSetter.INSTANCE);
                request = builder.build();
                callback = CallbackWrapperCreator.INSTANCE.wrap(originalCallback, span);
            }
            return new Object[]{request, callback, span};
        }

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static void onAfterEnqueue(@Advice.Enter @Nullable Object[] enter) {
            Span span = enter != null ? (Span) enter[2] : null;
            if (span != null) {
                span.deactivate();
            }
        }
    }

    public static class CallbackWrapperCreator implements WrapperCreator<Callback> {

        public static final CallbackWrapperCreator INSTANCE = new CallbackWrapperCreator();

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
                    span.captureException(e)
                        .withOutcome(Outcome.FAILURE)
                        .end();
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
