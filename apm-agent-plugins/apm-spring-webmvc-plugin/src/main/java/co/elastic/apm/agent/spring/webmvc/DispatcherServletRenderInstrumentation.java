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
package co.elastic.apm.agent.spring.webmvc;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import java.util.Arrays;
import java.util.Collection;

public class DispatcherServletRenderInstrumentation extends ElasticApmInstrumentation {

    private static final String SPAN_TYPE_DISPATCHER_SERVLET_RENDER = "template.dispatcher-servlet.render";
    private static final String RENDER_METHOD = "Render";
    private static final String DISPATCHER_SERVLET_RENDER_METHOD = "DispatcherServlet#render";

    @Advice.OnMethodEnter(suppress = Throwable.class)
    private static void beforeExecute(@Advice.Argument(0) ModelAndView modelAndView,
                                      @Advice.Local("span") Span span) {
        if (tracer == null || tracer.activeSpan() == null)
            return;
        final AbstractSpan<?> parent = tracer.activeSpan();

        span = parent.createSpan().withType(SPAN_TYPE_DISPATCHER_SERVLET_RENDER);
        if (modelAndView == null || modelAndView.getViewName() == null)
            span.withName(DISPATCHER_SERVLET_RENDER_METHOD);
        else
            span.withName(RENDER_METHOD).appendToName(" ").appendToName(modelAndView.getViewName()).activate();

        span.activate();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    private static void afterExecute(@Advice.Local("span") @Nullable Span span,
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
            .and(declaresMethod(getMethodMatcher()))
            .and(named("org.springframework.web.servlet.DispatcherServlet"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("render")
            .and(takesArgument(0, named("org.springframework.web.servlet.ModelAndView")))
            .and(takesArgument(1, named("javax.servlet.http.HttpServletRequest")))
            .and(takesArgument(2, named("javax.servlet.http.HttpServletResponse")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("dispatcher-servlet", "render");
    }
}

