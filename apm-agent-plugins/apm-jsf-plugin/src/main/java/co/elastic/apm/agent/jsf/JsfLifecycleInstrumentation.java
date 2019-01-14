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
package co.elastic.apm.agent.jsf;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public abstract class JsfLifecycleInstrumentation extends ElasticApmInstrumentation {
    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader())
            .and(classLoaderCanLoadClass("javax.faces.lifecycle.Lifecycle"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("javax.faces.lifecycle.Lifecycle"));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("servlet-api", "jsf");
    }

    public static class JsfLifecycleExecuteInstrumentation extends JsfLifecycleInstrumentation {
        private static final String SPAN_TYPE = "jsf-execute";

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("execute")
                .and(takesArguments(1))
                .and(takesArgument(0, named("javax.faces.context.FacesContext")));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void createExecuteSpan(@Advice.Local("span") Span span) {
            if (tracer != null) {
                final AbstractSpan<?> activeSpan = tracer.activeSpan();
                if (activeSpan == null || !activeSpan.isSampled() || SPAN_TYPE.equals(activeSpan.getType())) {
                    return;
                }
                span = activeSpan.createSpan()
                    .withType(SPAN_TYPE)
                    .withName("JSF Execute");
                span.activate();
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void endExecuteSpan(@Advice.Local("span") @Nullable Span span,
                                         @Advice.Thrown @Nullable Throwable t) {
            if (span != null) {
                try {
                    if (t != null) {
                        span.captureException(t);
                    }
                } finally {
                    span.deactivate().end();
                }

            }
        }
    }

    public static class JsfLifecycleRenderInstrumentation extends JsfLifecycleInstrumentation {
        private static final String SPAN_TYPE = "jsf-render";

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("render")
                .and(takesArguments(1))
                .and(takesArgument(0, named("javax.faces.context.FacesContext")));
        }

        @Override
        public Collection<String> getInstrumentationGroupNames() {
            List<String> ret = new ArrayList<>(super.getInstrumentationGroupNames());
            ret.add("render");
            return ret;
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void createRenderSpan(@Advice.Local("span") Span span) {
            if (tracer != null) {
                final AbstractSpan<?> activeSpan = tracer.activeSpan();
                if (activeSpan == null || !activeSpan.isSampled() || SPAN_TYPE.equals(activeSpan.getType())) {
                    return;
                }
                span = activeSpan.createSpan()
                    .withType(SPAN_TYPE)
                    .withName("JSF Render");
                span.activate();
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void endRenderSpan(@Advice.Local("span") @Nullable Span span,
                                         @Advice.Thrown @Nullable Throwable t) {
            if (span != null) {
                try {
                    if (t != null) {
                        span.captureException(t);
                    }
                } finally {
                    span.deactivate().end();
                }

            }
        }
    }
}
