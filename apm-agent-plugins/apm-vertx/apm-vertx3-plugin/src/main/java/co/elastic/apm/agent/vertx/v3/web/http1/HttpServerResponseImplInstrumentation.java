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
package co.elastic.apm.agent.vertx.v3.web.http1;

import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.vertx.v3.web.ResponseEndHandlerWrapper;
import co.elastic.apm.agent.vertx.v3.web.WebInstrumentation;
import io.vertx.core.http.HttpServerResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Instruments {@link io.vertx.core.http.impl.HttpServerResponseImpl} constructor to create and append {@link ResponseEndHandlerWrapper}
 * for transaction finalization.
 */
public class HttpServerResponseImplInstrumentation extends WebInstrumentation {

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
        return "co.elastic.apm.agent.vertx.v3.web.http1.HttpServerResponseImplInstrumentation$HttpResponseConstructorAdvice";
    }

    public static class HttpResponseConstructorAdvice {

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static void enter(@Advice.This HttpServerResponse response) {
            Transaction transaction = GlobalTracer.get().currentTransaction();
            if (transaction != null) {
                response.endHandler(new ResponseEndHandlerWrapper(transaction, response));
            }
        }
    }
}
