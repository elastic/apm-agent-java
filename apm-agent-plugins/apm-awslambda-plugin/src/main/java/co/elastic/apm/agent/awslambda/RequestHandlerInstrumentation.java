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
package co.elastic.apm.agent.awslambda;

import co.elastic.apm.agent.awslambda.helper.AWSEventsHelper;
import co.elastic.apm.agent.awslambda.helper.PlainTransactionHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.amazonaws.services.lambda.runtime.Context;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class RequestHandlerInstrumentation extends AbstractAwsLambdaHandlerInstrumentation {

    public RequestHandlerInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        String matchMethod = (handlerMethodName != null) ? handlerMethodName : "handleRequest";
        return isPublic()
            .and(named(matchMethod))
            .and(takesArgument(1, named("com.amazonaws.services.lambda.runtime.Context")));
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.awslambda.RequestHandlerInstrumentation$RequestHandlerAdvice";
    }

    public static class RequestHandlerAdvice {

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object handlerEnter(@Nullable @Advice.Argument(value = 0) Object input, @Advice.Argument(value = 1) Context lambdaContext) {
            if (input != null && input.getClass().getName().startsWith("com.amazonaws.services.lambda.runtime.events")) {
                // handler uses aws events, it's safe to assume that the AWS events classes are available
                return AWSEventsHelper.startTransaction(input, lambdaContext);
            } else {
                // Fallback instrumentation (AWS events classes might be not available)
                return PlainTransactionHelper.getInstance().startTransaction(input, lambdaContext);
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false, onThrowable = Throwable.class)
        public static void handlerExit(@Nullable @Advice.Enter Object transactionObj,
                                       @Nullable @Advice.Thrown Throwable thrown,
                                       @Nullable @Advice.Return Object output) {
            if (transactionObj instanceof Transaction) {
                Transaction transaction = (Transaction) transactionObj;

                if (output != null && output.getClass().getName().startsWith("com.amazonaws.services.lambda.runtime.events")) {
                    // handler uses aws events, it's save to assume that the AWS events classes are available
                    AWSEventsHelper.finalizeTransaction(transaction, output, thrown);
                } else {
                    // Fallback instrumentation (AWS events classes might be not available)
                    PlainTransactionHelper.getInstance().finalizeTransaction(transaction, output, thrown);
                }
            }
        }
    }
}
