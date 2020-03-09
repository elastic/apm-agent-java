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
import co.elastic.apm.agent.dubbo.helper.ApacheDubboAttachmentHelper;
import co.elastic.apm.agent.dubbo.helper.DubboApiInfo;
import co.elastic.apm.agent.dubbo.helper.DubboTraceHelper;
import co.elastic.apm.agent.dubbo.helper.IgnoreExceptionHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;

@VisibleForAdvice
public class ApacheDubboFilterAdvice {

    @VisibleForAdvice
    public static ElasticApmTracer tracer;

    @VisibleForAdvice
    public static HelperClassManager<ApacheDubboAttachmentHelper> helperClassManager;

    public static void init(ElasticApmTracer tracer) {
        ApacheDubboFilterAdvice.tracer = tracer;
        DubboTraceHelper.init(tracer);
        IgnoreExceptionHelper.init(tracer);
        helperClassManager = HelperClassManager.ForAnyClassLoader.of(tracer,
            "co.elastic.apm.agent.dubbo.helper.ApacheDubboAttachmentHelperImpl");
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnterFilterInvoke(@Advice.Argument(1) Invocation invocation,
                                           @Advice.Local("span") Span span,
                                           @Advice.Local("apiClazz") Class<?> apiClazz) {
        RpcContext context = RpcContext.getContext();
        String version = context.getUrl().getParameter("version");
        Invoker<?> invoker = invocation.getInvoker();
        apiClazz = invoker.getInterface();
        DubboApiInfo dubboApiInfo = new DubboApiInfo(
            apiClazz, invocation.getMethodName(),
            invocation.getParameterTypes(), version);
        ApacheDubboAttachmentHelper helper = helperClassManager.getForClassLoaderOfClass(Invocation.class);
        // for consumer side, just create span, more information will be collected in provider side
        if (context.isConsumerSide()) {
            if (tracer == null || tracer.getActive() == null) {
                return;
            }

            span = DubboTraceHelper.createConsumerSpan(dubboApiInfo, context.getRemoteAddress());
            if (span != null) {
                span.getTraceContext().setOutgoingTraceContextHeaders(invocation, helper);
            }
            return;
        }


        // for provider side
        if (tracer.currentTransaction() == null) {
            tracer.startChildTransaction(invocation, helper, Invocation.class.getClassLoader()).activate();
        }

        Transaction transaction = tracer.currentTransaction();
        if (transaction != null) {
            DubboTraceHelper.fillTransaction(transaction, dubboApiInfo);
        }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExitFilterInvoke(@Advice.Argument(1) Invocation invocation,
                                          @Advice.Return Result result,
                                          @Advice.Local("span") Span span,
                                          @Advice.Local("apiClazz") Class<?> apiClazz,
                                          @Advice.Thrown Throwable t) {

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
                span.deactivate().end();
            }
            return;
        }

        // provider side
        Transaction transaction = tracer.currentTransaction();
        if (transaction != null) {
            try {
                boolean hasError = actualExp != null
                    && !DubboTraceHelper.isBizException(invocation.getInvoker().getInterface(), actualExp.getClass());
                if (hasError) {
                    transaction.captureException(actualExp);
                }
                Object ret = result != null ? result.getValue() : null;
                DubboTraceHelper.doCapture(invocation.getArguments(), actualExp, ret);
            } finally {
                transaction.deactivate().end();
            }
        }
    }
}
