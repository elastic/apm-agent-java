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
package co.elastic.apm.agent.dubbo.advice;

import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.dubbo.helper.ApacheDubboAttachmentHelper;
import co.elastic.apm.agent.dubbo.helper.AsyncCallbackCreator;
import co.elastic.apm.agent.dubbo.helper.DubboTraceHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import org.apache.dubbo.rpc.AsyncRpcResult;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;

import javax.annotation.Nullable;

@VisibleForAdvice
public class ApacheMonitorFilterAdvice {

    @Nullable
    @VisibleForAdvice
    public static ElasticApmTracer tracer;

    @VisibleForAdvice
    public static HelperClassManager<ApacheDubboAttachmentHelper> attachmentHelperClassManager;

    @VisibleForAdvice
    public static HelperClassManager<AsyncCallbackCreator> asyncCallbackCreatorClassManager;

    public static void init(ElasticApmTracer tracer) {
        ApacheMonitorFilterAdvice.tracer = tracer;
        attachmentHelperClassManager = HelperClassManager.ForAnyClassLoader.of(tracer,
            "co.elastic.apm.agent.dubbo.helper.ApacheDubboAttachmentHelperImpl");
        asyncCallbackCreatorClassManager = HelperClassManager.ForAnyClassLoader.of(tracer,
            "co.elastic.apm.agent.dubbo.helper.AsyncCallbackCreatorImpl",
            "co.elastic.apm.agent.dubbo.helper.AsyncCallbackCreatorImpl$AsyncCallback");
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnterFilterInvoke(@Advice.Argument(1) Invocation invocation,
                                           @Advice.Local("span") Span span,
                                           @Advice.Local("transaction") Transaction transaction) {
        RpcContext context = RpcContext.getContext();
        ApacheDubboAttachmentHelper helper = attachmentHelperClassManager.getForClassLoaderOfClass(Invocation.class);
        if (helper == null || tracer == null) {
            return;
        }
        AbstractSpan<?> active = tracer.getActive();
        // for consumer side, just create span, more information will be collected in provider side
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
                                          @Advice.Local("span") @Nullable final Span span,
                                          @Advice.Thrown @Nullable Throwable t,
                                          @Advice.Local("transaction") @Nullable Transaction transaction) {

        RpcContext context = RpcContext.getContext();
        AbstractSpan<?> actualSpan = span != null ? span : transaction;
        if (actualSpan == null) {
            return;
        }

        actualSpan.deactivate();
        if (result instanceof AsyncRpcResult) {
            AsyncCallbackCreator callbackCreator = asyncCallbackCreatorClassManager.getForClassLoaderOfClass(Result.class);
            if (callbackCreator == null) {
                actualSpan.end();
                return;
            }
            context.set(DubboTraceHelper.SPAN_KEY, actualSpan);
            result.whenCompleteWithContext(callbackCreator.create(actualSpan));
        } else {
            actualSpan.end();
        }
    }
}
