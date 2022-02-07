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

import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.asm.Advice.AssignReturned.ToFields.ToField;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URL;

import static net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

public class OkHttpClientAsyncInstrumentation extends AbstractOkHttpClientInstrumentation {

    public static final Logger logger = LoggerFactory.getLogger(OkHttpClientAsyncInstrumentation.class);

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.okhttp.OkHttpClientAsyncInstrumentation$OkHttpClient3ExecuteAdvice";
    }

    public static class OkHttpClient3ExecuteAdvice {

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        @Advice.AssignReturned.ToFields(@ToField(index = 0, value = "originalRequest", typing = DYNAMIC))
        @Advice.AssignReturned.ToArguments(@ToArgument(index = 1, value = 0, typing = DYNAMIC))
        public static Object[] onBeforeEnqueue(final @Advice.Origin Class<? extends Call> clazz,
                                               final @Advice.FieldValue("originalRequest") @Nullable Request originalRequest,
                                               final @Advice.Argument(0) @Nullable Callback originalCallback) {

            final AbstractSpan<?> parent = tracer.getActive();
            if (parent == null) {
                return null;
            }

            if (originalRequest == null || originalCallback == null) {
                return null;
            }

            Request request = originalRequest;
            Callback callback = originalCallback;
            URL url = request.url();

            Span span = HttpClientHelper.startHttpClientSpan(parent, request.method(), url.toString(), url.getProtocol(),
                OkHttpClientHelper.computeHostName(url.getHost()), url.getPort());

            if (span != null) {
                span.activate();
            }

            if (!TraceContext.containsTraceContextTextHeaders(request, OkHttpRequestHeaderGetter.INSTANCE)) {
                Request.Builder builder = originalRequest.newBuilder();
                if (span != null) {
                    span.propagateTraceContext(builder, OkHttpRequestHeaderSetter.INSTANCE);
                    request = builder.build();
                    callback = CallbackWrapperCreator.INSTANCE.wrap(originalCallback, span);
                } else {
                    parent.propagateTraceContext(builder, OkHttpRequestHeaderSetter.INSTANCE);
                    request = builder.build();
                }
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
