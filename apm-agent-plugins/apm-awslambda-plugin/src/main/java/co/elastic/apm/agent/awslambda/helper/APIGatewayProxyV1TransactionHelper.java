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
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.util.PrivilegedActionUtils;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import javax.annotation.Nullable;
import java.util.Map;

public class APIGatewayProxyV1TransactionHelper extends AbstractAPIGatewayTransactionHelper<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Nullable
    private static APIGatewayProxyV1TransactionHelper INSTANCE;

    private APIGatewayProxyV1TransactionHelper(ElasticApmTracer tracer) {
        super(tracer);
    }

    public static APIGatewayProxyV1TransactionHelper getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new APIGatewayProxyV1TransactionHelper(GlobalTracer.get().require(ElasticApmTracer.class));
        }
        return INSTANCE;
    }

    @Override
    protected Transaction doStartTransaction(APIGatewayProxyRequestEvent apiGatewayEvent, Context lambdaContext) {
        Transaction transaction = tracer.startChildTransaction(apiGatewayEvent.getHeaders(), MapTextHeaderGetter.INSTANCE, PrivilegedActionUtils.getClassLoader(apiGatewayEvent.getClass()));
        String host = getHost(apiGatewayEvent);

        if (null != transaction) {
            fillHttpRequestData(transaction, getHttpMethod(apiGatewayEvent), apiGatewayEvent.getHeaders(), host,
                apiGatewayEvent.getRequestContext().getPath(), getQueryString(apiGatewayEvent), apiGatewayEvent.getBody());
        }

        return transaction;
    }

    @Nullable
    private String getHost(APIGatewayProxyRequestEvent apiGatewayEvent) {
        String host = null;
        if (null != apiGatewayEvent.getHeaders()) {
            host = apiGatewayEvent.getHeaders().get("host");
            if (null == host) {
                host = apiGatewayEvent.getHeaders().get("Host");
            }
        }
        return host;
    }

    @Nullable
    private String getQueryString(APIGatewayProxyRequestEvent apiGatewayEvent) {
        Map<String, String> queryParameters = apiGatewayEvent.getQueryStringParameters();
        if (null != queryParameters && !queryParameters.isEmpty()) {
            StringBuilder queryString = new StringBuilder();
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
            return queryString.toString();
        }
        return null;
    }

    @Override
    public void captureOutputForTransaction(Transaction transaction, APIGatewayProxyResponseEvent responseEvent) {
        Integer statusCode = responseEvent.getStatusCode();
        if (statusCode == null) {
            statusCode = 0;
        }
        fillHttpResponseData(transaction, responseEvent.getHeaders(), statusCode);
    }

    @Override
    protected void setTransactionTriggerData(Transaction transaction, APIGatewayProxyRequestEvent apiGatewayRequest) {
        super.setTransactionTriggerData(transaction, apiGatewayRequest);
        APIGatewayProxyRequestEvent.ProxyRequestContext rContext = apiGatewayRequest.getRequestContext();

        if (null != rContext) {
            setApiGatewayContextData(transaction, rContext.getRequestId(), rContext.getApiId(),
                getHost(apiGatewayRequest), rContext.getAccountId());
        }
    }

    @Override
    protected String getApiGatewayVersion() {
        return "1.0";
    }

    @Nullable
    @Override
    protected String getHttpMethod(APIGatewayProxyRequestEvent event) {
        String httpMethod = event.getRequestContext().getHttpMethod();
        return (httpMethod == null) ? "GET" : httpMethod;
    }

    @Nullable
    @Override
    protected String getRequestContextPath(APIGatewayProxyRequestEvent event) {
        return event.getRequestContext().getPath();
    }

    @Nullable
    @Override
    protected String getStage(APIGatewayProxyRequestEvent event) {
        return event.getRequestContext().getStage();
    }

    @Nullable
    @Override
    protected String getResourcePath(APIGatewayProxyRequestEvent event) {
        return event.getRequestContext().getResourcePath();
    }
}
