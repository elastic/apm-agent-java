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
package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import java.util.Arrays;
import java.util.Collection;

import static co.elastic.apm.agent.servlet.ServletInstrumentation.SERVLET_API;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameContainsIgnoreCase;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class ServletContextServiceNameInstrumentation extends ElasticApmInstrumentation {
    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Servlet").or(nameContainsIgnoreCase("jsp"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(hasSuperType(named("javax.servlet.Servlet")));
    }

    // TODO don't instrument init as that does not play well with runtime attachment
    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("init")
            .and(takesArgument(0, named("javax.servlet.ServletConfig")))
            .and(takesArguments(1));
    }

    @Override
    public Class<?> getAdviceClass() {
        return ServletContextServiceNameAdvice.class;
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList(SERVLET_API, "servlet-service-name");
    }

    public static class ServletContextServiceNameAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onServletInit(@Nullable @Advice.Argument(0) ServletConfig servletConfig) {
            if (tracer == null || servletConfig == null) {
                return;
            }
            ServletContext servletContext = servletConfig.getServletContext();
            if (servletContext == null) {
                return;
            }
            @Nullable
            String serviceName = servletContext.getServletContextName();
            if ("application".equals(serviceName) || "".equals(serviceName) || "/".equals(serviceName)) {
                // payara returns an empty string as opposed to null
                // spring applications which did not set spring.application.name have application as the default
                // jetty returns context path when no display name is set, which could be the root context of "/"
                // this is a worse default than the one we would otherwise choose
                serviceName = null;
            }
            final String contextPath = servletContext.getContextPath();
            if (serviceName == null && contextPath != null && !contextPath.isEmpty()) {
                // remove leading slash
                serviceName = contextPath.substring(1);
            }
            if (serviceName != null) {
                tracer.overrideServiceNameForClassLoader(servletContext.getClassLoader(), serviceName);
            }
        }
    }
}
