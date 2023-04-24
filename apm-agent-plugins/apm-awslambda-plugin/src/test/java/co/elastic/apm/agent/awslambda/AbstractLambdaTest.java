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
import co.elastic.apm.agent.awslambda.lambdas.AbstractFunction;
import co.elastic.apm.agent.awslambda.lambdas.TestContext;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.ServerlessConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.metadata.MetaDataMock;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.TraceState;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import co.elastic.apm.agent.tracer.Outcome;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public abstract class AbstractLambdaTest<ReqE, ResE> extends AbstractInstrumentationTest {

    // TraceContext
    protected static final String TRACE_ID_EXAMPLE = "0af7651916cd43dd8448eb211c80316c";
    protected static final String TRACE_ID_EXAMPLE_2 = "0af7651916cd43dd8448eb211c80316d";
    protected static final String PARENT_ID_EXAMPLE = "b9c7c989f97918e6";
    protected static final String PARENT_ID_EXAMPLE_2 = "b9c7c989f97918e7";
    protected static final String TRACEPARENT_EXAMPLE = String.format("00-%s-%s-01", TRACE_ID_EXAMPLE, PARENT_ID_EXAMPLE);
    protected static final String TRACEPARENT_EXAMPLE_2 = String.format("00-%s-%s-01", TRACE_ID_EXAMPLE_2, PARENT_ID_EXAMPLE_2);
    protected static final String TRACESTATE_EXAMPLE = TraceState.getHeaderValue(0.77d);

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
    protected static final String CONTENT_TYPE_HEADER = "Content-Type";
    protected static final String TEXT_CONTENT_TYPE = "text/plain";
    protected static final String QUERY_PARAM_KEY = "QUERY_PARAM_KEY";
    protected static final String QUERY_PARAM_VALUE = "QUERY_PARAM_VALUE";
    protected static final Map<String, String> REQUEST_HEADERS = Map.of(
        HEADER_1_KEY, HEADER_1_VALUE,
        HEADER_2_KEY, HEADER_2_VALUE,
        CONTENT_TYPE_HEADER, TEXT_CONTENT_TYPE,
        "Host", API_GATEWAY_HOST,
        TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, TRACEPARENT_EXAMPLE,
        TraceContext.TRACESTATE_HEADER_NAME, TRACESTATE_EXAMPLE
    );
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
    static ServerlessConfiguration serverlessConfiguration;

    @Nullable
    protected TestContext context;

    @Nullable
    private AbstractFunction<ReqE, ResE> function;

    private DslJsonSerializer.Writer jsonSerializer;
    private ObjectMapper objectMapper;

    public AbstractLambdaTest() {
        jsonSerializer = new DslJsonSerializer(
            mock(StacktraceConfiguration.class),
            mock(ApmServerClient.class),
            MetaDataMock.create()
        ).newWriter();
        objectMapper = new ObjectMapper();
    }

    protected AbstractFunction<ReqE, ResE> getFunction() {
        return Objects.requireNonNull(function);
    }

    protected abstract AbstractFunction<ReqE, ResE> createHandler();

    @Nullable
    protected abstract ReqE createInput();

    protected boolean supportsContextPropagation() {
        return true;
    }

    static synchronized void initAllButInstrumentation() {
        config = SpyConfiguration.createSpyConfig();
        serverlessConfiguration = config.getConfig(ServerlessConfiguration.class);
        doReturn(true).when(serverlessConfiguration).runsOnAwsLambda();
        MockTracer.MockInstrumentationSetup mockInstrumentationSetup = MockTracer.createMockInstrumentationSetup(config);
        tracer = mockInstrumentationSetup.getTracer();
        objectPoolFactory = mockInstrumentationSetup.getObjectPoolFactory();
        reporter = mockInstrumentationSetup.getReporter();
        assertThat(tracer.isRunning()).isTrue();
    }

    static synchronized void initInstrumentation() {
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());
    }

    @BeforeEach
    public void initTests() {
        context = new TestContext();
        function = createHandler();
    }

    @Test
    public void testCallWithHandlerError() {
        Objects.requireNonNull(context).raiseException();
        assertThatThrownBy(() -> getFunction().handleRequest(createInput(), context)).isInstanceOf(RuntimeException.class);
        verifyFailure();
    }

    protected void verifyFailure() {
        reporter.awaitTransactionCount(1);
        reporter.awaitSpanCount(1);
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("child-span");
        assertThat(reporter.getFirstSpan().getTransaction()).isEqualTo(reporter.getFirstTransaction());
        Transaction transaction = reporter.getFirstTransaction();
        assertThat(transaction.getOutcome()).isEqualTo(Outcome.FAILURE);
        assertThat(transaction.getResult()).isEqualTo("failure");
    }

    @Test
    public void testTraceContext() {
        ReqE input = createInput();
        if (!supportsContextPropagation()) {
            return;
        }
        getFunction().handleRequest(input, context);
        Transaction transaction = reporter.getFirstTransaction();
        TraceContext traceContext = transaction.getTraceContext();
        verifyDistributedTracing(traceContext);
    }

    protected void verifyDistributedTracing(TraceContext traceContext) {
        assertThat(traceContext.getTraceId().toString()).isEqualTo(TRACE_ID_EXAMPLE);
        assertThat(traceContext.getParentId().toString()).isEqualTo(PARENT_ID_EXAMPLE);
        assertThat(traceContext.getTraceState().getSampleRate()).isEqualTo(0.77d);
    }

    protected void printTransactionJson(Transaction transaction) {
        String transactionJson = jsonSerializer.toJsonString(transaction);
        try {
            System.out.println(objectMapper.readTree(transactionJson).toPrettyString());
        } catch (JsonProcessingException e) {
            System.err.println("Failed to deserialize transaction JSON");
            e.printStackTrace();
        }
    }
}
