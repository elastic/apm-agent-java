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
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.servlet.view.AbstractView;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class ViewRenderInstrumentation extends ElasticApmInstrumentation {

    private static final String SPAN_TYPE = "template";
    private static final String SPAN_ACTION = "render";
    private static final String DISPATCHER_SERVLET_RENDER_METHOD = "View#render";
    private static Map<String, String> subTypeCache = new ConcurrentHashMap<>();

    @Override
    public Class<?> getAdviceClass() {
        return ViewRenderAdviceService.class;
    }

    public static class ViewRenderAdviceService {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void beforeExecute(@Advice.Local("span") @Nullable Span span,
                                         @Advice.This @Nullable Object thiz) {
            if (tracer == null || tracer.getActive() == null) {
                return;
            }
            final TraceContextHolder<?> parent = tracer.getActive();

            String className = thiz.getClass().getName();
            span = parent.createSpan()
                .withType(SPAN_TYPE)
                .withSubtype(defineSubtype(className))
                .withAction(SPAN_ACTION)
                .withName(DISPATCHER_SERVLET_RENDER_METHOD);

            if (thiz instanceof AbstractView) {
                AbstractView view = (AbstractView) thiz;
                span.appendToName(" ").appendToName(view.getBeanName());
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

        public static String defineSubtype(String className) {
            switch (className) {
                case "org.springframework.web.servlet.view.groovy.GroovyMarkupView":
                    return "GroovyMarkup";
                case "org.springframework.web.servlet.view.freemarker.FreeMarkerView":
                    return "FreeMarker";
                case "org.springframework.web.servlet.view.json.MappingJackson2JsonView":
                    return "MappingJackson2Json";
                case "de.neuland.jade4j.spring.view.JadeView":
                    return "Jade";
                case "org.springframework.web.servlet.view.InternalResourceView":
                    return "InternalResource";
                case "org.thymeleaf.spring4.view.ThymeleafView":
                    return "Thymeleaf";
                default:
                    if (subTypeCache.containsKey(className)) {
                        return subTypeCache.get(className);
                    } else {
                        String subType = className.substring(className.lastIndexOf('.') + 1, className.lastIndexOf("View"));
                        subTypeCache.put(className, subType);
                        return subType;
                    }
            }
        }
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("View");
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
        return Arrays.asList("spring-view-render");
    }
}

