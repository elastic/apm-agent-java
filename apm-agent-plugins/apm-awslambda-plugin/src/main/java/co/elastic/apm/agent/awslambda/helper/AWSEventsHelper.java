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
package co.elastic.apm.agent.awslambda.helper;

import co.elastic.apm.agent.impl.transaction.Transaction;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;

import javax.annotation.Nullable;

public class AWSEventsHelper {

    @Nullable
    public static Transaction startTransaction(Object input, Context lambdaContext) {
        if (input instanceof APIGatewayV2HTTPEvent && ((APIGatewayV2HTTPEvent) input).getRequestContext() != null
            && ((APIGatewayV2HTTPEvent) input).getRequestContext().getHttp() != null) {
            // API Gateway V2 trigger
            return APIGatewayProxyV2TransactionHelper.getInstance().startTransaction((APIGatewayV2HTTPEvent) input, lambdaContext);
        } else if (input instanceof APIGatewayProxyRequestEvent && ((APIGatewayProxyRequestEvent) input).getRequestContext() != null) {
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
        return PlainTransactionHelper.getInstance().startTransaction(input, lambdaContext);
    }

    public static void finalizeTransaction(Transaction transaction, Object output, @Nullable Throwable thrown) {
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
