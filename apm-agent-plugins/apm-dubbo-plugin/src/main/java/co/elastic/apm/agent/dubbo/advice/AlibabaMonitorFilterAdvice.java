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
import co.elastic.apm.agent.dubbo.helper.DubboApiInfo;
import co.elastic.apm.agent.dubbo.helper.DubboTraceHelper;
import co.elastic.apm.agent.dubbo.helper.IgnoreExceptionHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.Scope;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcContext;
import net.bytebuddy.asm.Advice;

@VisibleForAdvice
public class AlibabaMonitorFilterAdvice {

    @VisibleForAdvice
    public static ElasticApmTracer tracer;

    @VisibleForAdvice
    public static HelperClassManager<AlibabaDubboAttachmentHelper> helperManager;

    public static void init(ElasticApmTracer tracer) {
        AlibabaMonitorFilterAdvice.tracer = tracer;
        DubboTraceHelper.init(tracer);
        IgnoreExceptionHelper.init(tracer);
        helperManager = HelperClassManager.ForAnyClassLoader.of(tracer,
            "co.elastic.apm.agent.dubbo.helper.AlibabaDubboAttachmentHelperImpl");
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnterFilterInvoke(@Advice.Argument(1) Invocation invocation,
                                           @Advice.Local("span") Span span,
                                           @Advice.Local("apiClazz") Class<?> apiClazz,
                                           @Advice.Local("transaction") Transaction transaction,
                                           @Advice.Local("scope") Scope scope) {
        RpcContext context = RpcContext.getContext();
        String version = context.getUrl().getParameter("version");

        Invoker<?> invoker = invocation.getInvoker();
        apiClazz = invoker.getInterface();
        DubboApiInfo apiInfo = new DubboApiInfo(apiClazz, invocation.getMethodName(), invocation.getParameterTypes(), version);
        AlibabaDubboAttachmentHelper helper = helperManager.getForClassLoaderOfClass(Invocation.class);
        if (helper == null) {
            return;
        }
        // for consumer side, just create span, more information will be collected in provider side
        if (context.isConsumerSide()) {
            if (tracer == null || tracer.getActive() == null) {
                return;
            }
            span = DubboTraceHelper.createConsumerSpan(apiInfo, context.getRemoteAddress());
            if (span != null) {
                span.getTraceContext().setOutgoingTraceContextHeaders(invocation, helper);
            }

            return;
        }

        // for provider side
        transaction = tracer.startChildTransaction(invocation, helper, Invocation.class.getClassLoader());
        if (transaction != null) {
            scope = transaction.activateInScope();
            DubboTraceHelper.fillTransaction(transaction, apiInfo);
        }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExitFilterInvoke(@Advice.Argument(1) Invocation invocation,
                                          @Advice.Return Result result,
                                          @Advice.Local("span") Span span,
                                          @Advice.Local("apiClazz") Class<?> apiClazz,
                                          @Advice.Thrown Throwable t,
                                          @Advice.Local("transaction") Transaction transaction,
                                          @Advice.Local("scope") Scope scope) {
        Throwable actualExp = t != null ? t : result.getException();
        RpcContext context = RpcContext.getContext();
        if (context.isConsumerSide()) {
            if (span == null) {
                return;
            }
            try {
                if (actualExp != null) {
                    if (DubboTraceHelper.isBizException(apiClazz, actualExp.getClass())) {
                        IgnoreExceptionHelper.addIgnoreException(actualExp);
                    }
                }
                //for consumer side, no need to capture exception, let upper application handle it or capture it
            } finally {
                span.deactivate();
                if (context.getFuture() == null) {
                    span.end();
                } else {
                    context.set(DubboTraceHelper.SPAN_KEY, span);
                }
            }
            return;
        }

        // provider side
        if (scope != null) {
            scope.close();
        }

        if (transaction != null) {
            try {
                boolean hasError = actualExp != null
                    && !DubboTraceHelper.isBizException(invocation.getInvoker().getInterface(), actualExp.getClass());
                if (hasError) {
                    transaction.captureException(actualExp);
                }
                Object ret = result != null ? result.getValue(): null;
                DubboTraceHelper.doCapture(invocation.getArguments(), actualExp, ret);
            } finally {
                transaction.deactivate().end();
            }
        }
    }
}
