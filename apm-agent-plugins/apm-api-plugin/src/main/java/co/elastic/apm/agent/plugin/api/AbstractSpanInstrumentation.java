/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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
package co.elastic.apm.agent.plugin.api;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.util.HashMap;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.named;
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

        @VisibleForAdvice
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void setName(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) AbstractSpan<?> span,
                                   @Advice.Argument(0) String name) {
            span.setName(name);
        }
    }

    public static class SetTypeInstrumentation extends AbstractSpanInstrumentation {
        public SetTypeInstrumentation() {
            super(named("doSetType"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void setType(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) AbstractSpan<?> span,
                                   @Advice.Argument(0) String type) {
            span.withType(type);
        }
    }

    public static class DoCreateSpanInstrumentation extends AbstractSpanInstrumentation {
        public DoCreateSpanInstrumentation() {
            super(named("doCreateSpan"));
        }

        @VisibleForAdvice
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void doCreateSpan(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) AbstractSpan<?> span,
                                        @Advice.Return(readOnly = false) Object result) {
            result = span.createSpan();
        }
    }

    public static class EndInstrumentation extends AbstractSpanInstrumentation {
        public EndInstrumentation() {
            super(named("end"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void end(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) AbstractSpan<?> span) {
            span.end();
        }
    }


    public static class CaptureExceptionInstrumentation extends AbstractSpanInstrumentation {
        public CaptureExceptionInstrumentation() {
            super(named("captureException").and(takesArguments(Throwable.class)));
        }

        @VisibleForAdvice
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void doCreateSpan(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) AbstractSpan<?> span,
                                        @Advice.Argument(0) Throwable t) {
            span.captureException(t);
        }
    }

    public static class GetIdInstrumentation extends AbstractSpanInstrumentation {
        public GetIdInstrumentation() {
            super(named("getId").and(takesArguments(0)));
        }

        @VisibleForAdvice
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void getId(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) AbstractSpan<?> span,
                                 @Advice.Return(readOnly = false) String id) {
            if (tracer != null) {
                id = span.getTraceContext().getId().toString();
            }
        }
    }

    public static class GetTraceIdInstrumentation extends AbstractSpanInstrumentation {
        public GetTraceIdInstrumentation() {
            super(named("getTraceId").and(takesArguments(0)));
        }

        @VisibleForAdvice
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void getTraceId(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) AbstractSpan<?> span,
                                      @Advice.Return(readOnly = false) String traceId) {
            if (tracer != null) {
                traceId = span.getTraceContext().getTraceId().toString();
            }
        }
    }

    public static class AddTagInstrumentation extends AbstractSpanInstrumentation {
        public AddTagInstrumentation() {
            super(named("doAddTag"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void addTag(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) AbstractSpan<?> span,
                                  @Advice.Argument(0) String key, @Advice.Argument(1) String value) {
            span.addTag(key, value);
        }
    }

    public static class ActivateInstrumentation extends AbstractSpanInstrumentation {
        public ActivateInstrumentation() {
            super(named("activate"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void addTag(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) AbstractSpan<?> span) {
            span.activate();
        }
    }

    public static class IsSampledInstrumentation extends AbstractSpanInstrumentation {
        public IsSampledInstrumentation() {
            super(named("isSampled"));
        }

        @VisibleForAdvice
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void addTag(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) AbstractSpan<?> span,
                                  @Advice.Return(readOnly = false) boolean sampled) {
            sampled = span.isSampled();
        }
    }

    public static class GetTraceHeadersInstrumentation extends AbstractSpanInstrumentation {
        public GetTraceHeadersInstrumentation() {
            super(named("getTraceHeaders"));
        }

        @VisibleForAdvice
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void addTraceHeaders(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) AbstractSpan<?> span,
                                           @Advice.Return(readOnly = false) Map<? super String, ? super String> headers) {
            headers = new HashMap<>();
            headers.put(TraceContext.TRACE_PARENT_HEADER, span.getTraceContext().getOutgoingTraceParentHeader().toString());
        }
    }

    public static class InjectTraceHeadersInstrumentation extends AbstractSpanInstrumentation {

        public InjectTraceHeadersInstrumentation() {
            super(named("doInjectTraceHeaders"));
        }

        @VisibleForAdvice
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void addTraceHeaders(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) AbstractSpan<?> span,
                                           @Advice.Argument(0) MethodHandle addHeader,
                                           @Advice.Argument(1) @Nullable Object headerInjector) throws Throwable {
            if (headerInjector != null) {
                addHeader.invoke(headerInjector, TraceContext.TRACE_PARENT_HEADER, span.getTraceContext().getOutgoingTraceParentHeader().toString());
            }
        }
    }
}
