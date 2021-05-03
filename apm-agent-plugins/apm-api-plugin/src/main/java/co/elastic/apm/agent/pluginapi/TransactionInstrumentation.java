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

import co.elastic.apm.agent.impl.transaction.Id;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Injects the actual implementation of the public API class {@code co.elastic.apm.api.TransactionImpl}.
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

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void setUser(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object transaction,
                                   @Advice.Argument(0) String id, @Advice.Argument(1) String email, @Advice.Argument(2) String username, @Advice.Argument(value = 3, optional = true) String domain) {
            if (transaction instanceof Transaction) {
                ((Transaction) transaction).setUser(id, email, username, domain);
            }
        }
    }

    public static class EnsureParentIdInstrumentation extends TransactionInstrumentation {
        public EnsureParentIdInstrumentation() {
            super(named("ensureParentId"));
        }

        @Nullable
        @AssignTo.Return
        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static String ensureParentId(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object transaction,
                                            @Advice.Return @Nullable String returnValue) {
            if (transaction instanceof Transaction) {
                final TraceContext traceContext = ((Transaction) transaction).getTraceContext();
                Id parentId = traceContext.getParentId();
                if (parentId.isEmpty()) {
                    parentId.setToRandomValue();
                }
                return parentId.toString();
            }
            return returnValue;
        }
    }

    public static class SetResultInstrumentation extends TransactionInstrumentation {
        public SetResultInstrumentation() {
            super(named("setResult"));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void setResult(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object transaction,
                                     @Advice.Argument(0) String result) {
            if (transaction instanceof Transaction) {
                ((Transaction) transaction).withResult(result);
            }
        }
    }

    public static class AddCustomContextInstrumentation extends TransactionInstrumentation {
        public AddCustomContextInstrumentation() {
            super(named("addCustomContext"));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void addCustomContext(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object transactionObj,
                                            @Advice.Argument(0) String key,
                                            @Advice.Argument(1) @Nullable Object value) {
            if (value != null && transactionObj instanceof Transaction) {
                Transaction transaction = (Transaction) transactionObj;
                if (value instanceof String) {
                    transaction.addCustomContext(key, (String) value);
                } else if (value instanceof Number) {
                    transaction.addCustomContext(key, (Number) value);
                } else if (value instanceof Boolean) {
                    transaction.addCustomContext(key, (Boolean) value);
                }
            }
        }
    }
}
