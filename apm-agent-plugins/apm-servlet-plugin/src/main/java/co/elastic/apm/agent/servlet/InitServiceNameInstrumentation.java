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
package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.servlet.adapter.JakartaServletApiAdapter;
import co.elastic.apm.agent.servlet.adapter.JavaxServletApiAdapter;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Instruments
 * <ul>
 *     <li>{@link javax.servlet.Filter#init(javax.servlet.FilterConfig)}</li>
 *     <li>{@link jakarta.servlet.Filter#init(jakarta.servlet.FilterConfig)}</li>
 *     <li>{@link javax.servlet.Servlet#init(javax.servlet.ServletConfig)}</li>
 *     <li>{@link jakarta.servlet.Servlet#init(jakarta.servlet.ServletConfig)}</li>
 * </ul>
 *
 * Determines the service name based on the webapp's {@code META-INF/MANIFEST.MF} file early in the startup process.
 * As this doesn't work with runtime attachment, the service name is also determined when the first request comes in.
 */
public abstract class InitServiceNameInstrumentation extends AbstractServletInstrumentation {

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Filter").or(nameContains("Servlet"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface()).and(hasSuperType(namedOneOf("javax.servlet.Filter", "javax.servlet.Servlet", "jakarta.servlet.Filter", "jakarta.servlet.Servlet")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("init")
            .and(takesArguments(1))
            .and(takesArgument(0, nameEndsWith("Config")));
    }

    public static class JavaxInitServiceNameInstrumentation extends InitServiceNameInstrumentation {

        private static final JavaxServletApiAdapter adapter = JavaxServletApiAdapter.get();

        @Override
        public String rootClassNameThatClassloaderCanLoad() {
            return "javax.servlet.AsyncContext";
        }

        public static class AdviceClass {
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void onEnter(@Advice.Argument(0) @Nullable Object config) {
                if (config == null) {
                    return;
                }
                javax.servlet.ServletContext servletContext;
                if (config instanceof javax.servlet.FilterConfig) {
                    servletContext = adapter.getServletContextFromFilterConfig((javax.servlet.FilterConfig) config);
                } else if (config instanceof javax.servlet.ServletConfig) {
                    servletContext = adapter.getServletContextFromServletConfig((javax.servlet.ServletConfig) config);
                } else {
                    return;
                }
                ServletServiceNameHelper.determineServiceName(adapter, servletContext, tracer);
            }
        }
    }

    public static class JakartaInitServiceNameInstrumentation extends InitServiceNameInstrumentation {

        private static final JakartaServletApiAdapter adapter = JakartaServletApiAdapter.get();

        @Override
        public String rootClassNameThatClassloaderCanLoad() {
            return "jakarta.servlet.AsyncContext";
        }

        public static class AdviceClass {

            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void onEnter(@Advice.Argument(0) @Nullable Object config) {
                if (config == null) {
                    return;
                }
                jakarta.servlet.ServletContext servletContext;
                if (config instanceof jakarta.servlet.FilterConfig) {
                    servletContext = adapter.getServletContextFromFilterConfig((jakarta.servlet.FilterConfig) config);
                } else if (config instanceof jakarta.servlet.ServletConfig) {
                    servletContext = adapter.getServletContextFromServletConfig((jakarta.servlet.ServletConfig) config);
                } else {
                    return;
                }
                ServletServiceNameHelper.determineServiceName(adapter, servletContext, tracer);
            }
        }
    }
}
