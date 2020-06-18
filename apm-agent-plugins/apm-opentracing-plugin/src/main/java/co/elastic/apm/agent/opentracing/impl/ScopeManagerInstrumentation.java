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
package co.elastic.apm.agent.opentracing.impl;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.bci.bytebuddy.postprocessor.AssignTo;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class ScopeManagerInstrumentation extends OpenTracingBridgeInstrumentation {

    private final ElementMatcher<? super MethodDescription> methodMatcher;

    public ScopeManagerInstrumentation(ElementMatcher<? super MethodDescription> methodMatcher) {
        this.methodMatcher = methodMatcher;
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.apm.opentracing.ApmScopeManager");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return methodMatcher;
    }

    public static class ActivateInstrumentation extends ScopeManagerInstrumentation {

        public ActivateInstrumentation() {
            super(named("doActivate"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void doActivate(@Advice.Argument(value = 0, typing = Assigner.Typing.DYNAMIC) @Nullable AbstractSpan<?> span) {
            if (span != null) {
                span.activate();
            }
        }
    }

    public static class CurrentSpanInstrumentation extends ScopeManagerInstrumentation {

        public CurrentSpanInstrumentation() {
            super(named("getCurrentSpan"));
        }

        @Nullable
        @AssignTo.Return
        @VisibleForAdvice
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static Object getCurrentSpan() {
            if (tracer == null) {
                return null;
            }
            return tracer.getActive();
        }

    }

    public static class CurrentTraceContextInstrumentation extends ScopeManagerInstrumentation {

        public CurrentTraceContextInstrumentation() {
            super(named("getCurrentTraceContext"));
        }

        @Nullable
        @AssignTo.Return
        @VisibleForAdvice
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static Object getCurrentTraceContext() {
            if (tracer == null) {
                return null;
            }
            return tracer.getActive();
        }

    }
}
