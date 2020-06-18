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
package co.elastic.apm.agent.plugin.api;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.bci.bytebuddy.postprocessor.AssignTo;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static co.elastic.apm.agent.impl.transaction.AbstractSpan.PRIO_USER_SUPPLIED;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Injects the actual implementation of the public API class co.elastic.apm.api.SpanImpl.
 * <p>
 * Used for older versions of the API, for example 1.1.0 where there was no AbstractSpanImpl
 *
 * @deprecated can be removed in version 3.0.
 * Users should be able to update the agent to 2.0, without having to simultaneously update the API.
 */
@Deprecated
public class LegacySpanInstrumentation extends ApiInstrumentation {

    private final ElementMatcher<? super MethodDescription> methodMatcher;

    public LegacySpanInstrumentation(ElementMatcher<? super MethodDescription> methodMatcher) {
        this.methodMatcher = methodMatcher;
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.apm.api.SpanImpl").and(not(hasSuperType(named("co.elastic.apm.api.AbstractSpanImpl"))));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return methodMatcher;
    }

    public static class SetNameInstrumentation extends LegacySpanInstrumentation {
        public SetNameInstrumentation() {
            super(named("setName"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter
        public static void setName(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) AbstractSpan<?> span,
                                   @Advice.Argument(0) String name) {
            span.withName(name, PRIO_USER_SUPPLIED);
        }
    }

    public static class SetTypeInstrumentation extends LegacySpanInstrumentation {
        public SetTypeInstrumentation() {
            super(named("setType"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter
        public static void setType(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) AbstractSpan<?> span,
                                   @Advice.Argument(0) String type) {
            if (span instanceof Span) {
                ((Span) span).setType(type, null, null);
            }
        }
    }

    public static class DoCreateSpanInstrumentation extends LegacySpanInstrumentation {
        public DoCreateSpanInstrumentation() {
            super(named("doCreateSpan"));
        }

        @AssignTo.Return
        @VisibleForAdvice
        @Advice.OnMethodExit(inline = false)
        public static Span doCreateSpan(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) AbstractSpan<?> span) {
            return span.createSpan();
        }
    }

    public static class EndInstrumentation extends LegacySpanInstrumentation {
        public EndInstrumentation() {
            super(named("end"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter
        public static void end(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) AbstractSpan<?> span) {
            span.end();
        }
    }


    public static class CaptureExceptionInstrumentation extends LegacySpanInstrumentation {
        public CaptureExceptionInstrumentation() {
            super(named("captureException").and(takesArguments(Throwable.class)));
        }

        @VisibleForAdvice
        @Advice.OnMethodExit(inline = false)
        public static void doCreateSpan(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) AbstractSpan<?> span,
                                        @Advice.Argument(0) Throwable t) {
            span.captureException(t);
        }
    }

    public static class GetIdInstrumentation extends LegacySpanInstrumentation {
        public GetIdInstrumentation() {
            super(named("getId").and(takesArguments(0)));
        }

        @Nullable
        @AssignTo.Return
        @VisibleForAdvice
        @Advice.OnMethodExit(inline = false)
        public static String getId(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) AbstractSpan<?> span) {
            if (tracer == null) {
                return null;
            }
            return span.getTraceContext().getId().toString();
        }
    }

    public static class GetTraceIdInstrumentation extends LegacySpanInstrumentation {
        public GetTraceIdInstrumentation() {
            super(named("getTraceId").and(takesArguments(0)));
        }

        @Nullable
        @AssignTo.Return
        @VisibleForAdvice
        @Advice.OnMethodExit(inline = false)
        public static String getTraceId(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) AbstractSpan<?> span) {
            if (tracer == null) {
                return null;
            }
            return span.getTraceContext().getTraceId().toString();
        }
    }

    public static class AddTagInstrumentation extends LegacySpanInstrumentation {
        public AddTagInstrumentation() {
            super(named("addTag"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter
        public static void addTag(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) AbstractSpan<?> span,
                                  @Advice.Argument(0) String key, @Advice.Argument(1) String value) {
            span.addLabel(key, value);
        }
    }

    public static class ActivateInstrumentation extends LegacySpanInstrumentation {
        public ActivateInstrumentation() {
            super(named("activate"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter
        public static void addTag(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) AbstractSpan<?> span) {
            span.activate();
        }
    }

    public static class IsSampledInstrumentation extends LegacySpanInstrumentation {
        public IsSampledInstrumentation() {
            super(named("isSampled"));
        }

        @AssignTo.Return
        @VisibleForAdvice
        @Advice.OnMethodExit(inline = false)
        public static boolean addTag(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) AbstractSpan<?> span) {
            return span.isSampled();
        }
    }
}
