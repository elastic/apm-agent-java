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
package co.elastic.apm.agent.resttemplate;

import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import co.elastic.apm.agent.resttemplate.helper.RestTemplateInstrumentationHelper;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.AsyncClientHttpRequest;
import org.springframework.util.concurrent.ListenableFuture;
import javax.annotation.Nullable;
import java.util.Objects;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class AsyncRestTemplateInstrumentation extends AbstractRestTemplateInstrumentation {

    @Nullable
    @VisibleForAdvice
    public static HelperClassManager<RestTemplateInstrumentationHelper<ListenableFuture<?>>> helperClassManager;

    public AsyncRestTemplateInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
        synchronized (AsyncRestTemplateInstrumentation.class) {
            if (helperClassManager == null) {
                helperClassManager = HelperClassManager.ForAnyClassLoader.of(tracer,
                    "co.elastic.apm.agent.resttemplate.helper.RestTemplateInstrumentationHelperImpl",
                    "co.elastic.apm.agent.resttemplate.helper.ListenableFutureWrapper",
                    "co.elastic.apm.agent.resttemplate.helper.RestTemplateInstrumentationHelperImpl$ListenableFutureWrapperAllocator");
            }
        }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    private static void beforeExecute(@Advice.This AsyncClientHttpRequest request,
                                      @Advice.Local("span") Span span) {
        if (tracer == null || tracer.getActive() == null) {
            return;
        }
        final TraceContextHolder<?> parent = tracer.getActive();
        span = HttpClientHelper.startHttpClientSpan(parent, Objects.toString(request.getMethod()), request.getURI(),
            request.getURI().getHost());
        if (span != null) {
            span.activate();
            if (headerSetterHelperManager != null) {
                TextHeaderSetter<HttpRequest> headerSetter = headerSetterHelperManager.getForClassLoaderOfClass(HttpRequest.class);
                if (headerSetter != null) {
                    span.getTraceContext().setOutgoingTraceContextHeaders(request, headerSetter);
                }
            }
        }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void afterExecute(@Advice.Local("span") @Nullable Span span,
                                      @Advice.Return(readOnly = false) @Nullable ListenableFuture<?> listenableFuture,
                                      @Advice.Thrown @Nullable Throwable t) {
        if (span != null) {
            RestTemplateInstrumentationHelper<ListenableFuture<?>> asyncHelper =
                helperClassManager.getForClassLoaderOfClass(ListenableFuture.class);
            if (asyncHelper != null && listenableFuture != null) {
                listenableFuture = asyncHelper.wrapListenableFuture(listenableFuture, span);
            }
            span.deactivate();
        }
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("executeAsync");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.springframework.http.client.AbstractAsyncClientHttpRequest");
    }
}
