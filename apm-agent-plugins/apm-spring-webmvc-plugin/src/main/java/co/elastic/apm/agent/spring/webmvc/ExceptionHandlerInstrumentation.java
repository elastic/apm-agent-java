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
package co.elastic.apm.agent.spring.webmvc;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class ExceptionHandlerInstrumentation extends ElasticApmInstrumentation {

    private final Collection<String> applicationPackages;

    public ExceptionHandlerInstrumentation(ElasticApmTracer tracer) {
        applicationPackages = tracer.getConfig(StacktraceConfiguration.class).getApplicationPackages();
    }

    @Override
    public Class<?> getAdviceClass() {
        return ExceptionHandlerAdviceService.class;
    }

    public static class ExceptionHandlerAdviceService {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void captureException(@Advice.Argument(4) Exception e) {
            if (tracer == null || tracer.getActive() == null) {
                return;
            }
            final TraceContextHolder<?> parent = tracer.getActive();

            if (parent != null) {
                parent.captureException(e);
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        private static void afterExecute(@Advice.Thrown @Nullable Throwable t) {
        }
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader())
            .and(classLoaderCanLoadClass("org.springframework.web.bind.annotation.ControllerAdvice"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.springframework.web.servlet.DispatcherServlet");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("processDispatchResult")
            .and(takesArgument(0, named("javax.servlet.http.HttpServletRequest")))
            .and(takesArgument(1, named("javax.servlet.http.HttpServletResponse")))
            .and(takesArgument(2, named("org.springframework.web.servlet.HandlerExecutionChain")))
            .and(takesArgument(3, named("org.springframework.web.servlet.ModelAndView")))
            .and(takesArgument(4, named("java.lang.Exception")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("exception-handler");
    }
}
