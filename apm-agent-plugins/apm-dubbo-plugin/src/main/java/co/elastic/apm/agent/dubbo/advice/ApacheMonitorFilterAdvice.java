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
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.sdk.internal.util.PrivilegedActionUtils;
import net.bytebuddy.asm.Advice;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.AsyncRpcResult;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.InvokeMode;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcInvocation;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;

public class ApacheMonitorFilterAdvice {

    private static final Tracer tracer = GlobalTracer.get();

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object onEnterFilterInvoke(@Advice.Argument(0) Invoker<?> invoker,
                                             @Advice.Argument(1) Invocation invocation) {

        RpcContext context = RpcContext.getContext();
        AbstractSpan<?> active = tracer.getActive();
        // for consumer side, just create span, more information will be collected in provider side
        if (context.isConsumerSide()) {
            Span<?> span = null;
            if (active != null) {
                span = DubboTraceHelper.createConsumerSpan(tracer, invocation.getInvoker().getInterface(), invocation.getMethodName(), context.getRemoteAddress());
            }
            tracer.currentContext().propagateContext(context, ApacheDubboTextMapPropagator.INSTANCE, null);
            return span;
        } else if (context.isProviderSide() && active == null) {
            // for provider side
            Transaction<?> transaction = tracer.startChildTransaction(context, ApacheDubboTextMapPropagator.INSTANCE, PrivilegedActionUtils.getClassLoader(Invocation.class));
            if (transaction != null) {
                transaction.activate();
                DubboTraceHelper.fillTransaction(transaction, invocation.getInvoker().getInterface(), invocation.getMethodName());
                return transaction;
            }
        }
        return null;
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExitFilterInvoke(@Advice.Argument(0) Invoker<?> invoker,
                                          @Advice.Argument(1) Invocation invocation,
                                          @Advice.Return @Nullable Result result,
                                          @Advice.Enter @Nullable final Object spanObj,
                                          @Advice.Thrown @Nullable Throwable thrown) {

        AbstractSpan<?> span = (AbstractSpan<?>) spanObj;
        if (span == null) {
            return;
        }

        span.captureException(thrown)
            .deactivate();

        if (result instanceof AsyncRpcResult) {
            RpcContext.getContext().set(DubboTraceHelper.SPAN_KEY, span);
            if(invocation instanceof RpcInvocation){
                RpcContext.getContext().set(DubboTraceHelper.INVOKE_MODE, ((RpcInvocation) invocation).getInvokeMode());
            }
            result.whenCompleteWithContext(AsyncCallback.INSTANCE);
        } else {
            span.withSync(true)
                .end();
        }

    }

    public static class AsyncCallback implements BiConsumer<Result, Throwable> {

        private final static BiConsumer<Result, Throwable> INSTANCE = new AsyncCallback();

        @Override
        public void accept(@Nullable Result result, @Nullable Throwable t) {
            AbstractSpan<?> span = (AbstractSpan<?>) RpcContext.getContext().get(DubboTraceHelper.SPAN_KEY);
            /*
             * because of RpcContextAttachment#getAttachment(String) check value type if not string return null
             * @see https://github.com/apache/dubbo/blob/dubbo-3.3.4/dubbo-rpc/dubbo-rpc-api/src/main/java/org/apache/dubbo/rpc/RpcContextAttachment.java#L78
             */
            if (span == null) {
                span = (AbstractSpan<?>) RpcContext.getContext().get().get(DubboTraceHelper.SPAN_KEY);
            }
            if(span == null){
                return;
            }
            try {
                RpcContext.getContext().remove(DubboTraceHelper.SPAN_KEY);

                Throwable resultException = null;
                if (result != null) {
                    resultException = result.getException();
                }

                if(span instanceof Span){
                    InvokeMode invokeMode = (InvokeMode) RpcContext.getContext().get(DubboTraceHelper.INVOKE_MODE);
                    if (invokeMode != null && invokeMode != InvokeMode.SYNC) {
                        span.withSync(false);
                    } else {
                        span.withSync(result instanceof AppResponse);
                    }
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
