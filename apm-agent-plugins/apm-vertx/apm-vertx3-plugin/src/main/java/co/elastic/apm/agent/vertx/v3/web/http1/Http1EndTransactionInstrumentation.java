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

import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.vertx.v3.web.WebHelper;
import co.elastic.apm.agent.vertx.v3.web.WebInstrumentation;
import io.vertx.core.http.impl.Http1xServerConnection;
import io.vertx.core.http.impl.HttpServerRequestImpl;
import io.vertx.core.http.impl.HttpServerResponseImpl;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

/**
 * Instruments {@link Http1xServerConnection#responseComplete()} to finalize the transaction and remove request mapping.
 */
public class Http1EndTransactionInstrumentation extends WebInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("io.vertx.core.http.impl.Http1xServerConnection");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("responseComplete").and(takesNoArguments());
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.vertx.v3.web.http1.Http1EndTransactionInstrumentation$ResponseCompleteAdvice";
    }

    public static class ResponseCompleteAdvice {

        private static final Logger log = LoggerFactory.getLogger(ResponseCompleteAdvice.class);

        private static final WebHelper helper = WebHelper.getInstance();

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void exit(@Advice.FieldValue("responseInProgress") HttpServerRequestImpl responseInProgress) {
            Transaction<?> transaction = helper.removeTransactionMapping(responseInProgress);
            if (transaction != null) {
                HttpServerResponseImpl response = responseInProgress.response();
                if (response != null) {
                    helper.finalizeTransaction(response, transaction);
                    log.debug("VERTX-DEBUG: ended Vert.x HTTP 1 transaction {} with details from this response: {}", transaction, response);
                } else {
                    log.debug("VERTX-DEBUG: response is not yet set for the following Vert.x HTTP 1 request: {}", responseInProgress);
                }
            } else {
                log.debug("VERTX-DEBUG: could not find a transaction for the following Vert.x HTTP 1 request: {}", responseInProgress);
            }
        }
    }
}
