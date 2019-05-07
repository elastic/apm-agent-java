/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.httpclient;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.protocol.HttpContext;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class ApacheHttpAsyncClientInstrumentation extends ElasticApmInstrumentation {

    // Referencing specific Apache HTTP client classes are allowed due to type erasure
    public static HelperClassManager<ApacheHttpAsyncClientHelper<HttpAsyncRequestProducer, FutureCallback<?>, HttpContext>> helperManager;

    private static class ApacheHttpAsyncClientAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onBeforeExecute(@Advice.Argument(value = 0, readOnly = false) HttpAsyncRequestProducer requestProducer,
                                            @Advice.Argument(2) HttpContext context,
                                            @Advice.Argument(value = 3, readOnly = false) FutureCallback futureCallback,
                                            @Advice.Local("span") @Nullable Span span,
                                            @Advice.Local("wrapped") boolean wrapped) {
            if (tracer == null || tracer.getActive() == null) {
                return;
            }
            final TraceContextHolder<?> parent = tracer.getActive();
            span = parent.createExitSpan();
            if (span != null) {
                span.withType(HttpClientHelper.EXTERNAL_TYPE)
                    .withSubtype(HttpClientHelper.HTTP_SUBTYPE)
                    .activate();

                ApacheHttpAsyncClientHelper<HttpAsyncRequestProducer, FutureCallback<?>, HttpContext> helper =
                    helperManager.getForClassLoaderOfClass(HttpAsyncRequestProducer.class);
                if (helper != null) {
                    requestProducer = helper.wrapRequestProducer(requestProducer, span);
                    futureCallback = helper.wrapFutureCallback(futureCallback, context, span);
                    wrapped = true;
                }
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void onAfterExecute(@Advice.Local("span") @Nullable Span span,
                                          @Advice.Local("wrapped") boolean wrapped,
                                          @Advice.Thrown @Nullable Throwable t) {
            if (span != null) {
                // Deactivate in this thread. Span will be ended and reported by the listener
                span.deactivate();

                if (!wrapped) {
                    // Listener is not wrapped- we need to end the span so to avoid leak and report error if occurred during method invocation
                    span.captureException(t);
                    span.end();
                }
            }
        }
    }

    public ApacheHttpAsyncClientInstrumentation(ElasticApmTracer tracer) {
        helperManager = HelperClassManager.ForAnyClassLoader.of(tracer,
            "co.elastic.apm.agent.httpclient.ApacheHttpAsyncClientHelperImpl",
            "co.elastic.apm.agent.httpclient.HttpAsyncRequestProducerWrapper",
            "co.elastic.apm.agent.httpclient.ApacheHttpAsyncClientHelperImpl$RequestProducerWrapperAllocator",
            "co.elastic.apm.agent.httpclient.FutureCallbackWrapper",
            "co.elastic.apm.agent.httpclient.ApacheHttpAsyncClientHelperImpl$FutureCallbackWrapperAllocator");
    }

    @Override
    public Class<?> getAdviceClass() {
        return ApacheHttpAsyncClientAdvice.class;
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("org.apache.http.nio.client.HttpAsyncClient"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("execute")
            .and(takesArguments(4))
            .and(takesArgument(0, named("org.apache.http.nio.protocol.HttpAsyncRequestProducer")))
            .and(takesArgument(1, named("org.apache.http.nio.protocol.HttpAsyncResponseConsumer")))
            .and(takesArgument(2, named("org.apache.http.protocol.HttpContext")))
            .and(takesArgument(3, named("org.apache.http.concurrent.FutureCallback")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "apache-httpclient");
    }
}
