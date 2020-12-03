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
package co.elastic.apm.agent.pluginapi;

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;

import static co.elastic.apm.agent.impl.transaction.AbstractSpan.PRIO_USER_SUPPLIED;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Injects the actual implementation of the public API class co.elastic.apm.api.SpanImpl.
 */
public class AbstractSpanInstrumentation extends ApiInstrumentation {

    private final ElementMatcher<? super MethodDescription> methodMatcher;

    public AbstractSpanInstrumentation(ElementMatcher<? super MethodDescription> methodMatcher) {
        this.methodMatcher = methodMatcher;
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.apm.api.AbstractSpanImpl");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return methodMatcher;
    }

    public static class SetNameInstrumentation extends AbstractSpanInstrumentation {
        public SetNameInstrumentation() {
            super(named("doSetName"));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void setName(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                   @Advice.Argument(0) String name) {
            if (context instanceof AbstractSpan<?>) {
                ((AbstractSpan<?>) context).withName(name, PRIO_USER_SUPPLIED);
            }
        }
    }

    public static class SetTypeInstrumentation extends AbstractSpanInstrumentation {
        public SetTypeInstrumentation() {
            super(named("doSetType"));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void setType(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                   @Advice.Argument(0) String type) {
            if (context instanceof Transaction) {
                ((Transaction) context).withType(type);
            } else if (context instanceof Span) {
                ((Span) context).setType(type, null, null);
            }
        }
    }

    public static class SetTypesInstrumentation extends AbstractSpanInstrumentation {
        public SetTypesInstrumentation() {
            super(named("doSetTypes"));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void setType(@Advice.Argument(0) Object span,
                                   @Advice.Argument(1) @Nullable String type,
                                   @Advice.Argument(2) @Nullable String subtype,
                                   @Advice.Argument(3) @Nullable String action) {
            if (span instanceof Span) {
                ((Span) span).setType(type, subtype, action);
            }
        }
    }

    public static class DoCreateSpanInstrumentation extends AbstractSpanInstrumentation {
        public DoCreateSpanInstrumentation() {
            super(named("doCreateSpan"));
        }

        @Nullable
        @AssignTo.Return
        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static Object doCreateSpan(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                          @Advice.Return @Nullable Object returnValue) {
            if (context instanceof AbstractSpan<?>) {
                return ((AbstractSpan<?>) context).createSpan();
            } else {
                return returnValue;
            }
        }
    }

    public static class SetStartTimestampInstrumentation extends AbstractSpanInstrumentation {
        public SetStartTimestampInstrumentation() {
            super(named("doSetStartTimestamp").and(takesArguments(long.class)));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void setStartTimestamp(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                             @Advice.Argument(value = 0) long epochMicros) {
            if (context instanceof AbstractSpan<?>) {
                ((AbstractSpan<?>) context).setStartTimestamp(epochMicros);
            }
        }
    }

    public static class EndInstrumentation extends AbstractSpanInstrumentation {
        public EndInstrumentation() {
            super(named("end").and(takesArguments(0)));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void end(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context) {
            if (context instanceof AbstractSpan<?>) {
                ((AbstractSpan<?>) context).end();
            }
        }
    }

    public static class EndWithTimestampInstrumentation extends AbstractSpanInstrumentation {
        public EndWithTimestampInstrumentation() {
            super(named("end").and(takesArguments(long.class)));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void end(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                               @Advice.Argument(value = 0) long epochMicros) {
            if (context instanceof AbstractSpan<?>) {
                ((AbstractSpan<?>) context).end(epochMicros);
            }
        }
    }

    /**
     * Instruments {@code co.elastic.apm.api.AbstractSpanImpl#captureException(Throwable)}
     */
    public static class CaptureExceptionInstrumentation extends AbstractSpanInstrumentation {
        public CaptureExceptionInstrumentation() {
            super(named("captureException")
                .and(takesArguments(Throwable.class))
                .and(returns(String.class)));
        }

        @Nullable
        @AssignTo.Return
        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static String captureException(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                              @Advice.Argument(0) Throwable t,
                                              @Advice.Return String returnValue) {
            if (context instanceof AbstractSpan<?>) {
                return ((AbstractSpan<?>) context).captureExceptionAndGetErrorId(t);
            } else {
                return returnValue;
            }
        }
    }

    /**
     * Instruments previous version of API where {@code co.elastic.apm.api.AbstractSpanImpl#captureException(Throwable)}
     * returns void.
     */
    public static class LegacyCaptureExceptionInstrumentation extends AbstractSpanInstrumentation {
        public LegacyCaptureExceptionInstrumentation() {
            super(named("captureException")
                .and(takesArguments(Throwable.class))
                .and(returns(Void.class)));
        }

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static void captureException(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                            @Advice.Argument(0) Throwable t) {
            if (context instanceof AbstractSpan<?>) {
                ((AbstractSpan<?>) context).captureException(t);
            }
        }
    }

    public static class GetIdInstrumentation extends AbstractSpanInstrumentation {
        public GetIdInstrumentation() {
            super(named("getId").and(takesArguments(0)));
        }

