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
import co.elastic.apm.agent.impl.transaction.TraceContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.asynchttpclient.Request;

import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class AsyncHttpClientRedirectInstrumentation extends ElasticApmInstrumentation {
    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.asynchttpclient.netty.handler.intercept.Redirect30xInterceptor");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return null; //named("exitAfterHandlingRedirect");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "asynchttpclient");
    }

    @Override
    public Class<?> getAdviceClass() {
        return AsyncHttpClientRedirectAdvice.class;
    }

    public static class AsyncHttpClientRedirectAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public void onAfterExecute(@Advice.Argument(value = 3) Request originalRequest) {
            if (tracer == null || originalRequest == null) {
                return;
            }

            if (originalRequest.getHeaders().contains(TraceContext.TRACE_PARENT_HEADER)) {
                String traceContext = originalRequest.getHeaders().get(TraceContext.TRACE_PARENT_HEADER);
                if (traceContext != null) {
                    originalRequest.getHeaders().add(TraceContext.TRACE_PARENT_HEADER, traceContext);
                }
            }
        }
    }
}
