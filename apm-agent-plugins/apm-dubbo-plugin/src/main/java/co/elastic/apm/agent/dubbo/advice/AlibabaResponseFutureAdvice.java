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
import co.elastic.apm.agent.dubbo.helper.DubboTraceHelper;
import co.elastic.apm.agent.dubbo.helper.WrapperCreator;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import com.alibaba.dubbo.remoting.exchange.ResponseCallback;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcContext;
import net.bytebuddy.asm.Advice;

public class AlibabaResponseFutureAdvice {

    @VisibleForAdvice
    public static ElasticApmTracer tracer;

    public static HelperClassManager<WrapperCreator<ResponseCallback>> helperClassManager;

    public static void init(ElasticApmTracer tracer) {
        AlibabaResponseFutureAdvice.tracer = tracer;
        DubboTraceHelper.init(tracer);
        helperClassManager = HelperClassManager.ForAnyClassLoader.of(tracer,
            AlibabaResponseFutureAdvice.class.getName() + "$CallbackWrapperCreator",
            AlibabaResponseFutureAdvice.class.getName() + "$CallbackWrapperCreator$CallbackWrapper");
    }

    @VisibleForAdvice
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(value = 0, readOnly = false) ResponseCallback callback) {
        RpcContext context = RpcContext.getContext();
        WrapperCreator<ResponseCallback> wrapperCreator =
            helperClassManager.getForClassLoaderOfClass(ResponseCallback.class);
        if (wrapperCreator == null) {
            return;
        }

        Span span = (Span) context.get(DubboTraceHelper.SPAN_KEY);
        if (span == null) {
            return;
        }
        callback = wrapperCreator.wrap(callback, span);
        context.remove(DubboTraceHelper.SPAN_KEY);
    }

    public static class CallbackWrapperCreator implements WrapperCreator<ResponseCallback> {

        @Override
        public ResponseCallback wrap(ResponseCallback delegate, Span span) {
            return new CallbackWrapper(delegate, span);
        }

        public static class CallbackWrapper implements ResponseCallback {

            private ResponseCallback delegate;

            private Span span;

            public CallbackWrapper(ResponseCallback delegate, Span span) {
                this.delegate = delegate;
                this.span = span;
            }

            @Override
            public void done(Object response) {
                try {
                    if (response instanceof Result) {
                        Result result = (Result) response;
                        if (result.hasException()) {
                            span.captureException(result.getException());
                        }
                    }
                    delegate.done(response);
                } finally {
                    if (span != null) {
                        span.end();
                    }
                }
            }

            @Override
            public void caught(Throwable exception) {
                try {
                    span.captureException(exception);
                    delegate.caught(exception);
                } finally {
                    if (span != null) {
                        span.end();
                    }
                }
            }
        }
    }

}
