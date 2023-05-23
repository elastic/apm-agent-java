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
package co.elastic.apm.agent.pluginapi;

import co.elastic.apm.agent.impl.context.ServiceTarget;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;

import static co.elastic.apm.agent.tracer.AbstractSpan.PRIORITY_USER_SUPPLIED;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
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

    /**
     * Instruments {@code co.elastic.apm.api.AbstractSpanImpl#doSetName(java.lang.String)}
     */
    public static class SetNameInstrumentation extends AbstractSpanInstrumentation {
        public SetNameInstrumentation() {
            super(named("doSetName"));
        }

        public static class AdviceClass {
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void setName(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                       @Advice.Argument(0) String name) {
                if (context instanceof AbstractSpan<?>) {
                    ((AbstractSpan<?>) context).withName(name, PRIORITY_USER_SUPPLIED);
                }
            }
        }
    }

    public static class SetTypeInstrumentation extends AbstractSpanInstrumentation {
        public SetTypeInstrumentation() {
            super(named("doSetType"));
        }

        public static class AdviceClass {
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
    }

    public static class SetTypesInstrumentation extends AbstractSpanInstrumentation {
        public SetTypesInstrumentation() {
            super(named("doSetTypes"));
        }

        public static class AdviceClass {
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
    }

    public static class DoCreateSpanInstrumentation extends AbstractSpanInstrumentation {
        public DoCreateSpanInstrumentation() {
            super(named("doCreateSpan"));
        }

        public static class AdviceClass {
            @Nullable
            @Advice.AssignReturned.ToReturned
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
    }

    public static class DoCreateExitSpanInstrumentation extends AbstractSpanInstrumentation {
        public DoCreateExitSpanInstrumentation() {
            super(named("doCreateExitSpan"));
        }

        public static class AdviceClass {
            @Nullable
            @Advice.AssignReturned.ToReturned
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static Object doCreateExitSpan(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                                  @Advice.Return @Nullable Object returnValue) {
                if (context instanceof AbstractSpan<?>) {
                    return ((AbstractSpan<?>) context).createExitSpan();
                } else {
                    return returnValue;
                }
            }
        }
    }

    public static class InitializeInstrumentation extends AbstractSpanInstrumentation {
        public InitializeInstrumentation() {
            super(named("initialize").and(takesArgument(0, Object.class)));
        }

        public static class AdviceClass {
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void incrementReferences(@Advice.Argument(0) Object span) {
                ((AbstractSpan<?>) span).incrementReferences();
            }
        }
    }

    public static class SetStartTimestampInstrumentation extends AbstractSpanInstrumentation {
        public SetStartTimestampInstrumentation() {
            super(named("doSetStartTimestamp").and(takesArguments(long.class)));
        }

        public static class AdviceClass {
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void setStartTimestamp(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                                 @Advice.Argument(value = 0) long epochMicros) {
                if (context instanceof AbstractSpan<?>) {
                    ((AbstractSpan<?>) context).setStartTimestamp(epochMicros);
                }
            }
        }
    }

    /**
     * Instruments {@code co.elastic.apm.api.AbstractSpanImpl#doSetOutcome(java.lang.Boolean)}
     */
    public static class SetOutcomeInstrumentation extends AbstractSpanInstrumentation {
        public SetOutcomeInstrumentation() {
            super(named("doSetOutcome")
                .and(takesArguments(1)
                    .and(takesArgument(0, named("co.elastic.apm.api.Outcome")))
                ));
        }

        public static class AdviceClass {
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void setOutcome(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                          @Advice.Argument(value = 0) @Nullable Enum<?> apiOutcome) {
                if (context instanceof AbstractSpan<?>) {
                    Outcome outcome = Outcome.UNKNOWN;
                    if (apiOutcome != null) {
                        // valueOf conversion is fast as Enum implementation is using a lookup map internally
                        // thus we don't need to do this ourselves
                        outcome = Outcome.valueOf(apiOutcome.name());
                    }
                    ((AbstractSpan<?>) context).withUserOutcome(outcome);
                }
            }
        }
    }

    public static class EndInstrumentation extends AbstractSpanInstrumentation {
        public EndInstrumentation() {
            super(named("end").and(takesArguments(0)));
        }

        public static class AdviceClass {
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void end(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context) {
                if (context instanceof AbstractSpan<?>) {
                    ((AbstractSpan<?>) context).end();
                }
            }
        }
    }

    public static class EndWithTimestampInstrumentation extends AbstractSpanInstrumentation {
        public EndWithTimestampInstrumentation() {
            super(named("end").and(takesArguments(long.class)));
        }

        public static class AdviceClass {
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void end(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                   @Advice.Argument(value = 0) long epochMicros) {
                if (context instanceof AbstractSpan<?>) {
                    ((AbstractSpan<?>) context).end(epochMicros);
                }
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

        public static class AdviceClass {
            @Nullable
            @Advice.AssignReturned.ToReturned
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static String captureException(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                                  @Advice.Argument(0) Throwable t,
                                                  @Advice.Return @Nullable String returnValue) {
                if (context instanceof AbstractSpan<?>) {
                    return ((AbstractSpan<?>) context).captureExceptionAndGetErrorId(t);
                } else {
                    return returnValue;
                }
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

        public static class AdviceClass {
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static void captureException(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                                @Advice.Argument(0) Throwable t) {
                if (context instanceof AbstractSpan<?>) {
                    ((AbstractSpan<?>) context).captureException(t);
                }
            }
        }
    }

    public static class GetIdInstrumentation extends AbstractSpanInstrumentation {
        public GetIdInstrumentation() {
            super(named("getId").and(takesArguments(0)));
        }

        public static class AdviceClass {
            @Nullable
            @Advice.AssignReturned.ToReturned
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static String getId(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                       @Advice.Return @Nullable String returnValue) {
                if (context instanceof AbstractSpan<?>) {
                    return ((AbstractSpan<?>) context).getTraceContext().getId().toString();
                } else {
                    return returnValue;
                }
            }
        }
    }

    public static class GetTraceIdInstrumentation extends AbstractSpanInstrumentation {
        public GetTraceIdInstrumentation() {
            super(named("getTraceId").and(takesArguments(0)));
        }

        public static class AdviceClass {
            @Nullable
            @Advice.AssignReturned.ToReturned
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static String getTraceId(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                            @Advice.Return @Nullable String returnValue) {
                if (context instanceof AbstractSpan<?>) {
                    return ((AbstractSpan<?>) context).getTraceContext().getTraceId().toString();
                } else {
                    return returnValue;
                }
            }
        }
    }

    public static class AddStringLabelInstrumentation extends AbstractSpanInstrumentation {
        public AddStringLabelInstrumentation() {
            super(named("doAddTag").or(named("doAddStringLabel")));
        }

        public static class AdviceClass {
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void addLabel(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                        @Advice.Argument(0) String key,
                                        @Advice.Argument(1) @Nullable String value) {
                if (value != null && context instanceof AbstractSpan) {
                    ((AbstractSpan<?>) context).addLabel(key, value);
                }
            }
        }
    }

    public static class AddNumberLabelInstrumentation extends AbstractSpanInstrumentation {
        public AddNumberLabelInstrumentation() {
            super(named("doAddNumberLabel"));
        }

        public static class AdviceClass {
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void addLabel(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                        @Advice.Argument(0) String key,
                                        @Advice.Argument(1) @Nullable Number value) {
                if (value != null && context instanceof AbstractSpan) {
                    ((AbstractSpan<?>) context).addLabel(key, value);
                }
            }
        }
    }

    public static class AddBooleanLabelInstrumentation extends AbstractSpanInstrumentation {
        public AddBooleanLabelInstrumentation() {
            super(named("doAddBooleanLabel"));
        }

        public static class AdviceClass {
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void addLabel(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                        @Advice.Argument(0) String key,
                                        @Advice.Argument(1) @Nullable Boolean value) {
                if (value != null && context instanceof AbstractSpan) {
                    ((AbstractSpan<?>) context).addLabel(key, value);
                }
            }
        }
    }

    public static class ActivateInstrumentation extends AbstractSpanInstrumentation {
        public ActivateInstrumentation() {
            super(named("activate"));
        }

        public static class AdviceClass {
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void activate(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context) {
                if (context instanceof AbstractSpan<?>) {
                    ((AbstractSpan<?>) context).activate();
                }
            }
        }
    }

    public static class IsSampledInstrumentation extends AbstractSpanInstrumentation {
        public IsSampledInstrumentation() {
            super(named("isSampled"));
        }

        public static class AdviceClass {
            @Advice.AssignReturned.ToReturned
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
    }

    public static class InjectTraceHeadersInstrumentation extends AbstractSpanInstrumentation {

        public InjectTraceHeadersInstrumentation() {
            super(named("doInjectTraceHeaders"));
        }

        public static class AdviceClass {
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static void injectTraceHeaders(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                                  @Advice.Argument(0) MethodHandle addHeaderMethodHandle,
                                                  @Advice.Argument(1) @Nullable Object headerInjector) {
                if (headerInjector != null && context instanceof AbstractSpan) {
                    ((AbstractSpan<?>) context).propagateTraceContext(headerInjector, HeaderInjectorBridge.get(addHeaderMethodHandle));
                }
            }
        }
    }

    public static class SetDestinationAddressInstrumentation extends AbstractSpanInstrumentation {

        public SetDestinationAddressInstrumentation() {
            super(named("doSetDestinationAddress"));
        }

        public static class AdviceClass {
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static void setDestinationAddress(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                                     @Advice.Argument(0) @Nullable String address,
                                                     @Advice.Argument(1) int port) {
                if (context instanceof Span) {
                    ((Span) context).getContext().getDestination()
                        .withUserAddress(address)
                        .withUserPort(port);
                }
            }
        }
    }

    public static class SetDestinationServiceInstrumentation extends AbstractSpanInstrumentation {

        public SetDestinationServiceInstrumentation() {
            super(named("doSetDestinationService"));
        }

        public static class AdviceClass {
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static void setDestinationService(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                                     @Advice.Argument(0) @Nullable String resource) {
                if (context instanceof Span) {
                    ServiceTarget serviceTarget = ((Span) context).getContext().getServiceTarget();
                    if (resource == null || resource.isEmpty()) {
                        serviceTarget.withUserType(null).withUserName(null);
                    } else {
                        String currentType = serviceTarget.getType();
                        serviceTarget
                            .withUserType(currentType != null ? currentType : "")
                            .withUserName(resource)
                            .withNameOnlyDestinationResource();
                    }

                }
            }
        }
    }

    public static class SetServiceTargetInstrumentation extends AbstractSpanInstrumentation {

        public SetServiceTargetInstrumentation() {
            super(named("doSetServiceTarget"));
        }

        public static class AdviceClass {
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static void setServiceTarget(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                                @Advice.Argument(0) @Nullable String type,
                                                @Advice.Argument(1) @Nullable String name) {
                if (context instanceof Span) {
                    ((Span) context).getContext().getServiceTarget()
                        .withUserType(type)
                        .withUserName(name);
                }
            }
        }
    }

    public static class SetNonDiscardableInstrumentation extends AbstractSpanInstrumentation {

        public SetNonDiscardableInstrumentation() {
            super(named("doSetNonDiscardable"));
        }

        public static class AdviceClass {
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static void setNonDiscardable(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context) {
                if (context instanceof AbstractSpan<?>) {
                    ((AbstractSpan<?>) context).setNonDiscardable();
                }
            }
        }
    }
}
