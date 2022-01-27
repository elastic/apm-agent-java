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

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.util.VersionUtils;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.quartz.JobExecutionContext;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.isInAnyPackage;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

public abstract class AbstractJobTransactionNameInstrumentation extends TracerAwareInstrumentation {
    public static final String TRANSACTION_TYPE = "scheduled";
    public static final String INSTRUMENTATION_TYPE = "quartz";

    private final Collection<String> applicationPackages;

    protected AbstractJobTransactionNameInstrumentation(ElasticApmTracer tracer) {
        applicationPackages = tracer.getConfig(StacktraceConfiguration.class).getApplicationPackages();
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return isInAnyPackage(applicationPackages, ElementMatchers.<NamedElement>none())
            .or(nameStartsWith("org.quartz.job"))
            .and(hasSuperType(named("org.quartz.Job")))
            .and(declaresMethod(getMethodMatcher()));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList(INSTRUMENTATION_TYPE);
    }

    static class BaseAdvice {

        private static final Logger logger = LoggerFactory.getLogger(BaseAdvice.class);

        @Nullable
        protected static <T> Transaction createAndActivateTransaction(@Nullable T jobExecutionContext, String signature, Class<?> clazz, JobExecutionContextHandler<T> helper) {
            Transaction transaction = null;
            AbstractSpan<?> active = GlobalTracer.get().getActive();
            if (active == null) {
                String transactionName = null;
                if (jobExecutionContext != null) {
                    transactionName = helper.getJobDetailKey(jobExecutionContext);
                }
                if (transactionName != null) {
                    transaction = createAndActivateTransaction(clazz, transactionName);
                } else {
                    logger.warn("Cannot correctly name transaction for method {} because JobExecutionContext is null or lacking job details", signature);
                    transaction = createAndActivateTransaction(clazz, signature);
                }
            } else {
                logger.debug("Not creating transaction for method {} because there is already a transaction running ({})", signature, active);
            }
            return transaction;
        }

        protected static <T> void endTransaction(@Nullable T jobExecutionContext,
                                                 @Nullable Object transactionObj,
                                                 @Nullable Throwable t,
                                                 JobExecutionContextHandler<T> helper) {
            if (transactionObj instanceof Transaction) {
                Transaction transaction = (Transaction) transactionObj;
                if (jobExecutionContext != null) {
                    Object result = helper.getResult(jobExecutionContext);
                    if (result != null) {
                        transaction.withResultIfUnset(result.toString());
                    }
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
                    .withType(AbstractJobTransactionNameInstrumentation.TRANSACTION_TYPE)
                    .activate();

                transaction.setFrameworkName("Quartz");
                transaction.setFrameworkVersion(VersionUtils.getVersion(JobExecutionContext.class, "org.quartz-scheduler", "quartz"));
            }
            return transaction;
        }
    }

}
