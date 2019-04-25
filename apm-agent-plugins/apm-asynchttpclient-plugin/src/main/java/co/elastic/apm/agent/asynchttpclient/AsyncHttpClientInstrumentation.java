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
package co.elastic.apm.agent.asynchttpclient;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.asynchttpclient.Request;
import org.asynchttpclient.Response;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ExecutionException;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

public class AsyncHttpClientInstrumentation extends ElasticApmInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.asynchttpclient.AsyncHttpClient");
    }

    @Override
    public ElementMatcher<? super MethodDescription>  getMethodMatcher() {
        return named("execute")
            .and(returns(named("org.asynchttpclient.ListenableFuture")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "org.asynchttpclient");
    }

    @VisibleForAdvice
    public static class AsyncHttpClientExecuteAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onBeforeExecute(
            @Advice.Argument(typing = Assigner.Typing.DYNAMIC, readOnly = true, value = 1) Request request,
            @Advice.Local("span") Span span) {

            if (tracer == null || tracer.getActive() == null) {
                return;
            }

            final TraceContextHolder<?> parent = tracer.getActive();

            span = HttpClientHelper.startHttpClientSpan(parent, request.getMethod(), request.getUrl(), request.getUri().getHost());

            if(span != null) {
                span.activate();
                request.getHeaders().add(TraceContext.TRACE_PARENT_HEADER, span.getTraceContext().getOutgoingTraceParentHeader().toString());
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void onAfterExecute(@Advice.Return @Nullable org.asynchttpclient.ListenableFuture<Response> response,
                                          @Advice.Local("span") @Nullable Span span,
                                          @Advice.Thrown @Nullable Throwable t) {
            if (span != null) {
                try {
                    if (response != null) {
                        int statusCode = response.get().getStatusCode();
                        span.getContext().getHttp().withStatusCode(statusCode);
                    }
                    span.captureException(t);
                } catch (InterruptedException e) {
                    span.captureException(e);
                } catch (ExecutionException e) {
                    span.captureException(e);
                } finally {
                    span.deactivate().end();
                }
            }
        }
    }
}
