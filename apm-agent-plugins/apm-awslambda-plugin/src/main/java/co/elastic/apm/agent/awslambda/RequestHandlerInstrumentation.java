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

import co.elastic.apm.agent.awslambda.helper.APIGatewayProxyV1TransactionHelper;
import co.elastic.apm.agent.awslambda.helper.APIGatewayProxyV2TransactionHelper;
import co.elastic.apm.agent.awslambda.helper.PlainTransactionHelper;
import co.elastic.apm.agent.awslambda.helper.S3TransactionHelper;
import co.elastic.apm.agent.awslambda.helper.SNSTransactionHelper;
import co.elastic.apm.agent.awslambda.helper.SQSTransactionHelper;
import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.configuration.ServerlessConfiguration;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class RequestHandlerInstrumentation extends TracerAwareInstrumentation {

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton("aws-lambda");
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return named(GlobalTracer.requireTracerImpl().getConfig(ServerlessConfiguration.class).getAwsLambdaHandler());
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(hasSuperType(named("com.amazonaws.services.lambda.runtime.RequestHandler")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return isPublic()
            .and(named("handleRequest"))
            .and(takesArgument(1, named("com.amazonaws.services.lambda.runtime.Context")));
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.awslambda.RequestHandlerInstrumentation$RequestHandlerAdvice";
    }

    public static class RequestHandlerAdvice {

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object handlerEnter(@Advice.Argument(value = 0) Object input, @Advice.Argument(value = 1) Context lambdaContext) {
            if (input instanceof APIGatewayV2HTTPEvent) {
                // API Gateway V2 trigger
                return APIGatewayProxyV2TransactionHelper.getInstance().startTransaction((APIGatewayV2HTTPEvent) input, lambdaContext);
            } else if (input instanceof APIGatewayProxyRequestEvent) {
                // API Gateway V1 trigger
                return APIGatewayProxyV1TransactionHelper.getInstance().startTransaction((APIGatewayProxyRequestEvent) input, lambdaContext);
            } else if (input instanceof SQSEvent) {
                // SQS trigger
                return SQSTransactionHelper.getInstance().startTransaction((SQSEvent) input, lambdaContext);
            } else if (input instanceof SNSEvent) {
                // SNS trigger
                return SNSTransactionHelper.getInstance().startTransaction((SNSEvent) input, lambdaContext);
            } else if (input instanceof S3Event) {
                // S3 event trigger
                return S3TransactionHelper.getInstance().startTransaction((S3Event) input, lambdaContext);
            }
            // Fallback instrumentation
            return PlainTransactionHelper.getInstance().startTransaction(input, lambdaContext);
        }

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false, onThrowable = Throwable.class)
        public static void handlerExit(@Nullable @Advice.Enter Object transactionObj,
                                       @Nullable @Advice.Thrown Throwable thrown,
                                       @Nullable @Advice.Return Object output) {
            if (transactionObj instanceof Transaction) {
                Transaction transaction = (Transaction) transactionObj;
                if (output instanceof APIGatewayV2HTTPResponse) {
                    APIGatewayProxyV2TransactionHelper.getInstance().finalizeTransaction(transaction, (APIGatewayV2HTTPResponse) output, thrown);
                } else if (output instanceof APIGatewayProxyResponseEvent) {
                    APIGatewayProxyV1TransactionHelper.getInstance().finalizeTransaction(transaction, (APIGatewayProxyResponseEvent) output, thrown);
                } else {
                    // use PlainTransactionHelper for all triggers that do not expect an output
                    PlainTransactionHelper.getInstance().finalizeTransaction(transaction, output, thrown);
                }
            }
        }
    }
}
