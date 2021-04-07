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
package co.elastic.apm.agent.dubbo.advice;

import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.dubbo.helper.AlibabaDubboAttachmentHelper;
import co.elastic.apm.agent.dubbo.helper.DubboTraceHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.protocol.dubbo.FutureAdapter;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;

@VisibleForAdvice
public class AlibabaMonitorFilterAdvice {

    @Nullable
    @VisibleForAdvice
    public static ElasticApmTracer tracer;

    @VisibleForAdvice
    public static HelperClassManager<AlibabaDubboAttachmentHelper> helperManager;

    public static void init(ElasticApmTracer tracer) {
        AlibabaMonitorFilterAdvice.tracer = tracer;
        helperManager = HelperClassManager.ForAnyClassLoader.of(tracer,
            "co.elastic.apm.agent.dubbo.helper.AlibabaDubboAttachmentHelperImpl");
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnterFilterInvoke(@Advice.Argument(1) Invocation invocation,
                                           @Advice.Local("span") Span span,
                                           @Advice.Local("apiClazz") Class<?> apiClazz,
                                           @Advice.Local("transaction") Transaction transaction) {
        RpcContext context = RpcContext.getContext();
        AlibabaDubboAttachmentHelper helper = helperManager.getForClassLoaderOfClass(Invocation.class);
        if (helper == null || tracer == null) {
            return;
        }
        // for consumer side, just create span, more information will be collected in provider side
        AbstractSpan<?> active = tracer.getActive();
        if (context.isConsumerSide() && active != null) {
            span = DubboTraceHelper.createConsumerSpan(tracer, invocation.getInvoker().getInterface(),
                invocation.getMethodName(), context.getRemoteAddress());
            if (span != null) {
                span.propagateTraceContext(invocation, helper);
            }
        } else if (active == null) {
            // for provider side
            transaction = tracer.startChildTransaction(invocation, helper, Invocation.class.getClassLoader());
            if (transaction != null) {
                transaction.activate();
                DubboTraceHelper.fillTransaction(transaction, invocation.getInvoker().getInterface(), invocation.getMethodName());
            }
        }

    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExitFilterInvoke(@Advice.Argument(1) Invocation invocation,
                                          @Advice.Return @Nullable Result result,
                                          @Advice.Local("span") @Nullable Span span,
                                          @Advice.Thrown @Nullable Throwable t,
                                          @Advice.Local("transaction") @Nullable Transaction transaction) {
        AbstractSpan<?> actualSpan = span != null ? span : transaction;
        if (actualSpan == null) {
            return;
        }

        Throwable resultException = null;
        if (result != null) { // will be null in case of thrown exception
            resultException = result.getException();
        }
        actualSpan
            .captureException(t)
            .captureException(resultException)
            .withOutcome(t != null || resultException != null ? Outcome.FAILURE : Outcome.SUCCESS)
            .deactivate();

        if (!(RpcContext.getContext().getFuture() instanceof FutureAdapter)) {
            actualSpan.end();
        }
        // else: end when ResponseCallback is called (see AlibabaResponseCallbackInstrumentation)
    }
}
