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

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.awslambda.lambdas.TestContext;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.ServerlessConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import com.amazonaws.services.lambda.runtime.Context;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.BeforeEach;

import javax.annotation.Nullable;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public abstract class AbstractLambdaTest extends AbstractInstrumentationTest {

    // API Gateway data
    protected static final String API_ID = "API_ID";
    protected static final String API_GATEWAY_REQUEST_ID = "REQUEST_ID";
    protected static final String API_GATEWAY_ACCOUNT_ID = "ACCOUNT_ID";
    protected static final String API_GATEWAY_OPERATION_NAME = "OPERATION_NAME";
    protected static final String API_GATEWAY_RESOURCE_PATH = "/some/resource";
    protected static final String API_GATEWAY_STAGE = "SOME_STAGE";
    protected static final String API_GATEWAY_HOST = "70ixmpl4fl.execute-api.us-east-2.amazonaws.com";

    // HTTP request data
    protected static final String HTTP_METHOD = "POST";
    protected static final String BODY = "This is a request body";
    protected static final String HEADER_1_KEY = "HEADER_1_KEY";
    protected static final String HEADER_1_VALUE = "HEADER_1_VALUE";
    protected static final String HEADER_2_KEY = "HEADER_2_KEY";
    protected static final String HEADER_2_VALUE = "HEADER_2_VALUE";
    protected static final String QUERY_PARAM_KEY = "QUERY_PARAM_KEY";
    protected static final String QUERY_PARAM_VALUE = "QUERY_PARAM_VALUE";
    protected static final Map<String, String> REQUEST_HEADERS = Map.of(HEADER_1_KEY, HEADER_1_VALUE, HEADER_2_KEY, HEADER_2_VALUE, "Host", API_GATEWAY_HOST);
    protected static final String PATH = "/some/url/path";

    // event source data
    protected static final String EVENT_SOURCE_REGION = "us-east-2";
    protected static final String EVENT_SOURCE_ACCOUNT_ID = "123456789012";

    // messaging data
    protected static final String MESSAGE_ID = "my-message-id";
    protected static final long MESSAGE_AGE = 1000;
    protected static final String MESSAGE_BODY = "This is a message body";

    // SQS data
    protected static final String SQS_QUEUE = "my-queue";
    protected static final String SQS_EVENT_SOURCE_ARN = "arn:aws:sqs:" + EVENT_SOURCE_REGION + ":" + EVENT_SOURCE_ACCOUNT_ID + ":" + SQS_QUEUE;

    // SNS data
    protected static final String SNS_TOPIC = "my-topic";
    protected static final String SNS_EVENT_SOURCE_ARN = "arn:aws:sns:" + EVENT_SOURCE_REGION + ":" + EVENT_SOURCE_ACCOUNT_ID + ":" + SNS_TOPIC;
    protected static final String SNS_EVENT_VERSION = "2.1";

    // S3 data
    protected static final String S3_EVENT_NAME = "ObjectCreated:Put";
    protected static final String S3_EVENT_SOURCE = "aws:s3";
    protected static final String S3_EVENT_VERSION = "2.2";
    protected static final String S3_REQUEST_ID = "C3D13FE58DE4C810";
    protected static final String S3_BUCKET_NAME = "my-s3-bucket";
    protected static final String S3_BUCKET_ARN = "arn:aws:s3:::" + S3_BUCKET_NAME;

    @Nullable
    protected static ServerlessConfiguration serverlessConfiguration;
    @Nullable
    protected Context context;

    protected static synchronized void initAllButInstrumentation() {
        config = SpyConfiguration.createSpyConfig();
        serverlessConfiguration = config.getConfig(ServerlessConfiguration.class);
        when(serverlessConfiguration.runsOnAwsLambda()).thenReturn(true);
        MockTracer.MockInstrumentationSetup mockInstrumentationSetup = MockTracer.createMockInstrumentationSetup(config);
        tracer = mockInstrumentationSetup.getTracer();
        objectPoolFactory = mockInstrumentationSetup.getObjectPoolFactory();
        reporter = mockInstrumentationSetup.getReporter();
        assertThat(tracer.isRunning()).isTrue();
    }

    protected static synchronized void initInstrumentation() {
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());
    }


    @BeforeEach
    public void initTests() {
        context = new TestContext();
    }

}
