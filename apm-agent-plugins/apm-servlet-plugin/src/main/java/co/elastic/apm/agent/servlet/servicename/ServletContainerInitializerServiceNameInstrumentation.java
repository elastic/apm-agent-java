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
package co.elastic.apm.agent.servlet.servicename;

import co.elastic.apm.agent.servlet.AbstractServletInstrumentation;
import co.elastic.apm.agent.servlet.Constants;
import co.elastic.apm.agent.servlet.ServletServiceNameHelper;
import co.elastic.apm.agent.servlet.adapter.JakartaServletApiAdapter;
import co.elastic.apm.agent.servlet.adapter.JavaxServletApiAdapter;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Set;

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
 *     <li>{@link javax.servlet.ServletContainerInitializer#onStartup(java.util.Set, javax.servlet.ServletContext)} </li>
 *     <li>{@link jakarta.servlet.ServletContainerInitializer#onStartup(java.util.Set, jakarta.servlet.ServletContext)}</li>
 * </ul>
 * <p>
 * Determines the service name based on the webapp's {@code META-INF/MANIFEST.MF} file early in the startup process.
 * As this doesn't work with runtime attachment, the service name is also determined when the first request comes in.
 */
public abstract class ServletContainerInitializerServiceNameInstrumentation extends AbstractServletInstrumentation {

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Initializer");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface()).and(hasSuperType(namedOneOf(
            "javax.servlet.ServletContainerInitializer", "jakarta.servlet.ServletContainerInitializer")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("onStartup")
            .and(takesArguments(2))
            .and(takesArgument(0, Set.class))
            .and(takesArgument(1, nameEndsWith("ServletContext")));
    }

    public static class JavaxInitServiceNameInstrumentation extends ServletContainerInitializerServiceNameInstrumentation {

        private static final JavaxServletApiAdapter adapter = JavaxServletApiAdapter.get();

        @Override
        public Constants.ServletImpl getImplConstants() {
            return Constants.ServletImpl.JAVAX;
        }

        public static class AdviceClass {

            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void onEnter(@Advice.Argument(1) @Nullable Object servletContext) {
                if (servletContext instanceof javax.servlet.ServletContext) {
                    ServletServiceNameHelper.determineServiceName(adapter, (javax.servlet.ServletContext) servletContext, tracer);
                }
            }
        }
    }

    public static class JakartaInitServiceNameInstrumentation extends ServletContainerInitializerServiceNameInstrumentation {

        private static final JakartaServletApiAdapter adapter = JakartaServletApiAdapter.get();

        @Override
        public Constants.ServletImpl getImplConstants() {
            return Constants.ServletImpl.JAVAX;
        }

        public static class AdviceClass {

            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void onEnter(@Advice.Argument(1) @Nullable Object servletContext) {
                if (servletContext instanceof jakarta.servlet.ServletContext) {
                    ServletServiceNameHelper.determineServiceName(adapter, (jakarta.servlet.ServletContext) servletContext, tracer);
                }
            }
        }
    }
}
