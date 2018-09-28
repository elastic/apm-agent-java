/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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
package co.elastic.apm.resttemplate;

import co.elastic.apm.bci.ElasticApmInstrumentation;
import co.elastic.apm.http.client.HttpClientHelper;
import co.elastic.apm.impl.transaction.AbstractSpan;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.transaction.TraceContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

import static co.elastic.apm.http.client.HttpClientHelper.HTTP_CLIENT_SPAN_TYPE_PREFIX;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class SpringRestTemplateInstrumentation extends ElasticApmInstrumentation {

    private static final String SPAN_TYPE_SPRING_REST_TEMPLATE = HTTP_CLIENT_SPAN_TYPE_PREFIX + "spring-resttemplate";

    @Advice.OnMethodEnter
    private static void beforeExecute(@Advice.This ClientHttpRequest request,
                                      @Advice.Local("span") Span span) {
        if (tracer == null || tracer.getActive() == null) {
            return;
        }
        final AbstractSpan<?> parent = tracer.getActive();
        span = HttpClientHelper.startHttpClientSpan(parent, Objects.toString(request.getMethod()), request.getURI(),
            request.getURI().getHost(), SPAN_TYPE_SPRING_REST_TEMPLATE);
        if (span != null) {
            request.getHeaders().add(TraceContext.TRACE_PARENT_HEADER, span.getTraceContext().getOutgoingTraceParentHeader().toString());
        }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    private static void afterExecute(@Advice.Return ClientHttpResponse clientHttpResponse,
                                     @Advice.Local("span") @Nullable Span span,
                                     @Advice.Thrown @Nullable Throwable t) {
        if (span != null) {
            span.captureException(t)
                .deactivate()
                .end();
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
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "spring-resttemplate");
    }
}
