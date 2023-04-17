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
package co.elastic.apm.agent.struts;

import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.util.TransactionNameUtils;
import com.opensymphony.xwork2.ActionContext;
import com.opensymphony.xwork2.ActionProxy;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;

import static co.elastic.apm.agent.tracer.AbstractSpan.PRIORITY_HIGH_LEVEL_FRAMEWORK;

public class ActionProxyAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object onEnterExecute(@Advice.This ActionProxy actionProxy) {
        Transaction<?> transaction = GlobalTracer.get().currentTransaction();
        if (transaction == null) {
            return null;
        }

        String className = actionProxy.getAction().getClass().getSimpleName();
        String methodName = actionProxy.getMethod();
        if (ActionContext.getContext().get("CHAIN_HISTORY") != null) {
            Span<?> span = transaction.createSpan().withType("app").withSubtype("internal");
            TransactionNameUtils.setNameFromClassAndMethod(className, methodName, span.getAndOverrideName(PRIORITY_HIGH_LEVEL_FRAMEWORK));
            return span.activate();
        } else {
            TransactionNameUtils.setNameFromClassAndMethod(className, methodName, transaction.getAndOverrideName(PRIORITY_HIGH_LEVEL_FRAMEWORK));
            StrutsFrameworkUtils.setFrameworkNameAndVersion(transaction);
            return null;
        }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExitExecute(@Advice.Enter @Nullable Object spanOrNull,
                                     @Advice.Thrown @Nullable Throwable t) {
        if (spanOrNull == null) {
            return;
        }

        Span<?> span = (Span<?>) spanOrNull;
        try {
            if (t != null) {
                span.captureException(t).withOutcome(Outcome.FAILURE);
            } else {
                span.withOutcome(Outcome.SUCCESS);
            }
        } finally {
            span.deactivate().end();
        }
    }
}
