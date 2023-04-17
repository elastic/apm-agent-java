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

import co.elastic.apm.agent.tracer.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static co.elastic.apm.agent.tracer.AbstractSpan.PRIORITY_USER_SUPPLIED;
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
        return named("co.elastic.apm.api.SpanImpl")
            .and(not(hasSuperType(named("co.elastic.apm.api.AbstractSpanImpl"))));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return methodMatcher;
    }

    public static class SetNameInstrumentation extends LegacySpanInstrumentation {
        public SetNameInstrumentation() {
            super(named("setName"));
        }

        public static class AdviceClass {
            @Advice.OnMethodEnter(inline = false)
            public static void setName(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object span,
                                       @Advice.Argument(0) String name) {
                if (span instanceof Span<?>) {
                    ((Span<?>) span).withName(name, PRIORITY_USER_SUPPLIED);
                }
            }
        }
    }

    public static class SetTypeInstrumentation extends LegacySpanInstrumentation {
        public SetTypeInstrumentation() {
            super(named("setType"));
        }
        public static class AdviceClass {

            @Advice.OnMethodEnter(inline = false)
            public static void setType(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object span,
                                       @Advice.Argument(0) String type) {
                if (span instanceof co.elastic.apm.agent.impl.transaction.Span) {
                    ((co.elastic.apm.agent.impl.transaction.Span) span).setType(type, null, null);
                }
            }
        }
    }

    public static class DoCreateSpanInstrumentation extends LegacySpanInstrumentation {
        public DoCreateSpanInstrumentation() {
            super(named("doCreateSpan"));
        }

        public static class AdviceClass {
            @Nullable
            @Advice.AssignReturned.ToReturned
            @Advice.OnMethodExit(inline = false)
            public static Object doCreateSpan(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object span) {
                if (span instanceof Span<?>) {
                    return ((Span<?>) span).createSpan();
                } else {
                    return null;
                }
            }
        }
    }

    public static class EndInstrumentation extends LegacySpanInstrumentation {
        public EndInstrumentation() {
            super(named("end"));
        }

        public static class AdviceClass {
            @Advice.OnMethodEnter(inline = false)
            public static void end(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object span) {
                if (span instanceof Span<?>) {
                    ((Span<?>) span).end();
                }
            }
        }
    }

    public static class CaptureExceptionInstrumentation extends LegacySpanInstrumentation {
        public CaptureExceptionInstrumentation() {
            super(named("captureException").and(takesArguments(Throwable.class)));
        }

        public static class AdviceClass {
            @Advice.OnMethodExit(inline = false)
            public static void captureException(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object span,
                                                @Advice.Argument(0) Throwable t) {
                if (span instanceof Span<?>) {
                    ((Span<?>) span).captureException(t);
                }
            }
        }
    }

    public static class GetIdInstrumentation extends LegacySpanInstrumentation {
        public GetIdInstrumentation() {
            super(named("getId").and(takesArguments(0)));
        }

        public static class AdviceClass {
            @Nullable
            @Advice.AssignReturned.ToReturned
            @Advice.OnMethodExit(inline = false)
            public static String getId(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object span,
                                       @Advice.Return @Nullable String returnValue) {
                if (span instanceof Span<?>) {
                    return ((Span<?>) span).getTraceContext().getId().toString();
                } else {
                    return returnValue;
                }
            }
        }
    }

    public static class GetTraceIdInstrumentation extends LegacySpanInstrumentation {
        public GetTraceIdInstrumentation() {
            super(named("getTraceId").and(takesArguments(0)));
        }

        public static class AdviceClass {
            @Nullable
            @Advice.AssignReturned.ToReturned
            @Advice.OnMethodExit(inline = false)
            public static String getTraceId(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object span,
                                            @Advice.Return @Nullable String returnValue) {
                if (span instanceof Span<?>) {
                    return ((Span<?>) span).getTraceContext().getTraceId().toString();
                } else {
                    return returnValue;
                }
            }
        }
    }

    public static class AddTagInstrumentation extends LegacySpanInstrumentation {
        public AddTagInstrumentation() {
            super(named("addTag"));
        }

        public static class AdviceClass {
            @Advice.OnMethodEnter(inline = false)
            public static void addTag(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object span,
                                      @Advice.Argument(0) String key,
                                      @Advice.Argument(1) String value) {
                if (span instanceof co.elastic.apm.agent.impl.transaction.Span) {
                    ((co.elastic.apm.agent.impl.transaction.Span) span).addLabel(key, value);
                }
            }
        }
    }

    public static class ActivateInstrumentation extends LegacySpanInstrumentation {
        public ActivateInstrumentation() {
            super(named("activate"));
        }

        public static class AdviceClass {
            @Advice.OnMethodEnter(inline = false)
            public static void activate(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object span) {
                if (span instanceof Span<?>) {
                    ((Span<?>) span).activate();
                }
            }
        }
    }

    public static class IsSampledInstrumentation extends LegacySpanInstrumentation {
        public IsSampledInstrumentation() {
            super(named("isSampled"));
        }

        public static class AdviceClass {
            @Advice.AssignReturned.ToReturned
            @Advice.OnMethodExit(inline = false)
            public static boolean isSampled(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object span,
                                            @Advice.Return boolean returnValue) {
                if (span instanceof Span<?>) {
                    return ((Span<?>) span).isSampled();
                } else {
                    return returnValue;
                }
            }
        }
    }
}
