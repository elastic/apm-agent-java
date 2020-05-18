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
package co.elastic.apm.agent.dubbo;

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import com.alibaba.dubbo.remoting.exchange.ResponseCallback;
import com.alibaba.dubbo.rpc.Result;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isOverriddenFrom;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Can't get the span from the {@link com.alibaba.dubbo.rpc.RpcContext} as it is reset already.
 * Therefore, the {@link ResponseCallback} is mapped to the {@link AbstractSpan} in {@link AlibabaResponseFutureInstrumentation}
 */
public abstract class AlibabaResponseCallbackInstrumentation extends AbstractDubboInstrumentation {
    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("com.alibaba.dubbo.remoting.exchange.ResponseCallback"));
    }

    public static class DoneInstrumentation extends AlibabaResponseCallbackInstrumentation {

        /**
         * {@link com.alibaba.dubbo.remoting.exchange.ResponseCallback#done(Object)}
         */
        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("done").and(isOverriddenFrom(named("com.alibaba.dubbo.remoting.exchange.ResponseCallback")));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onEnter(@Advice.This ResponseCallback thiz, @Advice.Local("span") AbstractSpan<?> span) {
            span = AlibabaResponseFutureInstrumentation.callbackSpanMap.remove(thiz);
            if (span != null) {
                span.activate();
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        private static void onExit(@Advice.Thrown Throwable thrown,
                                   @Nullable @Advice.Local("span") AbstractSpan<?> span,
                                   @Nullable @Advice.Argument(0) Object response) {
            if (span == null) {
                return;
            }
            if (response instanceof Result) {
                span.captureException(((Result) response).getException());
            }
            span.captureException(thrown).deactivate().end();
        }
    }

    public static class CaughtInstrumentation extends AlibabaResponseCallbackInstrumentation {

        /**
         * {@link com.alibaba.dubbo.remoting.exchange.ResponseCallback#caught(Throwable)}
         */
        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("caught").and(isOverriddenFrom(named("com.alibaba.dubbo.remoting.exchange.ResponseCallback")));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onEnter(@Advice.This ResponseCallback thiz, @Advice.Local("span") AbstractSpan<?> span) {
            span = AlibabaResponseFutureInstrumentation.callbackSpanMap.remove(thiz);
            if (span != null) {
                span.activate();
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        private static void onExit(@Advice.Thrown Throwable thrown,
                                   @Nullable @Advice.Local("span") AbstractSpan<?> span,
                                   @Nullable @Advice.Argument(0) Throwable caught) {
            if (span == null) {
                return;
            }
            span.captureException(thrown)
                .captureException(caught)
                .deactivate()
                .end();
        }
    }
}
