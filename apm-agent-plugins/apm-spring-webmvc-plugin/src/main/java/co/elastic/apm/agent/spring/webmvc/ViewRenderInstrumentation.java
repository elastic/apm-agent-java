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
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.servlet.view.AbstractView;
import org.springframework.web.servlet.view.freemarker.FreeMarkerView;
import org.springframework.web.servlet.view.json.MappingJackson2JsonView;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class ViewRenderInstrumentation extends ElasticApmInstrumentation {

    private static final String SPAN_TYPE = "template";
    private static final String SPAN_SUBTYPE = "dispatcher-servlet";
    private static final String SPAN_ACTION = "render";
    private static final String DISPATCHER_SERVLET_RENDER_METHOD = "DispatcherServlet#render";

    @Override
    public Class<?> getAdviceClass() {
        return ViewRenderAdviceService.class;
    }

    public static class ViewRenderAdviceService {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void beforeExecute(@Advice.Local("span") @Nullable Span span,
                                         @Advice.This @Nullable Object thiz) {
            System.out.println("HERE");
            if (tracer == null || tracer.getActive() == null) {
                return;
            }
            final TraceContextHolder<?> parent = tracer.getActive();

            String viewClassName = thiz.getClass().getSimpleName().replace("View", "");

            span = parent.createSpan()
                .withType(SPAN_TYPE)
                .withSubtype(viewClassName)
                .withAction(SPAN_ACTION)
                .withName(DISPATCHER_SERVLET_RENDER_METHOD);

            String viewName = null;
            if (thiz instanceof AbstractView) {
                AbstractView view = (AbstractView) thiz;
                viewName = view.getBeanName();
            }

            if (viewName != null) {
                span.appendToName(" ").appendToName(viewName);
            }
            span.activate();
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
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("org.springframework.web.servlet.View"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("render")
            .and(takesArgument(0, named("java.util.Map")))
            .and(takesArgument(1, named("javax.servlet.http.HttpServletRequest")))
            .and(takesArgument(2, named("javax.servlet.http.HttpServletResponse")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("dispatcher-servlet", "render");
    }
}

