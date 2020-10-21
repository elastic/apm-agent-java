// lucee.commons.net.http.httpclient.HTTPEngine4Impl#_invoke
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
package co.elastic.apm.agent.lucee;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import java.util.Collection;
import java.util.Arrays;
import java.net.URL;
import org.apache.http.client.methods.HttpUriRequest;
import lucee.commons.net.http.Header;
import lucee.commons.net.http.HTTPResponse;

import co.elastic.apm.agent.lucee.LuceeHttpEngineHeaderSetter;

public class LuceeHttpEngineInstrumentation extends TracerAwareInstrumentation {

    // We can refer OkHttp types thanks to type erasure
    @VisibleForAdvice
    @Nullable
    public static HelperClassManager<TextHeaderSetter<HttpUriRequest>> headerSetterHelperManager;

    public LuceeHttpEngineInstrumentation(ElasticApmTracer tracer) {
        synchronized (LuceeHttpHeaderSetter.class) {
            if (headerSetterHelperManager == null) {
                headerSetterHelperManager = HelperClassManager.ForAnyClassLoader.of(tracer,
                    "co.elastic.apm.agent.lucee.LuceeHttpEngineHeaderSetter"
                );
            }
        }
    }

    // lucee.runtime.tag.Http#doEndTag
    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("lucee.commons.net.http.httpclient.HTTPEngine4Impl");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("_invoke");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "lucee-cfhttp");
    }

    @Override
    public Class<?> getAdviceClass() {
        return CfHTTPEngineAdvice.class;
    }
    @VisibleForAdvice
    public static class CfHTTPEngineAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onBeforeExecute(
                @Advice.Argument(value=0) @Nullable URL url,
                @Advice.Argument(value=1, readOnly=false) @Nullable HttpUriRequest request,
                @Advice.Local("span") @Nullable Span span) {

            if (tracer == null || tracer.getActive() == null) {
                return;
            }

            final AbstractSpan<?> parent = tracer.getActive();
            span = HttpClientHelper.startHttpClientSpan(parent, (request != null)?request.getMethod():"UNKNOWN", url.toString(), url.getProtocol(), url.getHost(), url.getPort());
            if (span != null) {
                if (request != null) {
                    TextHeaderSetter<HttpUriRequest> headerSetter = headerSetterHelperManager.getForClassLoaderOfClass(HttpUriRequest.class);
                    span.propagateTraceContext(request, headerSetter);
                }
                span.activate();
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void onAfterExecute(@Advice.Return @Nullable HTTPResponse response,
                                          @Advice.Local("span") @Nullable Span span,
                                          @Advice.Thrown @Nullable Throwable t) {
            if (span != null) {
                try {
                    span.getContext().getHttp().withStatusCode(response.getStatusCode());
                    span.captureException(t);
                } finally {
                    span.deactivate().end();
                }
            }
        }
    }
}
