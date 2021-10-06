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

import co.elastic.apm.agent.awslambda.lambdas.ApiGatewayV1LambdaFunction;
import co.elastic.apm.agent.awslambda.lambdas.TestContext;
import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.context.Response;
import co.elastic.apm.agent.impl.context.Url;
import co.elastic.apm.agent.impl.transaction.Faas;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.util.PotentiallyMultiValuedMap;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import org.junit.BeforeClass;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.annotation.Nullable;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class ApiGatewayV1LambdaTest extends AbstractLambdaTest {

    protected ApiGatewayV1LambdaFunction function = new ApiGatewayV1LambdaFunction();

    @BeforeAll
    @BeforeClass
    // Need to overwrite the beforeAll() method from parent,
    // because we need to mock serverlessConfiguration BEFORE instrumentation is initialized!
    public static synchronized void beforeAll() {
        AbstractLambdaTest.initAllButInstrumentation();
        when(serverlessConfiguration.getAwsLambdaHandler()).thenReturn(ApiGatewayV1LambdaFunction.class.getName());
        AbstractLambdaTest.initInstrumentation();
    }

    public APIGatewayProxyRequestEvent createDefaultRequestEvent() {
        APIGatewayProxyRequestEvent.ProxyRequestContext requestContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        requestContext.withHttpMethod(HTTP_METHOD);
        requestContext.withPath(PATH);
        requestContext.withApiId(API_ID);
        requestContext.withRequestId(API_GATEWAY_REQUEST_ID);
        requestContext.withAccountId(API_GATEWAY_ACCOUNT_ID);
        requestContext.withOperationName(API_GATEWAY_OPERATION_NAME);
        requestContext.withResourcePath(API_GATEWAY_RESOURCE_PATH);
        requestContext.withStage(API_GATEWAY_STAGE);

        return createRequestEvent(requestContext);
    }

    private APIGatewayProxyRequestEvent createRequestEvent(@Nullable APIGatewayProxyRequestEvent.ProxyRequestContext requestContext) {
        APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent = new APIGatewayProxyRequestEvent();
        apiGatewayProxyRequestEvent.withHttpMethod(HTTP_METHOD);
        apiGatewayProxyRequestEvent.withBody(BODY);
        apiGatewayProxyRequestEvent.withHeaders(REQUEST_HEADERS);
        apiGatewayProxyRequestEvent.withPath(PATH);
        apiGatewayProxyRequestEvent.withQueryStringParameters(Map.of(QUERY_PARAM_KEY, QUERY_PARAM_VALUE));
        apiGatewayProxyRequestEvent.withRequestContext(requestContext);
        return apiGatewayProxyRequestEvent;
    }

    @Test
    public void testBasicCall() {
        function.handleRequest(createDefaultRequestEvent(), context);
        reporter.awaitTransactionCount(1);
        reporter.awaitSpanCount(1);
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("child-span");
        assertThat(reporter.getFirstSpan().getTransaction()).isEqualTo(reporter.getFirstTransaction());
        Transaction transaction = reporter.getFirstTransaction();
        assertThat(transaction.getNameAsString()).isEqualTo(HTTP_METHOD + " " + TestContext.FUNCTION_NAME);
        assertThat(transaction.getType()).isEqualTo("request");
        assertThat(transaction.getResult()).isEqualTo("HTTP 2xx");

        Request request = transaction.getContext().getRequest();
        assertThat(request.getMethod()).isEqualTo(HTTP_METHOD);
        assertThat(request.getBody()).isNull();
        assertThat(request.getHttpVersion()).isNull();

        Url url = request.getUrl();
        assertThat(url.getHostname()).isEqualTo(API_GATEWAY_HOST);
        assertThat(url.getPort()).isEqualTo(443);
        assertThat(url.getPathname()).isEqualTo(PATH);
        assertThat(url.getSearch()).isEqualTo(QUERY_PARAM_KEY + "=" + QUERY_PARAM_VALUE);
        assertThat(url.getProtocol()).isEqualTo("https");
        assertThat(url.getFull().toString()).isEqualTo("https://" + API_GATEWAY_HOST + PATH + "?" + QUERY_PARAM_KEY + "=" + QUERY_PARAM_VALUE);

        assertThat(request.getHeaders()).isNotNull();
        PotentiallyMultiValuedMap headers = request.getHeaders();
        assertThat(headers.get(HEADER_1_KEY)).isEqualTo(HEADER_1_VALUE);
        assertThat(headers.get(HEADER_2_KEY)).isEqualTo(HEADER_2_VALUE);

        Response response = transaction.getContext().getResponse();
        assertThat(response.getStatusCode()).isEqualTo(ApiGatewayV1LambdaFunction.EXPECTED_STATUS_CODE);
        assertThat(response.getHeaders()).isNotNull();
        assertThat(response.getHeaders().get(ApiGatewayV1LambdaFunction.EXPECTED_RESPONSE_HEADER_1_KEY)).isEqualTo(ApiGatewayV1LambdaFunction.EXPECTED_RESPONSE_HEADER_1_VALUE);
        assertThat(response.getHeaders().get(ApiGatewayV1LambdaFunction.EXPECTED_RESPONSE_HEADER_2_KEY)).isEqualTo(ApiGatewayV1LambdaFunction.EXPECTED_RESPONSE_HEADER_2_VALUE);

        assertThat(transaction.getContext().getCloudOrigin()).isNotNull();
        assertThat(transaction.getContext().getCloudOrigin().getProvider()).isEqualTo("aws");
        assertThat(transaction.getContext().getCloudOrigin().getServiceName()).isEqualTo("api gateway");
        assertThat(transaction.getContext().getCloudOrigin().getAccountId()).isEqualTo(API_GATEWAY_ACCOUNT_ID);

        assertThat(transaction.getContext().getServiceOrigin().hasContent()).isTrue();
        assertThat(transaction.getContext().getServiceOrigin().getName()).isEqualTo(HTTP_METHOD + " " + API_GATEWAY_RESOURCE_PATH + "/" + API_GATEWAY_STAGE);
        assertThat(transaction.getContext().getServiceOrigin().getId()).isEqualTo(API_ID);
        assertThat(transaction.getContext().getServiceOrigin().getVersion()).isEqualTo("1.0");

        Faas faas = transaction.getFaas();
        assertThat(faas.getExecution()).isEqualTo(TestContext.AWS_REQUEST_ID);

        assertThat(faas.getTrigger().getType()).isEqualTo("http");
        assertThat(faas.getTrigger().getRequestId()).isEqualTo(API_GATEWAY_REQUEST_ID);
    }

    @Test
    public void testCallWithNullInput() {
        function.handleRequest(null, context);

        reporter.awaitTransactionCount(1);
        reporter.awaitSpanCount(1);
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("child-span");
        assertThat(reporter.getFirstSpan().getTransaction()).isEqualTo(reporter.getFirstTransaction());
        Transaction transaction = reporter.getFirstTransaction();
        assertThat(transaction.getNameAsString()).isEqualTo(TestContext.FUNCTION_NAME);
        assertThat(transaction.getType()).isEqualTo("request");
        assertThat(transaction.getResult()).isEqualTo("HTTP 2xx");

        assertThat(transaction.getContext().getCloudOrigin()).isNotNull();
        assertThat(transaction.getContext().getCloudOrigin().getProvider()).isEqualTo("aws");
        assertThat(transaction.getContext().getCloudOrigin().getServiceName()).isNull();
        assertThat(transaction.getContext().getCloudOrigin().getRegion()).isNull();
        assertThat(transaction.getContext().getCloudOrigin().getAccountId()).isNull();

        assertThat(transaction.getContext().getServiceOrigin().hasContent()).isFalse();

        Faas faas = transaction.getFaas();
        assertThat(faas.getExecution()).isEqualTo(TestContext.AWS_REQUEST_ID);

        assertThat(faas.getTrigger().getType()).isEqualTo("other");
        assertThat(faas.getTrigger().getRequestId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testCallWithNullRequestContext(boolean isObjectNull) {
        APIGatewayProxyRequestEvent.ProxyRequestContext requestContext = isObjectNull ? null : new APIGatewayProxyRequestEvent.ProxyRequestContext();
        APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent = createRequestEvent(requestContext);
        function.handleRequest(apiGatewayProxyRequestEvent, context);

        reporter.awaitTransactionCount(1);
        reporter.awaitSpanCount(1);
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("child-span");
        assertThat(reporter.getFirstSpan().getTransaction()).isEqualTo(reporter.getFirstTransaction());
        Transaction transaction = reporter.getFirstTransaction();
        assertThat(transaction.getNameAsString()).isEqualTo(HTTP_METHOD + " " + TestContext.FUNCTION_NAME);
        assertThat(transaction.getType()).isEqualTo("request");
        assertThat(transaction.getResult()).isEqualTo("HTTP 2xx");

        assertThat(transaction.getContext().getCloudOrigin()).isNotNull();
        assertThat(transaction.getContext().getCloudOrigin().getProvider()).isEqualTo("aws");
        assertThat(transaction.getContext().getCloudOrigin().getServiceName()).isEqualTo("api gateway");
        assertThat(transaction.getContext().getCloudOrigin().getRegion()).isNull();
        assertThat(transaction.getContext().getCloudOrigin().getAccountId()).isNull();

        assertThat(transaction.getContext().getServiceOrigin().hasContent()).isTrue();
        assertThat(transaction.getContext().getServiceOrigin().getVersion()).isEqualTo("1.0");

        Faas faas = transaction.getFaas();
        assertThat(faas.getExecution()).isEqualTo(TestContext.AWS_REQUEST_ID);

        assertThat(faas.getTrigger().getType()).isEqualTo("http");
        assertThat(faas.getTrigger().getRequestId()).isNull();
    }
}
