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
import io.vertx.ext.web.RoutingContext;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;

public class RouteImplAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object nextEnter(@Advice.Argument(value = 0) RoutingContext routingContext) {
        Transaction transaction = VertxWebHelper.getInstance().setRouteBasedNameForCurrentTransaction(routingContext);

        if (transaction != null) {
            transaction.activate();
        }

        return transaction;
    }

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false, onThrowable = Throwable.class)
    public static void nextExit(@Advice.Argument(value = 0) RoutingContext routingContext,
                                @Nullable @Advice.Enter Object transactionObj, @Nullable @Advice.Thrown Throwable thrown) {
        if (transactionObj instanceof Transaction) {
            Transaction transaction = (Transaction) transactionObj;
            transaction.captureException(thrown).deactivate();
        }
    }
}