        @AssignTo.Return
        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static String getId(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                   @Advice.Return String returnValue) {
            if (context instanceof AbstractSpan<?>) {
                return ((AbstractSpan<?>) context).getTraceContext().getId().toString();
            } else {
                return returnValue;
            }
        }
    }

    public static class GetTraceIdInstrumentation extends AbstractSpanInstrumentation {
        public GetTraceIdInstrumentation() {
            super(named("getTraceId").and(takesArguments(0)));
        }

        @AssignTo.Return
        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static String getTraceId(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                        @Advice.Return String returnValue) {
            if (context instanceof AbstractSpan<?>) {
                return ((AbstractSpan<?>) context).getTraceContext().getTraceId().toString();
            } else {
                return returnValue;
            }
        }
    }

    public static class AddStringLabelInstrumentation extends AbstractSpanInstrumentation {
        public AddStringLabelInstrumentation() {
            super(named("doAddTag").or(named("doAddStringLabel")));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void addLabel(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                    @Advice.Argument(0) String key, @Nullable @Advice.Argument(1) String value) {
            if (value != null && context instanceof AbstractSpan) {
                ((AbstractSpan<?>) context).addLabel(key, value);
            }
        }
    }

    public static class AddNumberLabelInstrumentation extends AbstractSpanInstrumentation {
        public AddNumberLabelInstrumentation() {
            super(named("doAddNumberLabel"));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void addLabel(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                    @Advice.Argument(0) String key, @Nullable @Advice.Argument(1) Number value) {
            if (value != null && context instanceof AbstractSpan) {
                ((AbstractSpan<?>) context).addLabel(key, value);
            }
        }
    }

    public static class AddBooleanLabelInstrumentation extends AbstractSpanInstrumentation {
        public AddBooleanLabelInstrumentation() {
            super(named("doAddBooleanLabel"));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void addLabel(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                    @Advice.Argument(0) String key, @Nullable @Advice.Argument(1) Boolean value) {
            if (value != null && context instanceof AbstractSpan) {
                ((AbstractSpan<?>) context).addLabel(key, value);
            }
        }
    }

    public static class ActivateInstrumentation extends AbstractSpanInstrumentation {
        public ActivateInstrumentation() {
            super(named("activate"));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void activate(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context) {
            if (context instanceof AbstractSpan<?>) {
                ((AbstractSpan<?>) context).activate();
            }
        }
    }

    public static class IsSampledInstrumentation extends AbstractSpanInstrumentation {
        public IsSampledInstrumentation() {
            super(named("isSampled"));
        }

        @AssignTo.Return
        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static boolean isSampled(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                        @Advice.Return boolean returnValue) {
            if (context instanceof AbstractSpan<?>) {
                return ((AbstractSpan<?>) context).isSampled();
            } else {
                return returnValue;
            }
        }
    }

    public static class InjectTraceHeadersInstrumentation extends AbstractSpanInstrumentation {

        public InjectTraceHeadersInstrumentation() {
            super(named("doInjectTraceHeaders"));
        }

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static void injectTraceHeaders(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                              @Advice.Argument(0) MethodHandle addHeaderMethodHandle,
                                              @Advice.Argument(1) @Nullable Object headerInjector) {
            if (headerInjector != null && context instanceof AbstractSpan) {
                ((AbstractSpan<?>) context).propagateTraceContext(headerInjector, HeaderInjectorBridge.get(addHeaderMethodHandle));
            }
        }
    }

    public static class WithDestinationServiceResourceInstrumentation extends AbstractSpanInstrumentation {

        public WithDestinationServiceResourceInstrumentation() {
            super(named("doAppendDestinationServiceResource"));
        }

        @Nullable
        @AssignTo.Return
        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static Object withDestinationServiceResource(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                                            @Advice.Argument(0) @Nullable String resource,
                                                            @Advice.Return @Nullable Object returnValue) {
            if (context instanceof Span) {
                if (resource != null) {
                    ((Span) context).getContext().getDestination().getService().withResource(resource);
                }
                return context;
            } else {
                return returnValue;
            }
        }
    }

    public static class WithDestinationServiceNameInstrumentation extends AbstractSpanInstrumentation {

        public WithDestinationServiceNameInstrumentation() {
            super(named("doAppendDestinationServiceName"));
        }

        @Nullable
        @AssignTo.Return
        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static Object withDestinationServiceName(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                                        @Advice.Argument(0) @Nullable String name,
                                                        @Advice.Return @Nullable Object returnValue) {
            if (context instanceof Span) {
                if (name != null) {
                    ((Span) context).getContext().getDestination().getService().withName(name);
                }
                return context;
            } else {
                return returnValue;
            }
        }
    }

    public static class WithDestinationServiceTypeInstrumentation extends AbstractSpanInstrumentation {

        public WithDestinationServiceTypeInstrumentation() {
            super(named("doSetDestinationServiceType"));
        }

        @Nullable
        @AssignTo.Return
        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static Object doSetDestinationServiceType(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                                         @Advice.Argument(0) @Nullable String type,
                                                         @Advice.Return @Nullable Object returnValue) {
            if (context instanceof Span) {
                if (type != null) {
                    ((Span) context).getContext().getDestination().getService().withType(type);
                }
                return context;
            } else {
                return returnValue;
            }
        }
    }
}
