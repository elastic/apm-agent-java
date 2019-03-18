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
import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

public class OkHttpClientAsyncInstrumentation extends ElasticApmInstrumentation {

    @VisibleForAdvice
    public static final Logger logger = LoggerFactory.getLogger(OkHttpClientAsyncInstrumentation.class);

    @Override
    public Class<?> getAdviceClass() {
        return OkHttpClient3ExecuteAdvice.class;
    }

    @Nullable
    @VisibleForAdvice
    public static HelperClassManager<WrapperCreator<Callback>> callbackWrapperCreator;


    @Override
    public void init(ElasticApmTracer tracer) {
        callbackWrapperCreator = HelperClassManager.ForAnyClassLoader.of(tracer,
            OkHttpClientAsyncInstrumentation.class.getName() + "$CallbackWrapperCreator",
            OkHttpClientAsyncInstrumentation.class.getName() + "$CallbackWrapperCreator$CallbackWrapper");
    }

    @VisibleForAdvice
    public static class OkHttpClient3ExecuteAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onBeforeEnqueue(@Advice.Origin Class<? extends Call> clazz,
                                            @Advice.FieldValue(value = "originalRequest", typing = Assigner.Typing.DYNAMIC, readOnly = false) @Nullable Request originalRequest,
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

            Request request = originalRequest;
            span = HttpClientHelper.startHttpClientSpan(parent, request.method(), request.url().toString(), request.url().getHost());
            if (span != null) {
                span.activate().markLifecycleManagingThreadSwitchExpected();
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

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "okhttp");
    }

}
