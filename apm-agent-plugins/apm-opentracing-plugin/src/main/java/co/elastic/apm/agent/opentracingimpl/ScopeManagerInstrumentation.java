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
package co.elastic.apm.agent.opentracingimpl;

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.sdk.advice.AssignTo;
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

    @Override
    public String getAdviceClassName() {
        return getClass().getName() + "$AdviceClass";
    }

    public static class ActivateInstrumentation extends ScopeManagerInstrumentation {

        public ActivateInstrumentation() {
            super(named("doActivate"));
        }

        public static class AdviceClass {
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void doActivate(@Advice.Argument(value = 0, typing = Assigner.Typing.DYNAMIC) @Nullable Object context) {
                if (context instanceof AbstractSpan<?>) {
                    ((AbstractSpan<?>) context).activate();
                }
            }
        }
    }

    public static class CurrentSpanInstrumentation extends ScopeManagerInstrumentation {

        public CurrentSpanInstrumentation() {
            super(named("getCurrentSpan"));
        }

        public static class AdviceClass {
            @Nullable
            @AssignTo.Return
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static Object getCurrentSpan() {
                return tracer.getActive();
            }
        }
    }

    public static class CurrentTraceContextInstrumentation extends ScopeManagerInstrumentation {

        public CurrentTraceContextInstrumentation() {
            super(named("getCurrentTraceContext"));
        }

        public static class AdviceClass {
            @Nullable
            @AssignTo.Return
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static Object getCurrentTraceContext() {
                return tracer.getActive();
            }
        }
    }
}
