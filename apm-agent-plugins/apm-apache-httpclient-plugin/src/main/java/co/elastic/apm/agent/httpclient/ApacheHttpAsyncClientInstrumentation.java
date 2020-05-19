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
package co.elastic.apm.agent.httpclient;

import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.httpclient.helper.ApacheHttpAsyncClientHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.HttpRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.protocol.HttpContext;

import javax.annotation.Nullable;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class ApacheHttpAsyncClientInstrumentation extends BaseApacheHttpClientInstrumentation {

    // Referencing specific Apache HTTP client classes are allowed due to type erasure
    @VisibleForAdvice
    public static HelperClassManager<ApacheHttpAsyncClientHelper<HttpAsyncRequestProducer, FutureCallback<?>, HttpContext, HttpRequest>> asyncHelperManager;

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
            final AbstractSpan<?> parent = tracer.getActive();
            span = parent.createExitSpan();
            if (span != null) {
                span.withType(HttpClientHelper.EXTERNAL_TYPE)
                    .withSubtype(HttpClientHelper.HTTP_SUBTYPE)
                    .activate();

                ApacheHttpAsyncClientHelper<HttpAsyncRequestProducer, FutureCallback<?>, HttpContext, HttpRequest> asyncHelper =
                    asyncHelperManager.getForClassLoaderOfClass(HttpAsyncRequestProducer.class);
                TextHeaderSetter<HttpRequest> headerSetter = headerSetterHelperClassManager.getForClassLoaderOfClass(HttpRequest.class);
                if (asyncHelper != null && headerSetter != null) {
                    requestProducer = asyncHelper.wrapRequestProducer(requestProducer, span, headerSetter);
                    futureCallback = asyncHelper.wrapFutureCallback(futureCallback, context, span);
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
        super(tracer);
        asyncHelperManager = HelperClassManager.ForAnyClassLoader.of(tracer,
            "co.elastic.apm.agent.httpclient.helper.ApacheHttpAsyncClientHelperImpl",
            "co.elastic.apm.agent.httpclient.helper.HttpAsyncRequestProducerWrapper",
            "co.elastic.apm.agent.httpclient.helper.ApacheHttpAsyncClientHelperImpl$RequestProducerWrapperAllocator",
            "co.elastic.apm.agent.httpclient.helper.FutureCallbackWrapper",
            "co.elastic.apm.agent.httpclient.helper.ApacheHttpAsyncClientHelperImpl$FutureCallbackWrapperAllocator");
    }

    @Override
    public Class<?> getAdviceClass() {
        return ApacheHttpAsyncClientAdvice.class;
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader())
            .and(classLoaderCanLoadClass("org.apache.http.nio.client.HttpAsyncClient"));
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("HttpAsyncClient");
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
}
