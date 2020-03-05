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

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.dubbo.helper.ApacheDubboAttachmentHelper;
import co.elastic.apm.agent.dubbo.helper.DubboApiInfo;
import co.elastic.apm.agent.dubbo.helper.DubboHelper;
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
    public static ApacheDubboAttachmentHelper helper = new ApacheDubboAttachmentHelper();

    public static void init(ElasticApmTracer tracer) {
        ApacheDubboFilterAdvice.tracer = tracer;
        DubboHelper.init(tracer);
        IgnoreExceptionHelper.init(tracer);
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

        // for consumer side, just create span, more information will be collected in provider side
        if (context.isConsumerSide()) {
            if (tracer == null || tracer.getActive() == null) {
                return;
            }

            span = DubboHelper.createConsumerSpan(dubboApiInfo, context.getRemoteAddress());
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
            DubboHelper.fillTransaction(transaction, dubboApiInfo);
        }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExitFilterInvoke(@Advice.Argument(1) Invocation invocation,
                                          @Advice.Return Result result,
                                          @Advice.Local("span") Span span,
                                          @Advice.Local("apiClazz") Class<?> apiClazz) {

        Throwable t = result.getException();
        RpcContext context = RpcContext.getContext();
        if (context.isConsumerSide()) {
            if (span == null) {
                return;
            }
            try {
                if (t != null) {
                    if (DubboHelper.isBizException(apiClazz, t.getClass())) {
                        IgnoreExceptionHelper.addIgnoreException(t);
                    }
                }
                String providerServiceName = result.getAttachment(DubboHelper.PROVIDER_SERVICE_NAME_KEY);
                if (providerServiceName != null) {
                    span.getContext().getDestination().getService().withName(providerServiceName);
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
                String providerServiceName = tracer.getConfig(CoreConfiguration.class).getServiceName();
                result.setAttachment(DubboHelper.PROVIDER_SERVICE_NAME_KEY, providerServiceName);
                boolean hasError = t != null
                    && !DubboHelper.isBizException(invocation.getInvoker().getInterface(), t.getClass());
                if (hasError) {
                    transaction.captureException(t);
                }

                DubboHelper.doCapture(invocation.getArguments(), t, result.getValue());
            } finally {
                transaction.deactivate().end();
            }
        }
    }
}
