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

import co.elastic.apm.agent.impl.transaction.Transaction;
import io.vertx.core.http.impl.Http2ServerRequestImpl;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Instruments {@link io.vertx.core.http.impl.Http2ServerRequestImpl} to start transaction and append {@link Vertx3ResponseEndHandlerWrapper}
 * for later transaction finalization.
 */
public class Vertx3Http2Instrumentation extends Vertx3WebInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("io.vertx.core.http.impl.Http2ServerRequestImpl");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return isConstructor();
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.vertx_3_6.Vertx3Http2Instrumentation$Http2ServerRequestAdvice";
    }

    public static class Http2ServerRequestAdvice {

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static void enter(@Advice.This Http2ServerRequestImpl request) {
            Transaction transaction = Vertx3WebHelper.getInstance().startOrGetTransaction(request);

            if (transaction != null && request.response() != null) {
                request.response().endHandler(new Vertx3ResponseEndHandlerWrapper(transaction, request.response(), request));
            }
        }
    }
}
