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

import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.vertx.v3.web.WebHelper;
import co.elastic.apm.agent.vertx.v3.web.WebInstrumentation;
import io.netty.buffer.ByteBuf;
import io.vertx.core.http.impl.Http2ServerResponseImpl;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

@SuppressWarnings("JavadocReference")
public abstract class Http2ServerResponseImplEndInstrumentation extends WebInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("io.vertx.core.http.impl.Http2ServerResponseImpl");
    }

    /**
     * Instruments {@link Http2ServerResponseImpl#write(ByteBuf, boolean)} to remove transaction mapping for this response.
     */
    public static class WriteInstrumentation extends Http2ServerResponseImplEndInstrumentation {
        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("write").and(takesArgument(1, boolean.class));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.vertx.v3.web.http2.Http2ServerResponseImplEndInstrumentation$WriteInstrumentation$WriteAdvice";
        }

        public static class WriteAdvice {
            private static final Logger log = LoggerFactory.getLogger(WriteAdvice.class);

            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static void writeExit(@Advice.This Http2ServerResponseImpl response,
                                         @Advice.Argument(1) boolean end) {
                if (end) {
                    Transaction transaction = WebHelper.getInstance().removeTransactionMapping(response);
                    log.debug("VERTX-DEBUG: removing transaction {} mapping to response {}", transaction, response);
                }
            }
        }
    }

    /**
     * Instruments {@link Http2ServerResponseImpl#handleClose()} and {@link Http2ServerResponseImpl#close()} to remove transaction
     * mapping for this response.
     */
    public static class CloseInstrumentation extends Http2ServerResponseImplEndInstrumentation {
        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("close");
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.vertx.v3.web.http2.Http2ServerResponseImplEndInstrumentation$CloseInstrumentation$CloseAdvice";
        }

        public static class CloseAdvice {
            private static final Logger log = LoggerFactory.getLogger(CloseAdvice.class);

            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static void closeExit(@Advice.This Http2ServerResponseImpl response) {
                Transaction transaction = WebHelper.getInstance().removeTransactionMapping(response);
                log.debug("VERTX-DEBUG: removing transaction {} mapping to response {}", transaction, response);
            }
        }
    }
}
