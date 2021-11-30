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
import co.elastic.apm.agent.awslambda.lambdas.SQSEventLambdaFunction;
import co.elastic.apm.agent.awslambda.lambdas.TestContext;
import co.elastic.apm.agent.impl.context.Headers;
import co.elastic.apm.agent.impl.transaction.Faas;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class SQSEventLambdaTest extends AbstractLambdaTest<SQSEvent, Void> {

    @BeforeAll
    // Need to overwrite the beforeAll() method from parent,
    // because we need to mock serverlessConfiguration BEFORE instrumentation is initialized!
    public static synchronized void beforeAll() {
        AbstractLambdaTest.initAllButInstrumentation();
        when(Objects.requireNonNull(serverlessConfiguration).getAwsLambdaHandler()).thenReturn(SQSEventLambdaFunction.class.getName());
        AbstractLambdaTest.initInstrumentation();
    }

    @Override
    protected AbstractFunction<SQSEvent, Void> createHandler() {
        return new SQSEventLambdaFunction();
    }

    @Override
    protected SQSEvent createInput() {
        SQSEvent sqsEvent = new SQSEvent();
        sqsEvent.setRecords(List.of(createSqsMessage()));
        return sqsEvent;
    }

    @Nonnull
    private SQSEvent.SQSMessage createSqsMessage() {
        SQSEvent.SQSMessage sqsMessage = new SQSEvent.SQSMessage();
        sqsMessage.setEventSourceArn(SQS_EVENT_SOURCE_ARN);
        sqsMessage.setMessageId(MESSAGE_ID);
        sqsMessage.setAwsRegion(EVENT_SOURCE_REGION);
        SQSEvent.MessageAttribute sentTimestampAttribute = new SQSEvent.MessageAttribute();
        sentTimestampAttribute.setStringValue(Long.toString(System.currentTimeMillis() - MESSAGE_AGE));
        SQSEvent.MessageAttribute header_1_Attribute = new SQSEvent.MessageAttribute();
        header_1_Attribute.setStringValue(HEADER_1_VALUE);
        SQSEvent.MessageAttribute traceparent_Attribute = new SQSEvent.MessageAttribute();
        traceparent_Attribute.setStringValue(TRACEPARENT_EXAMPLE);
        SQSEvent.MessageAttribute tracestate_Attribute = new SQSEvent.MessageAttribute();
        tracestate_Attribute.setStringValue(TRACESTATE_EXAMPLE);
        sqsMessage.setMessageAttributes(Map.of(
            "SentTimestamp", sentTimestampAttribute,
            HEADER_1_KEY, header_1_Attribute,
            TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, traceparent_Attribute,
            TraceContext.TRACESTATE_HEADER_NAME, tracestate_Attribute
        ));
        sqsMessage.setBody(MESSAGE_BODY);
        return sqsMessage;
    }

    private SQSEvent createSQSEventWithNulls() {
        SQSEvent sqsEvent = new SQSEvent();
        sqsEvent.setRecords(List.of(new SQSEvent.SQSMessage()));

        return sqsEvent;
    }


    @Test
    public void testBasicCall() {
        long beforeFunctionTimestamp = System.currentTimeMillis();
        getFunction().handleRequest(createInput(), context);
        long afterFunctionTimestamp = System.currentTimeMillis();
        reporter.awaitTransactionCount(1);
        reporter.awaitSpanCount(1);
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("child-span");
        assertThat(reporter.getFirstSpan().getTransaction()).isEqualTo(reporter.getFirstTransaction());
        Transaction transaction = reporter.getFirstTransaction();
        printTransactionJson(transaction);

        assertThat(transaction.getNameAsString()).isEqualTo("RECEIVE " + SQS_QUEUE);
        assertThat(transaction.getType()).isEqualTo("messaging");
        assertThat(transaction.getResult()).isEqualTo("success");
        assertThat(transaction.getOutcome()).isEqualTo(Outcome.SUCCESS);

        assertThat(transaction.getContext().getMessage().hasContent()).isTrue();
        assertThat(transaction.getContext().getMessage().getQueueName()).isEqualTo(SQS_QUEUE);
        assertThat(transaction.getContext().getMessage().getBodyForRead()).isNull();
        assertThat(transaction.getContext().getMessage().getHeaders().isEmpty()).isFalse();
        Map<String, String> attributesMap = new HashMap<>();
        for (Headers.Header header : transaction.getContext().getMessage().getHeaders()) {
            attributesMap.put(header.getKey(), Objects.requireNonNull(header.getValue()).toString());
        }
        assertThat(attributesMap.get(HEADER_1_KEY)).isEqualTo(HEADER_1_VALUE);
        assertThat(attributesMap.containsKey("SentTimestamp")).isTrue();
        assertThat(transaction.getContext().getMessage().getAge()).isBetween(MESSAGE_AGE, MESSAGE_AGE + (afterFunctionTimestamp - beforeFunctionTimestamp));

        assertThat(transaction.getContext().getServiceOrigin().hasContent()).isTrue();
        assertThat(transaction.getContext().getServiceOrigin().getName().toString()).isEqualTo(SQS_QUEUE);
        assertThat(transaction.getContext().getServiceOrigin().getId()).isEqualTo(SQS_EVENT_SOURCE_ARN);
        assertThat(transaction.getContext().getServiceOrigin().getVersion()).isNull();

        assertThat(transaction.getContext().getCloudOrigin()).isNotNull();
        assertThat(transaction.getContext().getCloudOrigin().getProvider()).isEqualTo("aws");
        assertThat(transaction.getContext().getCloudOrigin().getServiceName()).isEqualTo("sqs");
        assertThat(transaction.getContext().getCloudOrigin().getRegion()).isEqualTo(EVENT_SOURCE_REGION);
        assertThat(transaction.getContext().getCloudOrigin().getAccountId()).isEqualTo(EVENT_SOURCE_ACCOUNT_ID);

        Faas faas = transaction.getFaas();
        assertThat(faas.getExecution()).isEqualTo(TestContext.AWS_REQUEST_ID);

        assertThat(faas.getTrigger().getType()).isEqualTo("pubsub");
        assertThat(faas.getTrigger().getRequestId()).isEqualTo(MESSAGE_ID);
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
        assertThat(transaction.getResult()).isEqualTo("success");
        assertThat(transaction.getOutcome()).isEqualTo(Outcome.SUCCESS);

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
    public void testCallWithMultipleMessagesPerEvent() {
        SQSEvent sqsEvent = new SQSEvent();
        sqsEvent.setRecords(List.of(createSqsMessage(), createSqsMessage()));
        getFunction().handleRequest(sqsEvent, context);

        validateResultsForUnspecifiedMessage();
    }

    @Test
    public void testCallWithEmptyMessage() {
        getFunction().handleRequest(createSQSEventWithNulls(), context);
        validateResultsForUnspecifiedMessage();
    }

    private void validateResultsForUnspecifiedMessage() {
        reporter.awaitTransactionCount(1);
        reporter.awaitSpanCount(1);
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("child-span");
        assertThat(reporter.getFirstSpan().getTransaction()).isEqualTo(reporter.getFirstTransaction());
        Transaction transaction = reporter.getFirstTransaction();
        assertThat(transaction.getNameAsString()).isEqualTo(TestContext.FUNCTION_NAME);
        assertThat(transaction.getType()).isEqualTo("messaging");
        assertThat(transaction.getResult()).isEqualTo("success");
        assertThat(transaction.getOutcome()).isEqualTo(Outcome.SUCCESS);

        assertThat(transaction.getContext().getCloudOrigin()).isNotNull();
        assertThat(transaction.getContext().getCloudOrigin().getProvider()).isEqualTo("aws");
        assertThat(transaction.getContext().getCloudOrigin().getServiceName()).isEqualTo("sqs");
        assertThat(transaction.getContext().getCloudOrigin().getRegion()).isNull();
        assertThat(transaction.getContext().getCloudOrigin().getAccountId()).isNull();

        assertThat(transaction.getContext().getServiceOrigin().hasContent()).isFalse();

        Faas faas = transaction.getFaas();
        assertThat(faas.getExecution()).isEqualTo(TestContext.AWS_REQUEST_ID);

        assertThat(faas.getTrigger().getType()).isEqualTo("pubsub");
        assertThat(faas.getTrigger().getRequestId()).isNull();
    }
}
