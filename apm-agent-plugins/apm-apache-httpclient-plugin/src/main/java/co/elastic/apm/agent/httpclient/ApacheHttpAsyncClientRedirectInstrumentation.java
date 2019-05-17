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
import co.elastic.apm.agent.impl.transaction.TraceContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.HttpRequest;

import javax.annotation.Nullable;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Collection;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.implementationVersionLte;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * In versions 4.0.1 or lower, the headers are not automatically copied to redirected HttpRequests, so this copies them over
 */
public class ApacheHttpAsyncClientRedirectInstrumentation extends ElasticApmInstrumentation {

    private static class ApacheHttpAsyncClientRedirectAdvice {
        @Advice.OnMethodExit(suppress = Throwable.class)
        private static void onAfterExecute(@Advice.Argument(value = 0) HttpRequest original,
                                           @Advice.Return(typing = Assigner.Typing.DYNAMIC) @Nullable HttpRequest redirect) {
            if (tracer == null || redirect == null) {
                return;
            }
            // org.apache.http.HttpMessage#containsHeader implementations do not allocate iterator since 4.0.1
            if (original.containsHeader(TraceContext.TRACE_PARENT_HEADER) && !redirect.containsHeader(TraceContext.TRACE_PARENT_HEADER)) {
                String traceContext = original.getFirstHeader(TraceContext.TRACE_PARENT_HEADER).getValue();
                if (traceContext != null) {
                    redirect.setHeader(TraceContext.TRACE_PARENT_HEADER, traceContext);
                }
            }
        }
    }

    @Override
    public Class<?> getAdviceClass() {
        return ApacheHttpAsyncClientRedirectAdvice.class;
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("RedirectStrategy");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("org.apache.http.client.RedirectStrategy"));
    }

    /**
     * Apache HTTP Async client 4.0.1 is dependent on Apache HTTP client 4.3.2
     * @return a matcher for LTE 4.3.2
     */
    @Override
    public ElementMatcher.Junction<ProtectionDomain> getImplementationVersionPostFilter() {
        return implementationVersionLte("4.3.2");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("getRedirect");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "apache-httpclient");
    }
}
