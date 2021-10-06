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
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import javax.annotation.Nullable;
import java.util.Map;

public class APIGatewayProxyV1TransactionHelper extends AbstractAPIGatewayTransactionHelper<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Nullable
    private static APIGatewayProxyV1TransactionHelper INSTANCE;

    public APIGatewayProxyV1TransactionHelper(ElasticApmTracer tracer) {
        super(tracer);
    }

    public static APIGatewayProxyV1TransactionHelper getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new APIGatewayProxyV1TransactionHelper(GlobalTracer.requireTracerImpl());
        }
        return INSTANCE;
    }

    @Override
    protected Transaction doStartTransaction(APIGatewayProxyRequestEvent apiGatewayEvent, Context lambdaContext) {
        Transaction transaction = tracer.startChildTransaction(apiGatewayEvent.getHeaders(), MapTextHeaderGetter.INSTANCE, apiGatewayEvent.getClass().getClassLoader());
        String host = null;
        if (null != apiGatewayEvent.getHeaders()) {
            host = apiGatewayEvent.getHeaders().get("host");
            if(null == host){
                host = apiGatewayEvent.getHeaders().get("Host");
            }
        }

        if (null != transaction) {
            fillHttpRequestData(transaction, apiGatewayEvent.getHttpMethod(), apiGatewayEvent.getHeaders(), host,
                    apiGatewayEvent.getPath(), getQueryString(apiGatewayEvent), apiGatewayEvent.getBody());
        }

        return transaction;
    }

    private String getQueryString(APIGatewayProxyRequestEvent apiGatewayEvent) {
        StringBuilder queryString = new StringBuilder();
        Map<String, String> queryParameters = apiGatewayEvent.getQueryStringParameters();
        if (null != queryParameters) {
            int i = 0;
            for (Map.Entry<String, String> entry : apiGatewayEvent.getQueryStringParameters().entrySet()) {
                if (i > 0) {
                    queryString.append('&');
                }
                queryString.append(entry.getKey());
                queryString.append('=');
                queryString.append(entry.getValue());
                i++;
            }
        }
        return queryString.toString();
    }

    @Override
    public void captureOutputForTransaction(Transaction transaction, APIGatewayProxyResponseEvent responseEvent) {
        fillHttpResponseData(transaction, responseEvent.getHeaders(), responseEvent.getStatusCode());
    }

    @Override
    protected void setTransactionTriggerData(Transaction transaction, APIGatewayProxyRequestEvent apiGatewayRequest) {
        super.setTransactionTriggerData(transaction,apiGatewayRequest);
        APIGatewayProxyRequestEvent.ProxyRequestContext rContext = apiGatewayRequest.getRequestContext();

        if (null != rContext) {
            setApiGatewayContextData(transaction, rContext.getRequestId(), rContext.getApiId(), rContext.getHttpMethod(),
                    rContext.getResourcePath(), rContext.getStage(), rContext.getAccountId());
        }
    }

    @Override
    protected String getHttpMethod(APIGatewayProxyRequestEvent event) {
        return event.getHttpMethod();
    }

    @Override
    protected String getApiGatewayVersion() {
        return "1.0";
    }
}
