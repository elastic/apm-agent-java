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

import co.elastic.apm.agent.bci.bytebuddy.SimpleMethodSignatureOffsetMappingFactory;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.tracer.Tracer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.quartz.JobExecutionContext;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class Quartz1JobTransactionNameInstrumentation extends AbstractJobTransactionNameInstrumentation {
    public Quartz1JobTransactionNameInstrumentation(Tracer tracer) {
        super(tracer);
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("execute").or(named("executeInternal"))
            .and(takesArgument(0, named("org.quartz.JobExecutionContext").and(not(isInterface()))));
    }

    public static class AdviceClass extends BaseAdvice {
        private static final JobExecutionContextHandler<JobExecutionContext> helper;

        static {
            helper = new Quartz1JobExecutionContextHandler();
        }

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object setTransactionName(@Advice.Argument(value = 0) @Nullable JobExecutionContext jobExecutionContext,
                                                @SimpleMethodSignatureOffsetMappingFactory.SimpleMethodSignature String signature,
                                                @Advice.Origin Class<?> clazz) {
            return BaseAdvice.createAndActivateTransaction(jobExecutionContext, signature, clazz, helper);
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onMethodExitException(@Advice.Argument(value = 0) @Nullable JobExecutionContext jobExecutionContext,
                                                 @Advice.Enter @Nullable Object transactionObj,
                                                 @Advice.Thrown @Nullable Throwable t) {
            BaseAdvice.endTransaction(jobExecutionContext, transactionObj, t, helper);
        }
    }
}
