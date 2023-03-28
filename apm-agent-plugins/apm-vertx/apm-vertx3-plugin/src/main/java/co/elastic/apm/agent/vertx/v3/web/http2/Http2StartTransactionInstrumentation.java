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
package co.elastic.apm.agent.vertx.v3.web.http2;

import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.vertx.v3.web.WebHelper;
import co.elastic.apm.agent.vertx.v3.web.WebInstrumentation;
import io.vertx.core.http.impl.Http2ServerRequestImpl;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

/**
 * Instruments {@link io.vertx.core.http.impl.Http2ServerConnection#createRequest} to start transaction
 */
public class Http2StartTransactionInstrumentation extends WebInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("io.vertx.core.http.impl.Http2ServerConnection");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("createRequest").and(returns(named("io.vertx.core.http.impl.Http2ServerRequestImpl")));
    }

    public static class AdviceClass {

        private static final Logger log = LoggerFactory.getLogger(AdviceClass.class);

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static void exit(@Advice.Return Http2ServerRequestImpl request) {
            Transaction<?> transaction = WebHelper.getInstance().startOrGetTransaction(request);
            if (transaction != null) {
                // In HTTP 2, there may still be response processing after request end has ended the transaction
                WebHelper.getInstance().mapTransaction(request.response(), transaction);
            }
            log.debug("VERTX-DEBUG: started Vert.x 3.x HTTP 2 transaction: {}", transaction);
        }
    }
}
