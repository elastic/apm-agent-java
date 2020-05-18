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

import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.dubbo.AlibabaResponseFutureInstrumentation;
import co.elastic.apm.agent.dubbo.helper.AlibabaDubboAttachmentHelper;
import co.elastic.apm.agent.dubbo.helper.DubboTraceHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.alibaba.dubbo.remoting.exchange.ResponseFuture;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.protocol.dubbo.FutureAdapter;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

@VisibleForAdvice
public class AlibabaMonitorFilterAdvice {

    @VisibleForAdvice
    public static final List<Class<? extends ElasticApmInstrumentation>> RESPONSE_FUTURE_INSTRUMENTATION = Collections.<Class<? extends ElasticApmInstrumentation>>singletonList(AlibabaResponseFutureInstrumentation.class);
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
        if (context.isConsumerSide()) {
            if (tracer.getActive() == null) {
                return;
            }
            span = DubboTraceHelper.createConsumerSpan(tracer, invocation.getInvoker().getInterface(),
                invocation.getMethodName(), context.getRemoteAddress());
            if (span != null) {
                span.getTraceContext().setOutgoingTraceContextHeaders(invocation, helper);
            }
        } else {
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
                                          @Advice.Return Result result,
                                          @Nullable @Advice.Local("span") Span span,
                                          @Nullable @Advice.Thrown Throwable t,
                                          @Nullable @Advice.Local("transaction") Transaction transaction) {
        Throwable actualExp = t != null ? t : result.getException();
        RpcContext context = RpcContext.getContext();
        if (span != null) {
            span.captureException(actualExp).deactivate();
            Future<Object> future = context.getFuture();
            if (future instanceof FutureAdapter) {
                context.set(DubboTraceHelper.SPAN_KEY, span);
                Class<? extends ResponseFuture> futureClass = ((FutureAdapter<?>) future).getFuture().getClass();
                ElasticApmAgent.ensureInstrumented(futureClass, RESPONSE_FUTURE_INSTRUMENTATION);
            } else {
                span.end();
            }
        } else if (transaction != null) {
            transaction.captureException(actualExp).deactivate().end();
        }

    }
}
