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

import co.elastic.apm.agent.awslambda.lambdas.AbstractFunction;
import co.elastic.apm.agent.awslambda.lambdas.ApiGatewayV1LambdaFunction;
import co.elastic.apm.agent.awslambda.lambdas.ApiGatewayV2LambdaFunction;
import co.elastic.apm.agent.awslambda.lambdas.TestContext;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.context.Response;
import co.elastic.apm.agent.impl.context.Url;
import co.elastic.apm.agent.impl.context.web.WebConfiguration;
import co.elastic.apm.agent.impl.transaction.Faas;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.util.PotentiallyMultiValuedMap;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import org.junit.BeforeClass;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class ApiGatewayV2LambdaTest extends AbstractLambdaTest<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    @BeforeAll
    @BeforeClass
    // Need to overwrite the beforeAll() method from parent,
    // because we need to mock serverlessConfiguration BEFORE instrumentation is initialized!
    public static synchronized void beforeAll() {
        AbstractLambdaTest.initAllButInstrumentation();
        Objects.requireNonNull(serverlessConfiguration);
        doReturn(ApiGatewayV2LambdaFunction.class.getName()).when(serverlessConfiguration).getAwsLambdaHandler();
        AbstractLambdaTest.initInstrumentation();
    }

    @Override
    protected APIGatewayV2HTTPEvent createInput() {
        return createInput(HTTP_METHOD, PATH, API_GATEWAY_RESOURCE_PATH, API_GATEWAY_STAGE);
    }

    protected APIGatewayV2HTTPEvent createInput(String httpMethod, String httpPath, String routeKey, String stage) {
        APIGatewayV2HTTPEvent.RequestContext.Http.HttpBuilder httpBuilder = APIGatewayV2HTTPEvent.RequestContext.Http.builder();
        httpBuilder.withPath(httpPath);
        httpBuilder.withMethod(httpMethod);
        httpBuilder.withProtocol("HTTP/1.1");

        APIGatewayV2HTTPEvent.RequestContext.RequestContextBuilder requestContextBuilder = APIGatewayV2HTTPEvent.RequestContext.builder();
        requestContextBuilder.withApiId(API_ID);
        requestContextBuilder.withRequestId(API_GATEWAY_REQUEST_ID);
        requestContextBuilder.withAccountId(API_GATEWAY_ACCOUNT_ID);
        requestContextBuilder.withStage(stage);
        requestContextBuilder.withRouteKey(routeKey);
        requestContextBuilder.withHttp(httpBuilder.build());
        requestContextBuilder.withDomainName(API_GATEWAY_HOST);

        return createRequestEvent(requestContextBuilder.build());
    }


    private APIGatewayV2HTTPEvent createRequestEvent(@Nullable APIGatewayV2HTTPEvent.RequestContext requestContext) {
        APIGatewayV2HTTPEvent.APIGatewayV2HTTPEventBuilder requestEventBuilder = APIGatewayV2HTTPEvent.builder();
        requestEventBuilder.withBody(BODY);
        requestEventBuilder.withHeaders(REQUEST_HEADERS);
        requestEventBuilder.withQueryStringParameters(Map.of(QUERY_PARAM_KEY, QUERY_PARAM_VALUE));
        requestEventBuilder.withRawQueryString(QUERY_PARAM_KEY + "=" + QUERY_PARAM_VALUE);
        requestEventBuilder.withRouteKey(API_GATEWAY_RESOURCE_PATH);
        requestEventBuilder.withRequestContext(requestContext);
        return requestEventBuilder.build();
    }

    @Test
    public void testBasicCall() {
        doReturn(CoreConfiguration.EventType.ALL).when(config.getConfig(CoreConfiguration.class)).getCaptureBody();
        getFunction().handleRequest(createInput(), context);

        reporter.awaitTransactionCount(1);
        reporter.awaitSpanCount(1);
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("child-span");
        assertThat(reporter.getFirstSpan().getTransaction()).isEqualTo(reporter.getFirstTransaction());
        Transaction transaction = reporter.getFirstTransaction();
        assertThat(transaction.getNameAsString()).isEqualTo(HTTP_METHOD + " /" + API_GATEWAY_STAGE + API_GATEWAY_RESOURCE_PATH);
        assertThat(transaction.getType()).isEqualTo("request");
        assertThat(transaction.getResult()).isEqualTo("HTTP 2xx");

        Request request = transaction.getContext().getRequest();
        assertThat(request.getMethod()).isEqualTo(HTTP_METHOD);
        assertThat(String.valueOf(request.getBody())).isEqualTo(BODY);
        assertThat(request.getHttpVersion()).isEqualTo("1.1");

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
        assertThat(transaction.getContext().getServiceOrigin().getName().toString()).isEqualTo(API_GATEWAY_HOST);
        assertThat(transaction.getContext().getServiceOrigin().getId()).isEqualTo(API_ID);
        assertThat(transaction.getContext().getServiceOrigin().getVersion()).isEqualTo("2.0");

        Faas faas = transaction.getFaas();
        assertThat(faas.getExecution()).isEqualTo(TestContext.AWS_REQUEST_ID);
        assertThat(faas.getId()).isEqualTo(TestContext.FUNCTION_ARN);
        assertThat(faas.getTrigger().getType()).isEqualTo("http");
        assertThat(faas.getTrigger().getRequestId()).isEqualTo(API_GATEWAY_REQUEST_ID);
    }

    @Test
    public void testCallWithNullInput() {
        getFunction().handleRequest(null, context);

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
        APIGatewayV2HTTPEvent.RequestContext requestContext = isObjectNull ? null : new APIGatewayV2HTTPEvent.RequestContext();
        APIGatewayV2HTTPEvent apiGatewayProxyRequestEvent = createRequestEvent(requestContext);
        getFunction().handleRequest(apiGatewayProxyRequestEvent, context);

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

    @Test
    public void testCallWithHErrorStatusCode() {
        Objects.requireNonNull(context).setErrorStatusCode();
        getFunction().handleRequest(createInput(), context);
        reporter.awaitTransactionCount(1);
        reporter.awaitSpanCount(1);
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("child-span");
        assertThat(reporter.getFirstSpan().getTransaction()).isEqualTo(reporter.getFirstTransaction());
        Transaction transaction = reporter.getFirstTransaction();
        assertThat(transaction.getResult()).isEqualTo("HTTP 5xx");
        assertThat(transaction.getOutcome()).isEqualTo(Outcome.FAILURE);
    }

    @Test
    public void testTransactionNameForRestApiSpecificRoute() {
        getFunction().handleRequest(createInput("PUT", "/prod/test", "ANY /test", "prod"), context);
        reporter.awaitTransactionCount(1);
        reporter.awaitSpanCount(1);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("PUT /prod/test");
    }

    @Test
    public void testTransactionNameForRestApiProxy() {
        getFunction().handleRequest(createInput("PUT", "/prod/proxy-test/12345", "$default", "prod"), context);
        reporter.awaitTransactionCount(1);
        reporter.awaitSpanCount(1);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("PUT /prod/$default");
    }

    @Test
    public void testTransactionNameWithUsePathAsName() {
        doReturn(true).when(config.getConfig(WebConfiguration.class)).isUsePathAsName();
        getFunction().handleRequest(createInput("PUT", "/prod/proxy-test/12345", "$default", "prod"), context);
        reporter.awaitTransactionCount(1);
        reporter.awaitSpanCount(1);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("PUT /prod/proxy-test/12345");
    }

    @Override
    protected AbstractFunction<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> createHandler() {
        return new ApiGatewayV2LambdaFunction();
    }
}
