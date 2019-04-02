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
package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class ForwardIncludeRequestDispatcherInstrumentation extends ElasticApmInstrumentation {

    private static final String SPAN_TYPE_REQUEST_DISPATCHER_FORWARD = "servlet.request-dispatcher.forward";
    private static final String FORWARD = "FORWARD";
    private static final String SPAN_TYPE_REQUEST_DISPATCHER_INCLUDE = "servlet.request-dispatcher.include";
    private static final String INCLUDE = "INCLUDE";

    @Override
    public Class<?> getAdviceClass() {
        return ForwardIncludeRequestDispatcherAdvice.class;
    }

    public static class ForwardIncludeRequestDispatcherAdvice extends ForwardIncludeRequestDispatcherInstrumentation {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void beforeExecute(@Advice.Local("span") @Nullable Span span,
                                          @Advice.Origin("#m") String method,
                                          @Advice.Argument(0) @Nullable ServletRequest request) {
            if (tracer == null || tracer.getActive() == null) {
                return;
            }
            final TraceContextHolder<?> parent = tracer.getActive();

            if (request != null && request instanceof HttpServletRequest) {
                HttpServletRequest httpServletRequest = (HttpServletRequest) request;
                String servletPath = "";
                if (FORWARD.equalsIgnoreCase(method)) {
                    servletPath = (String) httpServletRequest.getAttribute(RequestDispatcher.FORWARD_SERVLET_PATH);
                    span = parent.createSpan().withType(SPAN_TYPE_REQUEST_DISPATCHER_FORWARD).withName(FORWARD);
                } else {
                    servletPath = (String) httpServletRequest.getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);
                    span = parent.createSpan().withType(SPAN_TYPE_REQUEST_DISPATCHER_INCLUDE).withName(INCLUDE);
                }
                if (servletPath != null) {
                    span.appendToName(" ").appendToName(servletPath);
                }
                span.activate();
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void afterExecute(@Advice.Local("span") @Nullable Span span,
                                        @Advice.Thrown @Nullable Throwable t) {
            if (span != null) {
                span.captureException(t)
                    .deactivate()
                    .end();
            }
        }
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContainsIgnoreCase("dispatcher");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return nameContainsIgnoreCase("dispatcher");
    }

    @Override
    public ElementMatcher<MethodDescription> getMethodMatcher() {
        return named("forward").and(takesArguments(2))
            .or(named("include").and(takesArguments(2)));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("servlet", "request-dispatcher");
    }
}
