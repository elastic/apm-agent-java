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
package co.elastic.apm.agent.vertx.v3.web.http1;

import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.vertx.v3.web.WebHelper;
import co.elastic.apm.agent.vertx.v3.web.WebInstrumentation;
import io.vertx.core.http.impl.HttpServerRequestImpl;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

/**
 * Instruments {@link io.vertx.core.http.impl.HttpServerRequestImpl#handleBegin()} to start transaction from.
 */
@SuppressWarnings("JavadocReference")
public class HttpServerRequestImplStartInstrumentation extends WebInstrumentation {
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
        return "co.elastic.apm.agent.vertx.v3.web.http1.HttpServerRequestImplStartInstrumentation$HttpRequestBeginAdvice";
    }

    public static class HttpRequestBeginAdvice {

        private static final WebHelper helper = WebHelper.getInstance();

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object enter(@Advice.This HttpServerRequestImpl request) {
            Transaction transaction = helper.startOrGetTransaction(request);
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
