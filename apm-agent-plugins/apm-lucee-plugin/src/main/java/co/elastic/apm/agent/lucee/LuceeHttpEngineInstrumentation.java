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
    public String getAdviceClassName() {
        return CfHTTPEngineAdvice.class.getName();
    }
    public static class CfHTTPEngineAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static @Nullable Object onBeforeExecute(
                @Advice.Argument(value=0) @Nullable URL url,
                @Advice.Argument(value=1) @Nullable HttpUriRequest request) {

            if (tracer == null || tracer.getActive() == null) {
                return null;
            }

            final AbstractSpan<?> parent = tracer.getActive();
            Object span = HttpClientHelper.startHttpClientSpan(parent, (request != null)?request.getMethod():"UNKNOWN", url.toString(), url.getProtocol(), url.getHost(), url.getPort());
            if (span != null) {
                if (request != null) {
                    try {
                        TextHeaderSetter<HttpUriRequest> headerSetter = new LuceeHttpEngineHeaderSetter();
                        ((Span)span).propagateTraceContext(request, headerSetter);
                    } catch (Throwable e) {}
                }
                ((Span)span).activate();
            }
            return span;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onAfterExecute(@Advice.Return @Nullable HTTPResponse response,
                                          @Advice.Enter @Nullable Object span,
                                          @Advice.Thrown @Nullable Throwable t) {
            if (span != null) {
                try {
                    if (span instanceof Span) {
                        ((Span) span).getContext().getHttp().withStatusCode(response.getStatusCode());
                    }
                    ((Span)span).captureException(t);
                } finally {
                    ((Span)span).deactivate().end();
                }
            }
        }
    }
}
