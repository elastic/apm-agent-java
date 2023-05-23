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
package co.elastic.apm.agent.jsf;

import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Transaction;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static co.elastic.apm.agent.tracer.AbstractSpan.PRIORITY_HIGH_LEVEL_FRAMEWORK;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public abstract class AbstractJsfLifecycleExecuteInstrumentation extends AbstractJsfLifecycleInstrumentation {
    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("execute")
            .and(takesArguments(1))
            .and(takesArgument(0, named(facesContextClassName())));
    }

    abstract String facesContextClassName();

    static class BaseExecuteAdvice {
        private static final String SPAN_ACTION = "execute";

        @Nullable
        protected static Object createAndActivateSpan(boolean withExternalContext, @Nullable String requestServletPath, @Nullable String requestPathInfo) {
            final AbstractSpan<?> parent = tracer.getActive();
            if (parent == null) {
                return null;
            }
            if (parent instanceof Span<?>) {
                Span<?> parentSpan = (Span<?>) parent;
                if (SPAN_SUBTYPE.equals(parentSpan.getSubtype()) && SPAN_ACTION.equals(parentSpan.getAction())) {
                    return null;
                }
            }
            Transaction<?> transaction = tracer.currentTransaction();
            if (transaction != null) {
                try {
                    if (withExternalContext) {
                        transaction.withName(requestServletPath, PRIORITY_HIGH_LEVEL_FRAMEWORK);
                        if (requestPathInfo != null) {
                            transaction.appendToName(requestPathInfo, PRIORITY_HIGH_LEVEL_FRAMEWORK);
                        }
                    }
                    transaction.setFrameworkName(FRAMEWORK_NAME);
                } catch (Exception e) {
                    // do nothing- rely on the default servlet name logic
                }
            }
            Span<?> span = parent.createSpan()
                .withType(SPAN_TYPE)
                .withSubtype(SPAN_SUBTYPE)
                .withAction(SPAN_ACTION)
                .withName("JSF Execute");
            span.activate();
            return span;
        }

        protected static void endAndDeactivateSpan(@Nullable Object span, @Nullable Throwable t) {
            if (span instanceof Span<?>) {
                ((Span<?>) span).captureException(t).deactivate().end();
            }
        }
    }
}
