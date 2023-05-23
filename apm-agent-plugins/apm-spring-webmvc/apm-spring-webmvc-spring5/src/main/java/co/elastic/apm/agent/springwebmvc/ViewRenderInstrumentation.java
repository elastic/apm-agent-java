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
package co.elastic.apm.agent.springwebmvc;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.servlet.Constants;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.servlet.view.AbstractView;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class ViewRenderInstrumentation extends TracerAwareInstrumentation {

    private static final String SPAN_TYPE = "template";
    private static final String SPAN_ACTION = "render";
    private static final String DISPATCHER_SERVLET_RENDER_METHOD = "View#render";
    private static final Map<String, String> subTypeCache = new ConcurrentHashMap<>();

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.springwebmvc.ViewRenderInstrumentation$ViewRenderAdviceService";
    }

    public static class ViewRenderAdviceService {

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object beforeExecute(@Advice.This Object thiz) {
            if (tracer.getActive() == null) {
                return null;
            }
            final AbstractSpan<?> parent = tracer.getActive();

            String className = thiz.getClass().getName();
            Span<?> span = parent.createSpan()
                .withType(SPAN_TYPE)
                .withSubtype(getSubtype(className))
                .withAction(SPAN_ACTION)
                .withName(DISPATCHER_SERVLET_RENDER_METHOD);

            if (thiz instanceof AbstractView) {
                AbstractView view = (AbstractView) thiz;
                String beanName = view.getBeanName();
                if (beanName != null) {
                    span.appendToName(" ").appendToName(beanName);
                }
            }
            span.activate();
            return span;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void afterExecute(@Advice.Enter @Nullable Object spanObj,
                                        @Advice.Thrown @Nullable Throwable t) {
            if (spanObj instanceof Span<?>) {
                Span<?> span = (Span<?>) spanObj;
                span.captureException(t)
                    .deactivate()
                    .end();
            }
        }

        public static String getSubtype(String className) {
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
                    String subType = subTypeCache.get(className);
                    if (subType != null) {
                        return subType;
                    } else {
                        int indexOfLastDot = className.lastIndexOf('.');
                        int indexOfView = className.lastIndexOf("View");
                        subType = className.substring(indexOfLastDot + 1, indexOfView > indexOfLastDot ? indexOfView : className.length());
                        if (subTypeCache.size() < 1000) {
                            subTypeCache.put(className, subType);
                        }
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

        ElementMatcher.Junction<MethodDescription> javaxMatcher =
            takesArgument(1, Constants.ServletImpl.JAVAX.httpRequestClassMatcher())
                .and(takesArgument(2, Constants.ServletImpl.JAVAX.httpResponseClassMatcher()));
        ElementMatcher.Junction<MethodDescription> jakartaMatcher =
            takesArgument(1, Constants.ServletImpl.JAKARTA.httpRequestClassMatcher())
                .and(takesArgument(2, Constants.ServletImpl.JAKARTA.httpResponseClassMatcher()));

        return named("render")
            .and(takesArgument(0, named("java.util.Map")))
            .and(javaxMatcher.or(jakartaMatcher));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton("spring-view-render");
    }
}

