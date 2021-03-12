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

import co.elastic.apm.agent.sdk.advice.AssignTo;
import co.elastic.apm.agent.vertx_3_6.wrapper.ResponseEndHandlerWrapper;
import io.vertx.core.Handler;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;

public class ResponseEndHandlerAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @AssignTo.Field(value = "endHandler")
    public static Handler<Void> wrapHandler(@Advice.Argument(value = 0) Handler<Void> handler,
                                            @Advice.FieldValue(value = "endHandler") @Nullable Handler<Void> internalHandler) {
        if (internalHandler instanceof ResponseEndHandlerWrapper && handler instanceof ResponseEndHandlerWrapper) {
            // avoid setting our wrapper multiple times
            return internalHandler;
        }

        if (handler instanceof ResponseEndHandlerWrapper) {
            if (internalHandler != null) {
                // wrap the existing internal handler into our added wrapper
                ((ResponseEndHandlerWrapper) handler).setActualHandler(internalHandler);
            }
            return handler;
        } else if (internalHandler instanceof ResponseEndHandlerWrapper) {
            // wrap new added handler into our wrapper that already is the internal one
            ((ResponseEndHandlerWrapper) internalHandler).setActualHandler(handler);
            return internalHandler;
        }

        return internalHandler;
    }
}
