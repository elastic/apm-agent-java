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

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.CloudOrigin;
import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.context.Response;
import co.elastic.apm.agent.impl.context.ServiceOrigin;
import co.elastic.apm.agent.impl.context.web.ResultUtil;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.tracer.AbstractSpan;
import com.amazonaws.services.lambda.runtime.Context;

import javax.annotation.Nullable;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static co.elastic.apm.agent.configuration.CoreConfiguration.EventType.OFF;

public abstract class AbstractAPIGatewayTransactionHelper<I, O> extends AbstractLambdaTransactionHelper<I, O> {
    private static final Logger logger = LoggerFactory.getLogger(AbstractAPIGatewayTransactionHelper.class);
    protected static final String TRANSACTION_TYPE = "request";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final Set<String> METHODS_WITH_BODY = new HashSet<>(Arrays.asList("POST", "PUT", "PATCH", "DELETE"));
    private static final String CONTENT_TYPE_FROM_URLENCODED = "application/x-www-form-urlencoded";

    protected AbstractAPIGatewayTransactionHelper(ElasticApmTracer tracer) {
        super(tracer);
    }

    protected abstract String getApiGatewayVersion();

    protected void fillHttpRequestData(Transaction transaction, @Nullable String httpMethod, @Nullable Map<String, String> headers, @Nullable String serverName, @Nullable String path, @Nullable String queryString, @Nullable String body) {
        Request request = transaction.getContext().getRequest();
        request.withMethod(httpMethod);
        fillUrlRelatedFields(request, serverName, path, queryString);
        if (null != headers) {
            String contentType = headers.get(CONTENT_TYPE_HEADER);
            setRequestHeaders(transaction, headers);
            CharBuffer bodyBuffer = startCaptureBody(transaction, httpMethod, contentType);
            if (bodyBuffer != null) {
                bodyBuffer.append(body);
            }
        }
    }

    protected void fillHttpResponseData(Transaction transaction, @Nullable Map<String, String> headers, int statusCode) {
        Response response = transaction.getContext().getResponse();
        response.withFinished(true);
        if (transaction.isSampled() && null != headers && isCaptureHeaders()) {
            for (Map.Entry<String, String> headerEntry : headers.entrySet()) {
                response.addHeader(headerEntry.getKey(), headerEntry.getValue());
            }
        }

        response.withStatusCode(statusCode);
        transaction.withResultIfUnset(ResultUtil.getResultByHttpStatus(statusCode));
    }

    private void fillUrlRelatedFields(Request request, @Nullable String serverName, @Nullable String path, @Nullable String queryString) {
        String qString = queryString == null || queryString.trim().isEmpty() ? null: queryString;
        request.getUrl().resetState();
        request.getUrl()
            .withProtocol("https")
            .withHostname(serverName)
            .withPort(443)
            .withPathname(path)
            .withSearch(qString);
    }

    @Nullable
    private CharBuffer startCaptureBody(Transaction transaction, @Nullable String method, @Nullable String contentTypeHeader) {
        Request request = transaction.getContext().getRequest();
        if (hasBody(contentTypeHeader, method)) {
            if (coreConfiguration.getCaptureBody() != OFF
                && contentTypeHeader != null
                // form parameters are recorded via ServletRequest.getParameterMap
                // as the container might not call ServletRequest.getInputStream
                && !contentTypeHeader.startsWith(CONTENT_TYPE_FROM_URLENCODED)
                && WildcardMatcher.isAnyMatch(webConfiguration.getCaptureContentTypes(), contentTypeHeader)) {
                return request.withBodyBuffer();
            } else {
                request.redactBody();
                if (coreConfiguration.getCaptureBody() == OFF) {
                    logger.debug("Not capturing Request body because the capture_body config option is OFF");
                }
                if (contentTypeHeader == null) {
                    logger.debug("Not capturing request body because couldn't find Content-Type header");
                } else if (!contentTypeHeader.startsWith(CONTENT_TYPE_FROM_URLENCODED)) {
                    logger.debug("Not capturing body for content type \"{}\". Consider updating the capture_body_content_types " +
                        "configuration option.", contentTypeHeader);
                }
            }
        }
        return null;
    }

    private boolean isCaptureHeaders() {
        return coreConfiguration.isCaptureHeaders();
    }

    private boolean hasBody(@Nullable String contentTypeHeader, @Nullable String method) {
        return method != null && METHODS_WITH_BODY.contains(method) && contentTypeHeader != null;
    }

    @Override
    protected void setTransactionTriggerData(Transaction transaction, I apiGatewayRequest) {
        transaction.withType(TRANSACTION_TYPE);
        CloudOrigin cloudOrigin = transaction.getContext().getCloudOrigin();
        cloudOrigin.withServiceName("api gateway");
        cloudOrigin.withProvider("aws");
        transaction.getFaas().getTrigger().withType("http");
        transaction.getContext().getServiceOrigin().withVersion(getApiGatewayVersion());
    }

    protected void setApiGatewayContextData(Transaction transaction, @Nullable String requestId, @Nullable String apiId,
                                            @Nullable String domainName, @Nullable String accountId) {
        transaction.getFaas().getTrigger().withRequestId(requestId);
        ServiceOrigin serviceOrigin = transaction.getContext().getServiceOrigin();
        serviceOrigin.withName(domainName);
        serviceOrigin.withId(apiId);

        transaction.getContext().getCloudOrigin().withAccountId(accountId);
    }

    private void setRequestHeaders(Transaction transaction, Map<String, String> headers) {
        final Request req = transaction.getContext().getRequest();
        if (transaction.isSampled() && isCaptureHeaders()) {
            for (Map.Entry<String, String> headerEntry : headers.entrySet()) {
                req.addHeader(headerEntry.getKey(), headerEntry.getValue());
            }
        }
    }

    @Override
    protected void setTransactionName(Transaction transaction, I event, Context lambdaContext) {
        StringBuilder transactionName = transaction.getAndOverrideName(AbstractSpan.PRIORITY_HIGH_LEVEL_FRAMEWORK);
        if (transactionName != null) {
            String httpMethod = getHttpMethod(event);
            String requestContextPath = getRequestContextPath(event);
            String resourcePath = getResourcePath(event);
            if (httpMethod != null) {
                transactionName.append(httpMethod).append(" ");
            }
            if (webConfiguration.isUsePathAsName() && requestContextPath != null) {
                transactionName.append(requestContextPath);
            } else if (resourcePath != null) {
                String stage = getStage(event);
                if (stage != null) {
                    transactionName.append('/').append(stage);
                }
                if (!resourcePath.startsWith("/")) {
                    transactionName.append('/');
                }
                transactionName.append(resourcePath);
            } else {
                // HTTP method may have been appended already to the name buffer, but calling the super's implementation that relies on
                // the lambda function name would reset the buffer before appending it
                super.setTransactionName(transaction, event, lambdaContext);
            }
        }
    }

    @Nullable
    protected abstract String getHttpMethod(I event);

    @Nullable
    protected abstract String getRequestContextPath(I event);

    @Nullable
    protected abstract String getStage(I event);

    @Nullable
    protected abstract String getResourcePath(I event);

}
