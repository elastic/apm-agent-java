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
package co.elastic.apm.agent.scheduled;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.bci.bytebuddy.SimpleMethodSignatureOffsetMappingFactory.SimpleMethodSignature;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.isInAnyPackage;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

public class ScheduledTransactionNameInstrumentation extends TracerAwareInstrumentation {

    public static final Logger logger = LoggerFactory.getLogger(ScheduledTransactionNameInstrumentation.class);

    private final Collection<String> applicationPackages;

    public ScheduledTransactionNameInstrumentation(ElasticApmTracer tracer) {
        applicationPackages = tracer.getConfig(StacktraceConfiguration.class).getApplicationPackages();
    }

    public static class ScheduledTransactionNameAdvice {
        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object setTransactionName(@SimpleMethodSignature String signature,
                                                @Advice.Origin Class<?> clazz) {
            AbstractSpan<?> active = tracer.getActive();
            if (active == null) {
                Transaction transaction = tracer.startRootTransaction(clazz.getClassLoader());
                if (transaction != null) {
                    transaction.withName(signature)
                        .withType("scheduled")
                        .activate();
                    return transaction;
                }
            } else {
                logger.debug("Not creating transaction for method {} because there is already a transaction running ({})", signature, active);
            }
            return null;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onMethodExit(@Advice.Enter @Nullable Object transactionObj,
                                        @Advice.Thrown @Nullable Throwable t) {
            if (transactionObj instanceof Transaction) {
                Transaction transaction = (Transaction) transactionObj;
                transaction.captureException(t)
                    .withOutcome(t != null ? Outcome.FAILURE : Outcome.SUCCESS)
                    .deactivate()
                    .end();
            }
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return isInAnyPackage(applicationPackages, ElementMatchers.<NamedElement>none())
            .and(declaresMethod(getMethodMatcher()));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return isAnnotatedWith(
            namedOneOf("org.springframework.scheduling.annotation.Scheduled", "org.springframework.scheduling.annotation.Schedules",
                "javax.ejb.Schedule", "javax.ejb.Schedules", "jakarta.ejb.Schedule", "jakarta.ejb.Schedules"));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("concurrent", "scheduled");
    }

    @Override
    public String getAdviceClassName() {
        return getClass().getName() + "$ScheduledTransactionNameAdvice";
    }
}
