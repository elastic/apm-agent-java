/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.agent.vertx_3_6;

import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.impl.HttpServerRequestImpl;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

@SuppressWarnings("JavadocReference")
public abstract class Vertx3Http1Instrumentation extends Vertx3WebInstrumentation {

    /**
     * Instruments {@link io.vertx.core.http.impl.HttpServerRequestImpl#handleBegin()} to start transaction from.
     */
    public static class HttpServerRequestImplStartInstrumentation extends Vertx3Http1Instrumentation {
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("io.vertx.core.http.impl.HttpServerRequestImpl");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("handleBegin").and(takesNoArguments());
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.vertx_3_6.Vertx3Http1Instrumentation$HttpServerRequestImplStartInstrumentation$Http1RequestBeginAdvice";
        }

        public static class Http1RequestBeginAdvice {

            @Nullable
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static Object enter(@Advice.This HttpServerRequestImpl request) {
                Transaction transaction = Vertx3WebHelper.getInstance().startOrGetTransaction(request);
                if (transaction != null) {
                    transaction.activate();
                }
                return transaction;
            }

            @Advice.OnMethodExit(suppress = Throwable.class, inline = false, onThrowable = Throwable.class)
            public static void exit(@Advice.This HttpServerRequestImpl request,
                                    @Advice.Enter Object transactionObj,
                                    @Advice.Thrown @Nullable Throwable thrown) {
                if (transactionObj instanceof Transaction) {
                    Transaction transaction = (Transaction) transactionObj;
                    transaction.captureException(thrown).deactivate();
                }
            }
        }
    }

    /**
     * Instruments {@link io.vertx.core.http.impl.HttpServerRequestImpl#doEnd()} to remove the context from the context map again.
     */
    public static class HttpServerRequestImplEndInstrumentation extends Vertx3Http1Instrumentation {
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("io.vertx.core.http.impl.HttpServerRequestImpl");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("doEnd").and(takesNoArguments());
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.vertx_3_6.Vertx3Http1Instrumentation$HttpServerRequestImplEndInstrumentation$Http1RequestEndAdvice";
        }

        public static class Http1RequestEndAdvice {

            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static void exit(@Advice.This HttpServerRequestImpl request) {
                Vertx3WebHelper.getInstance().removeTransactionFromContext(request);
            }
        }
    }

    /**
     * Instruments {@link io.vertx.ext.web.impl.HttpServerRequestWrapper#endHandler(io.vertx.core.Handler)} to use this method with a marker-argument
     * of type {@link Vertx3WebHelper.NoopHandler} to unwrap the original {@link io.vertx.core.http.impl.HttpServerRequestImpl} object.
     * <p>
     * See {@link Vertx3WebHelper#setRouteBasedNameForCurrentTransaction(io.vertx.ext.web.RoutingContext)}.
     */
    public static class HttpServerRequestWrapperInstrumentation extends Vertx3Http1Instrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("io.vertx.ext.web.impl.HttpServerRequestWrapper");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("endHandler")
                .and(takesArgument(0, named("io.vertx.core.Handler")))
                .and(returns(named("io.vertx.core.http.HttpServerRequest")));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.vertx_3_6.Vertx3Http1Instrumentation$HttpServerRequestWrapperInstrumentation$HttpServerRequestWrapperAdvice";
        }

        public static class HttpServerRequestWrapperAdvice {

            @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class, suppress = Throwable.class, inline = false)
            public static boolean enter(@Advice.Argument(0) Handler<Void> handler) {
                return handler instanceof Vertx3WebHelper.NoopHandler;
            }

            @AssignTo.Return
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static HttpServerRequest exit(@Advice.FieldValue(value = "delegate") HttpServerRequest delegate) {
                return delegate;
            }
        }
    }

    /**
     * Instruments {@link io.vertx.core.http.impl.HttpServerResponseImpl} constructor to create and append {@link Vertx3ResponseEndHandlerWrapper}
     * for transaction finalization.
     */
    public static class HttpServerResponseImplInstrumentation extends Vertx3Http1Instrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("io.vertx.core.http.impl.HttpServerResponseImpl");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return isConstructor();
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.vertx_3_6.Vertx3Http1Instrumentation$HttpServerResponseImplInstrumentation$Http1ResponseConstructorAdvice";
        }

        public static class Http1ResponseConstructorAdvice {

            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static void enter(@Advice.This HttpServerResponse response) {
                Transaction transaction = GlobalTracer.get().currentTransaction();
                if (transaction != null) {
                    response.endHandler(new Vertx3ResponseEndHandlerWrapper(transaction, response));
                }
            }
        }
    }
}
