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

import co.elastic.apm.agent.dubbo.helper.ApacheDubboTextMapPropagator;
import co.elastic.apm.agent.dubbo.helper.DubboTraceHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import org.apache.dubbo.rpc.AsyncRpcResult;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;

public class ApacheMonitorFilterAdvice {

    private static final ElasticApmTracer tracer = GlobalTracer.requireTracerImpl();

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object onEnterFilterInvoke(@Advice.Argument(1) Invocation invocation) {
        RpcContext context = RpcContext.getContext();
        AbstractSpan<?> active = tracer.getActive();
        // for consumer side, just create span, more information will be collected in provider side
        if (context.isConsumerSide() && active != null) {
            Span span = DubboTraceHelper.createConsumerSpan(tracer, invocation.getInvoker().getInterface(),
                invocation.getMethodName(), context.getRemoteAddress());
            if (span != null) {
                span.propagateTraceContext(context, ApacheDubboTextMapPropagator.INSTANCE);
                return span;
            }
        } else if (context.isProviderSide() && active == null) {
            // for provider side
            Transaction transaction = tracer.startChildTransaction(context, ApacheDubboTextMapPropagator.INSTANCE, Invocation.class.getClassLoader());
            if (transaction != null) {
                transaction.activate();
                DubboTraceHelper.fillTransaction(transaction, invocation.getInvoker().getInterface(), invocation.getMethodName());
                return transaction;
            }
        }
        return null;
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExitFilterInvoke(@Advice.Argument(1) Invocation invocation,
                                          @Advice.Return @Nullable Result result,
                                          @Advice.Enter @Nullable final Object spanObj,
                                          @Advice.Thrown @Nullable Throwable t) {

        AbstractSpan<?> span = (AbstractSpan<?>) spanObj;
        RpcContext context = RpcContext.getContext();
        if (span == null) {
            return;
        }

        span.deactivate();
        if (result instanceof AsyncRpcResult) {
            context.set(DubboTraceHelper.SPAN_KEY, span);
            result.whenCompleteWithContext(AsyncCallback.INSTANCE);
        } else {
            span.end();
        }
    }

    public static class AsyncCallback implements BiConsumer<Result, Throwable> {

        private final static BiConsumer<Result, Throwable> INSTANCE = new AsyncCallback();

        @Override
        public void accept(@Nullable Result result, @Nullable Throwable t) {
            AbstractSpan<?> span = (AbstractSpan<?>) RpcContext.getContext().get(DubboTraceHelper.SPAN_KEY);
            if (span != null) {
                try {
                    RpcContext.getContext().remove(DubboTraceHelper.SPAN_KEY);

                    Throwable resultException = null;
                    if (result != null) {
                        resultException = result.getException();
                    }

                    span.captureException(t)
                        .captureException(resultException)
                        .withOutcome(t != null || resultException != null ? Outcome.FAILURE : Outcome.SUCCESS);
                } finally {
                    span.end();
                }
            }
        }
    }
}
