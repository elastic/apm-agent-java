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

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class SpringRestTemplateInstrumentation extends ElasticApmInstrumentation {

    // We can refer Spring type thanks to type erasure
    @VisibleForAdvice
    @Nullable
    public static HelperClassManager<TextHeaderSetter<HttpRequest>> headerSetterHelperManager;

    public SpringRestTemplateInstrumentation(ElasticApmTracer tracer) {
        synchronized (SpringRestTemplateInstrumentation.class) {
            if (headerSetterHelperManager == null) {
                headerSetterHelperManager = HelperClassManager.ForAnyClassLoader.of(tracer,
                    "co.elastic.apm.agent.resttemplate.SpringRestRequestHeaderSetter"
                );
            }
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return nameStartsWith("org.springframework")
            .and(not(isInterface()))
            // only traverse the object hierarchy if the class declares the method to instrument at all
            .and(declaresMethod(getMethodMatcher()))
            .and(hasSuperType(named("org.springframework.http.client.ClientHttpRequest")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("execute")
            .and(takesArguments(0))
            .and(returns(hasSuperType(named("org.springframework.http.client.ClientHttpResponse"))));
    }

    @Override
    public Class<?> getAdviceClass() {
        return SpringRestTemplateAdvice.class;
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "spring-resttemplate");
    }

    public static class SpringRestTemplateAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void beforeExecute(@Advice.This ClientHttpRequest request,
                                          @Advice.Local("span") Span span) {
            if (tracer == null || tracer.getActive() == null) {
                return;
            }
            final AbstractSpan<?> parent = tracer.getActive();
            span = HttpClientHelper.startHttpClientSpan(parent, Objects.toString(request.getMethod()), request.getURI(),
                request.getURI().getHost());
            if (span != null) {
                span.activate();
                if (headerSetterHelperManager != null) {
                    TextHeaderSetter<HttpRequest> headerSetter = headerSetterHelperManager.getForClassLoaderOfClass(HttpRequest.class);
                    if (headerSetter != null) {
                        span.propagateTraceContext(request, headerSetter);
                    }
                }
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        private static void afterExecute(@Advice.Return @Nullable ClientHttpResponse clientHttpResponse,
                                         @Advice.Local("span") @Nullable Span span,
                                         @Advice.Thrown @Nullable Throwable t) throws IOException {
            if (span != null) {
                try {
                    if (clientHttpResponse != null) {
                        int statusCode = clientHttpResponse.getRawStatusCode();
                        span.getContext().getHttp().withStatusCode(statusCode);
                    }
                    span.captureException(t);
                } finally {
                    span.deactivate().end();
                }
            }
        }
    }
}
