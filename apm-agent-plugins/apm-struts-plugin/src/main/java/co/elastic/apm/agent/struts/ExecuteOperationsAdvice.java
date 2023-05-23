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
package co.elastic.apm.agent.struts;

import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.impl.context.web.WebConfiguration;
import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.util.TransactionNameUtils;
import net.bytebuddy.asm.Advice;

import javax.servlet.http.HttpServletRequest;

import static co.elastic.apm.agent.tracer.AbstractSpan.PRIORITY_HIGH_LEVEL_FRAMEWORK;

public class ExecuteOperationsAdvice {

    private static final WebConfiguration webConfig = GlobalTracer.get().getConfig(WebConfiguration.class);

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void setTransactionName(@Advice.Argument(0) HttpServletRequest request, @Advice.Return boolean handled) {
        Transaction<?> transaction = GlobalTracer.get().currentTransaction();
        if (!handled || transaction == null) {
            return;
        }

        StringBuilder transactionName = transaction.getAndOverrideName(PRIORITY_HIGH_LEVEL_FRAMEWORK);
        if (transactionName != null) {
            TransactionNameUtils.setNameFromHttpRequestPath(request.getMethod(), request.getServletPath(), transactionName, webConfig.getUrlGroups());
            StrutsFrameworkUtils.setFrameworkNameAndVersion(transaction);
        }
    }
}
