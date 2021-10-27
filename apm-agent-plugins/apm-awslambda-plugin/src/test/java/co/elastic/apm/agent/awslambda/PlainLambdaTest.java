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

import co.elastic.apm.agent.awslambda.lambdas.PlainLambdaFunction;
import co.elastic.apm.agent.awslambda.lambdas.TestContext;
import co.elastic.apm.agent.impl.metadata.MetaData;
import co.elastic.apm.agent.impl.transaction.Faas;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.util.VersionUtils;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.junit.BeforeClass;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class PlainLambdaTest extends AbstractLambdaTest {

    protected PlainLambdaFunction function = new PlainLambdaFunction();

    @BeforeAll
    @BeforeClass
    // Need to overwrite the beforeAll() method from parent,
    // because we need to mock serverlessConfiguration BEFORE instrumentation is initialized!
    public static synchronized void beforeAll() {
        AbstractLambdaTest.initAllButInstrumentation();
        when(serverlessConfiguration.getAwsLambdaHandler()).thenReturn(PlainLambdaFunction.class.getName());
        AbstractLambdaTest.initInstrumentation();
    }

    @Test
    public void testMetaData() throws Exception {
        function.handleRequest(null, context);

        MetaData metaData = tracer.getMetaDataFuture().get(100, TimeUnit.MILLISECONDS);
        assertThat(metaData).isNotNull();
        assertThat(metaData.getService()).isNotNull();
        assertThat(metaData.getService().getId()).isEqualTo(TestContext.FUNCTION_ARN);
        assertThat(metaData.getService().getRuntime()).isNotNull();
        assertThat(metaData.getService().getRuntime().getName()).startsWith("AWS_Lambda_java");
        assertThat(metaData.getService().getRuntime().getVersion()).isEqualTo(System.getProperty("java.version"));
        assertThat(metaData.getService().getFramework()).isNotNull();
        assertThat(metaData.getService().getFramework().getName()).isEqualTo("AWS Lambda");
        assertThat(metaData.getService().getFramework().getVersion()).isEqualTo(VersionUtils.getVersion(RequestHandler.class, "com.amazonaws", "aws-lambda-java-core"));
        assertThat(metaData.getService().getNode()).isNotNull();
        assertThat(metaData.getCloudProviderInfo()).isNotNull();
        assertThat(metaData.getCloudProviderInfo().getProvider()).isEqualTo("aws");
        assertThat(metaData.getCloudProviderInfo().getRegion()).isEqualTo(TestContext.FUNCTION_REGION);
        assertThat(metaData.getCloudProviderInfo().getService()).isNotNull();
        assertThat(metaData.getCloudProviderInfo().getService().getName()).isEqualTo("lambda");
        assertThat(metaData.getCloudProviderInfo().getAccount()).isNotNull();
        assertThat(metaData.getCloudProviderInfo().getAccount().getId()).isEqualTo(TestContext.FUNCTION_ACCOUNT_ID);
        assertThat(metaData.getCloudProviderInfo().getAccount().getName()).isNull();
    }

    @Test
    public void testBasicCall() {
        function.handleRequest(null, context);

        reporter.awaitTransactionCount(1);
        reporter.awaitSpanCount(1);
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("child-span");
        assertThat(reporter.getFirstSpan().getTransaction()).isEqualTo(reporter.getFirstTransaction());
        Transaction transaction = reporter.getFirstTransaction();
        assertThat(transaction.getNameAsString()).isEqualTo(TestContext.FUNCTION_NAME);
        assertThat(transaction.getType()).isEqualTo("request");
        assertThat(transaction.getResult()).isNull();

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
}
