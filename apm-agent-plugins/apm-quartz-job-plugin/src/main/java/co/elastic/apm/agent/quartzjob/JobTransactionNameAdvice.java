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
package co.elastic.apm.agent.quartzjob;

import co.elastic.apm.agent.bci.bytebuddy.SimpleMethodSignatureOffsetMappingFactory.SimpleMethodSignature;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.util.VersionUtils;
import net.bytebuddy.asm.Advice;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public class JobTransactionNameAdvice {

    private static final Logger logger = LoggerFactory.getLogger(JobTransactionNameInstrumentation.class);

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object setTransactionName(@Advice.Argument(value = 0) @Nullable JobExecutionContext context,
                                            @SimpleMethodSignature String signature,
                                            @Advice.Origin Class<?> clazz) {
        Transaction transaction = null;
        AbstractSpan<?> active = GlobalTracer.get().getActive();
        if (context == null) {
            logger.warn("Cannot correctly name transaction for method {} because JobExecutionContext is null", signature);
            transaction = createAndActivateTransaction(clazz, signature);
        } else if (active == null) {
            transaction = createAndActivateTransaction(clazz, context.getJobDetail().getKey().toString());
        } else {
            logger.debug("Not creating transaction for method {} because there is already a transaction running ({})", signature, active);
        }
        return transaction;
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onMethodExitException(@Advice.Argument(value = 0) @Nullable JobExecutionContext context,
                                             @Advice.Enter @Nullable Object transactionObj,
                                             @Advice.Thrown @Nullable Throwable t) {
        if (transactionObj instanceof Transaction) {
            Transaction transaction = (Transaction) transactionObj;
            if (context != null && context.getResult() != null) {
                transaction.withResultIfUnset(context.getResult().toString());
            }
            transaction.captureException(t)
                .withOutcome(t != null ? Outcome.FAILURE : Outcome.SUCCESS)
                .deactivate()
                .end();
        }
    }

    @Nullable
    private static Transaction createAndActivateTransaction(Class<?> originClass, String name) {
        Transaction transaction = GlobalTracer.get().startRootTransaction(originClass.getClassLoader());
        if (transaction != null) {
            transaction.withName(name)
                .withType(JobTransactionNameInstrumentation.TRANSACTION_TYPE)
                .activate();

            transaction.setFrameworkName("Quartz");
            transaction.setFrameworkVersion(VersionUtils.getVersion(JobExecutionContext.class, "org.quartz-scheduler", "quartz"));
        }
        return transaction;
    }
}
