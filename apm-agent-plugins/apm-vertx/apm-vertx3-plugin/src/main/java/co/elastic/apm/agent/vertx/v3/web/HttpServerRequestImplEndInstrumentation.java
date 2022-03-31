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
package co.elastic.apm.agent.vertx.v3.web;

import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.impl.HttpServerRequestImpl;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Instruments {@link HttpServerRequestImpl#handleEnd()} and {@link io.vertx.core.http.impl.Http2ServerRequestImpl#handleEnd} to finalize
 * the transaction and remove the mapping.
 */
@SuppressWarnings("JavadocReference")
public class HttpServerRequestImplEndInstrumentation extends WebInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("io.vertx.core.http.impl.HttpServerRequestImpl")
            .or(named("io.vertx.core.http.impl.Http2ServerRequestImpl"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("handleEnd");
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.vertx.v3.web.HttpServerRequestImplEndInstrumentation$HttpRequestEndAdvice";
    }

    public static class HttpRequestEndAdvice {

        private static final Logger log = LoggerFactory.getLogger(HttpRequestEndAdvice.class);

        private static final WebHelper helper = WebHelper.getInstance();

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static void exit(@Advice.This HttpServerRequest request,
                                @Advice.FieldValue(value = "response", typing = Assigner.Typing.DYNAMIC) @Nullable HttpServerResponse response) {
            Transaction transaction = helper.removeTransactionFromContext(request);
            if (transaction != null) {
                helper.finalizeTransaction(response, transaction);
                log.debug("VERTX-DEBUG: ended Vert.x transaction {} with details from this response: {}", transaction, response);
            } else {
                log.debug("VERTX-DEBUG: could not find a transaction for the following Vert.x request: {}", request);
            }
        }
    }
}
