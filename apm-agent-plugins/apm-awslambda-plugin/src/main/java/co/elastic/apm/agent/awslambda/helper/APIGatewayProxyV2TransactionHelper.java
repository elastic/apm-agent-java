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

import co.elastic.apm.agent.awslambda.MapTextHeaderGetter;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;

import javax.annotation.Nullable;

public class APIGatewayProxyV2TransactionHelper extends AbstractAPIGatewayTransactionHelper<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    @Nullable
    private static APIGatewayProxyV2TransactionHelper INSTANCE;

    private APIGatewayProxyV2TransactionHelper(ElasticApmTracer tracer) {
        super(tracer);
    }

    public static APIGatewayProxyV2TransactionHelper getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new APIGatewayProxyV2TransactionHelper(GlobalTracer.requireTracerImpl());
        }
        return INSTANCE;
    }

    @Override
    protected Transaction doStartTransaction(APIGatewayV2HTTPEvent apiGatewayEvent, Context lambdaContext) {
        Transaction transaction = tracer.startChildTransaction(apiGatewayEvent.getHeaders(), MapTextHeaderGetter.INSTANCE, apiGatewayEvent.getClass().getClassLoader());

        APIGatewayV2HTTPEvent.RequestContext requestContext = apiGatewayEvent.getRequestContext();
        if (transaction != null && null != requestContext) {
            APIGatewayV2HTTPEvent.RequestContext.Http http = requestContext.getHttp();
            if (null != http) {
                fillHttpRequestData(transaction, http.getMethod(), apiGatewayEvent.getHeaders(), requestContext.getDomainName(),
                        http.getPath(), apiGatewayEvent.getRawQueryString(), apiGatewayEvent.getBody());
                transaction.getContext().getRequest().withHttpVersion(getHttpVersion(http.getProtocol()));
            }
        }

        return transaction;
    }

    @Override
    public void captureOutputForTransaction(Transaction transaction, APIGatewayV2HTTPResponse responseEvent) {
        fillHttpResponseData(transaction, responseEvent.getHeaders(), responseEvent.getStatusCode());
    }

    @Override
    protected void setTransactionTriggerData(Transaction transaction, APIGatewayV2HTTPEvent apiGatewayRequest) {
        super.setTransactionTriggerData(transaction,apiGatewayRequest);
        APIGatewayV2HTTPEvent.RequestContext rContext = apiGatewayRequest.getRequestContext();

        if (null != rContext) {
            String httpMethod = null != rContext.getHttp() ? rContext.getHttp().getMethod() : null;
            setApiGatewayContextData(transaction, rContext.getRequestId(), rContext.getApiId(), httpMethod,
                    apiGatewayRequest.getRouteKey(), rContext.getAccountId());
        }
    }

    @Override
    protected String getHttpMethod(APIGatewayV2HTTPEvent event) {
        if(null != event.getRequestContext() && null != event.getRequestContext().getHttp()){
            return event.getRequestContext().getHttp().getMethod();
        }
        return null;
    }

    @Override
    protected String getApiGatewayVersion() {
        return "2.0";
    }
}
