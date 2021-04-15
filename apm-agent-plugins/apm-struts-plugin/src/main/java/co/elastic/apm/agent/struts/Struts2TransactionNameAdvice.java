/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2021 Elastic and contributors
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
package co.elastic.apm.agent.struts;

import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.util.VersionUtils;
import com.opensymphony.xwork2.ActionProxy;
import net.bytebuddy.asm.Advice;

import static co.elastic.apm.agent.impl.transaction.AbstractSpan.PRIO_HIGH_LEVEL_FRAMEWORK;

public class Struts2TransactionNameAdvice {

    private static final String FRAMEWORK_NAME = "Struts 2";

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void setTransactionName(@Advice.This ActionProxy actionProxy) {
        Transaction transaction = GlobalTracer.get().currentTransaction();
        if (transaction == null) {
            return;
        }

        transaction.setFrameworkName(FRAMEWORK_NAME);
        transaction.setFrameworkVersion(VersionUtils.getVersion(ActionProxy.class, "org.apache.struts", "struts2-core"));

        StringBuilder transactionName = transaction.getAndOverrideName(PRIO_HIGH_LEVEL_FRAMEWORK);
        if (transactionName != null) {
            String className = actionProxy.getAction().getClass().getSimpleName();
            String methodName = actionProxy.getMethod();

            transactionName.append(className).append('#').append(methodName);
        }
    }
}
