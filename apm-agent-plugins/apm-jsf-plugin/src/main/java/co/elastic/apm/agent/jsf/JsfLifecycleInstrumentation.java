/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent.jsf;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import javax.faces.context.FacesContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static co.elastic.apm.agent.impl.transaction.AbstractSpan.PRIO_HIGH_LEVEL_FRAMEWORK;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Instruments javax.faces.lifecycle.Lifecycle#execute and javax.faces.lifecycle.Lifecycle#render.
 * Code is duplicated because it is injected inline
 */
public abstract class JsfLifecycleInstrumentation extends ElasticApmInstrumentation {
    private static final String SPAN_TYPE = "template";
    private static final String SPAN_SUBTYPE = "jsf";
    private static final String FRAMEWORK_NAME = "JavaServer Faces";

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Lifecycle");
    }

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
        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("execute")
                .and(takesArguments(1))
                .and(takesArgument(0, named("javax.faces.context.FacesContext")));
        }

        @Override
        public Class<?> getAdviceClass() {
            return JsfLifecycleExecuteAdvice.class;
        }

        public static class JsfLifecycleExecuteAdvice {
            private static final String SPAN_ACTION = "execute";

            @SuppressWarnings("Duplicates")
            @Advice.OnMethodEnter(suppress = Throwable.class)
            public static void createExecuteSpan(@Advice.Argument(0) javax.faces.context.FacesContext facesContext,
                                                 @Advice.Local("span") Span span) {
                if (tracer != null) {
                    final AbstractSpan<?> parent = tracer.getActive();
                    if (parent == null) {
                        return;
                    }
                    if (parent instanceof Span) {
                        Span parentSpan = (Span)parent;
                        if (SPAN_SUBTYPE.equals(parentSpan.getSubtype()) && SPAN_ACTION.equals(parentSpan.getAction())) {
                            return;
                        }
                    }
                    Transaction transaction = tracer.currentTransaction();
                    if (transaction != null) {
                        try {
                            javax.faces.context.ExternalContext externalContext = facesContext.getExternalContext();
                            if (externalContext != null) {
                                transaction.withName(externalContext.getRequestServletPath(), PRIO_HIGH_LEVEL_FRAMEWORK);
                                String pathInfo = externalContext.getRequestPathInfo();
                                if (pathInfo != null) {
                                    transaction.appendToName(pathInfo, PRIO_HIGH_LEVEL_FRAMEWORK);
                                }
                            }
                            transaction.setFrameworkName(FRAMEWORK_NAME);
                        } catch (Exception e) {
                            // do nothing- rely on the default servlet name logic
                        }
                    }
                    span = parent.createSpan()
                        .withType(SPAN_TYPE)
                        .withSubtype(SPAN_SUBTYPE)
                        .withAction(SPAN_ACTION)
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
    }

    public static class JsfLifecycleRenderInstrumentation extends JsfLifecycleInstrumentation {
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

        @Override
        public Class<?> getAdviceClass() {
            return JsfLifecycleRenderAdvice.class;
        }

        public static class JsfLifecycleRenderAdvice {
            private static final String SPAN_ACTION = "render";

            @SuppressWarnings("Duplicates")
            @Advice.OnMethodEnter(suppress = Throwable.class)
            public static void createRenderSpan(@Advice.Local("span") Span span) {
                if (tracer != null) {
                    final AbstractSpan<?> parent = tracer.getActive();
                    if (parent == null) {
                        return;
                    }
                    if (parent instanceof Span) {
                        Span parentSpan = (Span)parent;
                        if (SPAN_SUBTYPE.equals(parentSpan.getSubtype()) && SPAN_ACTION.equals(parentSpan.getAction())) {
                            return;
                        }
                    }
                    span = parent.createSpan()
                        .withType(SPAN_TYPE)
                        .withSubtype(SPAN_SUBTYPE)
                        .withAction(SPAN_ACTION)
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
}
