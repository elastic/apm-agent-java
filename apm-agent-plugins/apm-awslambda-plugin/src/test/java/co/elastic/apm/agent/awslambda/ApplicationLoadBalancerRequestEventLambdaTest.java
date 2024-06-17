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
import co.elastic.apm.agent.awslambda.lambdas.ApplicationLoadBalancerRequestLambdaFunction;
import co.elastic.apm.agent.awslambda.lambdas.TestContext;
import co.elastic.apm.agent.configuration.CoreConfigurationImpl;
import co.elastic.apm.agent.impl.context.RequestImpl;
import co.elastic.apm.agent.impl.context.ResponseImpl;
import co.elastic.apm.agent.impl.context.UrlImpl;
import co.elastic.apm.agent.impl.transaction.FaasImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.configuration.WebConfiguration;
import co.elastic.apm.agent.tracer.metadata.PotentiallyMultiValuedMap;
import com.amazonaws.services.lambda.runtime.events.ApplicationLoadBalancerRequestEvent;
import com.amazonaws.services.lambda.runtime.events.ApplicationLoadBalancerResponseEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class ApplicationLoadBalancerRequestEventLambdaTest extends BaseGatewayLambdaTest<ApplicationLoadBalancerRequestEvent, ApplicationLoadBalancerResponseEvent> {

    @BeforeAll
    // Need to overwrite the beforeAll() method from parent,
    // because we need to mock serverlessConfiguration BEFORE instrumentation is initialized!
    public static synchronized void beforeAll() {
        AbstractLambdaTest.initAllButInstrumentation();
        doReturn(ApplicationLoadBalancerRequestLambdaFunction.class.getName()).when(Objects.requireNonNull(serverlessConfiguration)).getAwsLambdaHandler();
        AbstractLambdaTest.initInstrumentation();
    }

    @Override
    protected AbstractFunction<ApplicationLoadBalancerRequestEvent, ApplicationLoadBalancerResponseEvent> createHandler() {
        return new ApplicationLoadBalancerRequestLambdaFunction();
    }

    @Override
    protected ApplicationLoadBalancerRequestEvent createInput() {
        var event = new ApplicationLoadBalancerRequestEvent();
        event.setBody("blablablabody");
        event.setIsBase64Encoded(false);
        var requestContext = new ApplicationLoadBalancerRequestEvent.RequestContext();
        var elb = new ApplicationLoadBalancerRequestEvent.Elb();
        elb.setTargetGroupArn("arn:aws:elasticloadbalancing:us-east-2:123456789012:targetgroup/lambda-279XGJDqGZ5rsrHC2Fjr/49e9d65c45c6791a");
        requestContext.setElb(elb);
        event.setRequestContext(requestContext);
        event.setHttpMethod("POST");
        event.setPath("/toolz/api/v2.0/downloadPDF/PDF_2020-09-11_11-06-01.pdf");
        event.setQueryStringParameters(Map.of("test%40key", "test%40value", "language", "en-DE"));
        event.setHeaders(Map.of("accept-encoding", "gzip,deflate",
            "connection", "Keep-Alive",
            "host", "blabla.com",
            "user-agent", "Apache-HttpClient/4.5.13 (Java/11.0.15)",
            "x-amzn-trace-id", "Root=1-xxxxxxxxxxxxxx",
            "x-forwarded-for", "199.99.99.999",
            "x-forwarded-port", "443",
            "x-forwarded-proto", "https"));
        return event;
    }

    @Override
    protected boolean supportsContextPropagation() {
        return false;
    }

    @Test
    public void testBasicCall() {
        doReturn(CoreConfigurationImpl.EventType.ALL).when(config.getConfig(CoreConfigurationImpl.class)).getCaptureBody();
        getFunction().handleRequest(createInput(), context);
        reporter.awaitTransactionCount(1);
        reporter.awaitSpanCount(1);
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("child-span");
        assertThat(reporter.getFirstSpan().getTransaction()).isEqualTo(reporter.getFirstTransaction());
        TransactionImpl transaction = reporter.getFirstTransaction();
        assertThat(transaction.getNameAsString()).isEqualTo("FUNCTION_NAME");
        assertThat(transaction.getType()).isEqualTo("request");
        assertThat(transaction.getResult()).isEqualTo("HTTP 2xx");
        assertThat(transaction.getOutcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(reporter.getPartialTransactions()).containsExactly(transaction);

        RequestImpl request = transaction.getContext().getRequest();
        assertThat(request.getMethod()).isEqualTo(HTTP_METHOD);
        assertThat(request.getBody()).isNull();
        assertThat(request.getHttpVersion()).isNull();

        UrlImpl url = request.getUrl();
        assertThat(url.getHostname()).isEqualTo("blabla.com");
        assertThat(url.getPort()).isEqualTo(443);
        assertThat(url.getPathname()).isEqualTo("/toolz/api/v2.0/downloadPDF/PDF_2020-09-11_11-06-01.pdf");
        assertThat(url.getSearch()).contains("test%40key=test%40value");
        assertThat(url.getSearch()).contains(Arrays.asList("language=en-DE", "test%40key=test%40value"));
        assertThat(url.getProtocol()).isEqualTo("https");
        String baseUrl = "https://" + "blabla.com" + "/toolz/api/v2.0/downloadPDF/PDF_2020-09-11_11-06-01.pdf" + "?";
        assertThat(url.getFull().toString()).containsAnyOf(baseUrl + "test%40key=test%40value&language=en-DE",
            baseUrl + "language=en-DE&test%40key=test%40value");

        assertThat(request.getHeaders()).isNotNull();
        PotentiallyMultiValuedMap headers = request.getHeaders();
        assertThat(headers.get("connection")).isEqualTo("Keep-Alive");
        assertThat(headers.get("accept-encoding")).isEqualTo("gzip,deflate");

        ResponseImpl response = transaction.getContext().getResponse();
        assertThat(response.getStatusCode()).isEqualTo(ApiGatewayV1LambdaFunction.EXPECTED_STATUS_CODE);
        assertThat(response.getHeaders()).isNotNull();
        assertThat(response.getHeaders().get(ApiGatewayV1LambdaFunction.EXPECTED_RESPONSE_HEADER_1_KEY)).isEqualTo(ApiGatewayV1LambdaFunction.EXPECTED_RESPONSE_HEADER_1_VALUE);
        assertThat(response.getHeaders().get(ApiGatewayV1LambdaFunction.EXPECTED_RESPONSE_HEADER_2_KEY)).isEqualTo(ApiGatewayV1LambdaFunction.EXPECTED_RESPONSE_HEADER_2_VALUE);

        assertThat(transaction.getContext().getCloudOrigin()).isNotNull();
        assertThat(transaction.getContext().getCloudOrigin().getProvider()).isEqualTo("aws");
        assertThat(transaction.getContext().getCloudOrigin().getServiceName()).isEqualTo("elb");
        assertThat(transaction.getContext().getCloudOrigin().getAccountId()).isEqualTo("123456789012");
        assertThat(transaction.getContext().getCloudOrigin().getRegion()).isEqualTo("us-east-2");

        assertThat(transaction.getContext().getServiceOrigin().hasContent()).isTrue();
        assertThat(transaction.getContext().getServiceOrigin().getName().toString()).isEqualTo("49e9d65c45c6791a");
        assertThat(transaction.getContext().getServiceOrigin().getId()).isEqualTo("arn:aws:elasticloadbalancing:us-east-2:123456789012:targetgroup/lambda-279XGJDqGZ5rsrHC2Fjr/49e9d65c45c6791a");
        assertThat(transaction.getContext().getServiceOrigin().getVersion()).isNull();

        FaasImpl faas = transaction.getFaas();
        assertThat(faas.getExecution()).isEqualTo(TestContext.AWS_REQUEST_ID);
        assertThat(faas.getId()).isEqualTo(TestContext.FUNCTION_ARN);
        assertThat(faas.getTrigger().getType()).isEqualTo("http");
        assertThat(faas.getTrigger().getRequestId()).isEqualTo("Root=1-xxxxxxxxxxxxxx");
    }

    @Test
    public void testCallWithNullRequestContext() {
        ApplicationLoadBalancerRequestEvent requestEvent = createInput();
        requestEvent.setRequestContext(null);

        getFunction().handleRequest(requestEvent, context);

        reporter.awaitTransactionCount(1);
        reporter.awaitSpanCount(1);
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("child-span");
        assertThat(reporter.getFirstSpan().getTransaction()).isEqualTo(reporter.getFirstTransaction());
        TransactionImpl transaction = reporter.getFirstTransaction();
        assertThat(transaction.getNameAsString()).isEqualTo(TestContext.FUNCTION_NAME);
        assertThat(transaction.getType()).isEqualTo("request");
        assertThat(transaction.getResult()).isEqualTo("HTTP 2xx");

        assertThat(transaction.getContext().getCloudOrigin()).isNotNull();
        assertThat(transaction.getContext().getCloudOrigin().getProvider()).isEqualTo("aws");

        assertThat(transaction.getContext().getCloudOrigin().getServiceName()).isEqualTo("elb");

        assertThat(transaction.getContext().getCloudOrigin().getRegion()).isNull();
        assertThat(transaction.getContext().getCloudOrigin().getAccountId()).isNull();

        assertThat(transaction.getContext().getServiceOrigin().hasContent()).isFalse();

        FaasImpl faas = transaction.getFaas();
        assertThat(faas.getExecution()).isEqualTo(TestContext.AWS_REQUEST_ID);

        assertThat(faas.getTrigger().getType()).isEqualTo("http");
        assertThat(faas.getTrigger().getRequestId()).isEqualTo("Root=1-xxxxxxxxxxxxxx");
    }

    @Test
    public void testTransactionNameWithUsePathAsName() {
        doReturn(true).when(config.getConfig(WebConfiguration.class)).isUsePathAsName();
        getFunction().handleRequest(createInput(), context);
        reporter.awaitTransactionCount(1);
        reporter.awaitSpanCount(1);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("POST /toolz/api/v2.0/downloadPDF/PDF_2020-09-11_11-06-01.pdf");
    }

}
