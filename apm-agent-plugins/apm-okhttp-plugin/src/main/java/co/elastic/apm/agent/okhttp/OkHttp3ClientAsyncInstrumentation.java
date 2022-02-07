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
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.asm.Advice.AssignReturned.ToFields.ToField;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;

import static net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class OkHttp3ClientAsyncInstrumentation extends AbstractOkHttp3ClientInstrumentation {

    public static final Logger logger = LoggerFactory.getLogger(OkHttp3ClientAsyncInstrumentation.class);

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.okhttp.OkHttp3ClientAsyncInstrumentation$OkHttpClient3ExecuteAdvice";
    }

    public static class OkHttpClient3ExecuteAdvice {

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        @Advice.AssignReturned.ToFields(@ToField(index = 0, value = "originalRequest", typing = DYNAMIC))
        @Advice.AssignReturned.ToArguments(@ToArgument(index = 1, value = 0, typing = DYNAMIC))
        public static Object[] onBeforeEnqueue(final @Advice.Origin Class<? extends Call> clazz,
                                               final @Advice.FieldValue("originalRequest") @Nullable okhttp3.Request originalRequest,
                                               final @Advice.Argument(0) @Nullable Callback originalCallback) {

            final AbstractSpan<?> parent = tracer.getActive();
            if (parent == null) {
                return null;
            }

            if (originalRequest == null || originalCallback == null) {
                return null;
            }

            okhttp3.Request request = originalRequest;
            Callback callback = originalCallback;
            HttpUrl url = request.url();

            Span span = HttpClientHelper.startHttpClientSpan(parent, request.method(), url.toString(), url.scheme(),
                OkHttpClientHelper.computeHostName(url.host()), url.port());

            if (span != null) {
                span.activate();
            }

            if (!TraceContext.containsTraceContextTextHeaders(request, OkHttp3RequestHeaderGetter.INSTANCE)) {
                Request.Builder builder = originalRequest.newBuilder();
                if (span != null) {
                    span.propagateTraceContext(builder, OkHttp3RequestHeaderSetter.INSTANCE);
                    request = builder.build();
                    callback = CallbackWrapperCreator.INSTANCE.wrap(callback, span);
                } else {
                    parent.propagateTraceContext(builder, OkHttp3RequestHeaderSetter.INSTANCE);
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
            public void onFailure(Call call, IOException e) {
                try {
                    span.captureException(e).withOutcome(Outcome.FAILURE).end();
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
        return nameStartsWith("okhttp3.").and(nameEndsWith(".RealCall"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("enqueue").and(takesArguments(1)).and(takesArgument(0, named("okhttp3.Callback"))).and(returns(void.class));
    }
}
