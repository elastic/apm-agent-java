/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
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
package co.elastic.apm.opentracing.impl;

import co.elastic.apm.bci.ElasticApmInstrumentation;
import co.elastic.apm.bci.VisibleForAdvice;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static co.elastic.apm.opentracing.impl.ApmSpanInstrumentation.OPENTRACING_INSTRUMENTATION_GROUP;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class ScopeManagerInstrumentation extends ElasticApmInstrumentation {

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
    public boolean includeWhenInstrumentationIsDisabled() {
        return true;
    }

    @Override
    public String getInstrumentationGroupName() {
        return OPENTRACING_INSTRUMENTATION_GROUP;
    }

    public static class ActivateInstrumentation extends ScopeManagerInstrumentation {

        public ActivateInstrumentation() {
            super(named("doActivate"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter(inline = false)
        public static void doActivate(@Advice.Argument(value = 0, typing = Assigner.Typing.DYNAMIC) @Nullable Transaction transaction,
                                      @Advice.Argument(value = 1, typing = Assigner.Typing.DYNAMIC) @Nullable Span span) {
            if (tracer != null) {
                if (transaction != null) {
                    tracer.activate(transaction);
                } else if (span != null) {
                    tracer.activate(span);
                }
            }
        }
    }

    public static class CurrentTransactionInstrumentation extends ScopeManagerInstrumentation {

        public CurrentTransactionInstrumentation() {
            super(named("getCurrentTransaction"));
        }

        @VisibleForAdvice
        @Advice.OnMethodExit
        public static void getCurrentTransaction(@Advice.Return(readOnly = false) Object transaction) {
            if (tracer != null) {
                transaction = tracer.currentTransaction();
            }
        }
    }

    public static class CurrentSpanInstrumentation extends ScopeManagerInstrumentation {

        public CurrentSpanInstrumentation() {
            super(named("getCurrentSpan"));
        }

        @VisibleForAdvice
        @Advice.OnMethodExit
        public static void getCurrentSpan(@Advice.Return(readOnly = false) Object span) {
            if (tracer != null) {
                span = tracer.currentSpan();
            }
        }

    }
}
