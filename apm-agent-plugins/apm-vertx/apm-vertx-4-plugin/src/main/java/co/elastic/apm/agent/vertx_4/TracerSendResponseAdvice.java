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
package co.elastic.apm.agent.vertx_4;

import co.elastic.apm.agent.impl.transaction.Transaction;
import io.vertx.core.Context;
import io.vertx.core.http.HttpServerResponse;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;

public class TracerSendResponseAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void receiveRequest(@Advice.Argument(value = 0) Context context, @Advice.Argument(value = 1) Object response,
                                      @Nullable @Advice.Argument(value = 3) Throwable failure) {

        Object transactionObj = context.getLocal(VertxWebHelper.CONTEXT_TRANSACTION_KEY);
        if (transactionObj instanceof Transaction) {
            Transaction transaction = (Transaction) transactionObj;
            if (failure != null) {
                transaction.captureException(failure);
            }
            if (response instanceof HttpServerResponse) {
                VertxWebHelper.getInstance().finalizeTransaction((HttpServerResponse) response, transaction);
            } else {
                transaction.end();
            }
        }
    }
}
