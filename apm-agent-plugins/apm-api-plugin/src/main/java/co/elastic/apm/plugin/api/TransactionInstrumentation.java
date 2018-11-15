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
package co.elastic.apm.plugin.api;

import co.elastic.apm.bci.VisibleForAdvice;
import co.elastic.apm.impl.transaction.TraceContext;
import co.elastic.apm.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Injects the actual implementation of the public API class co.elastic.apm.api.TransactionImpl.
 */
public class TransactionInstrumentation extends ApiInstrumentation {

    private final ElementMatcher<? super MethodDescription> methodMatcher;

    public TransactionInstrumentation(ElementMatcher<? super MethodDescription> methodMatcher) {
        this.methodMatcher = methodMatcher;
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.apm.api.TransactionImpl");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return methodMatcher;
    }

    public static class SetUserInstrumentation extends TransactionInstrumentation {
        public SetUserInstrumentation() {
            super(named("setUser"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter
        public static void setUser(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Transaction transaction,
                                   @Advice.Argument(0) String id, @Advice.Argument(1) String email, @Advice.Argument(2) String username) {
            transaction.setUser(id, email, username);
        }
    }

    public static class EnsureParentIdInstrumentation extends TransactionInstrumentation {
        public EnsureParentIdInstrumentation() {
            super(named("ensureParentId"));
        }

        @VisibleForAdvice
        @Advice.OnMethodExit
        public static void ensureParentId(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Transaction transaction,
                                          @Advice.Return(readOnly = false) String spanId) {
            if (tracer != null) {
                final TraceContext traceContext = transaction.getTraceContext();
                if (traceContext.getParentId().isEmpty()) {
                    traceContext.getParentId().setToRandomValue();
                }
                spanId = traceContext.getParentId().toString();
            }
        }
    }

    public static class SetResultInstrumentation extends TransactionInstrumentation {
        public SetResultInstrumentation() {
            super(named("setResult"));
        }

        @Advice.OnMethodEnter()
        private static void ensureParentId(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Transaction transaction,
                                           @Advice.Argument(0) String result) {
            transaction.withResult(result);
        }
    }
}
