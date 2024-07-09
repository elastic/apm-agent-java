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
import co.elastic.apm.agent.impl.transaction.FaasImpl;
import co.elastic.apm.agent.impl.transaction.TraceContextImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import co.elastic.apm.agent.tracer.Outcome;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class SQSEventLambdaTest extends AbstractLambdaTest<SQSEvent, Void> {

    @BeforeAll
    // Need to overwrite the beforeAll() method from parent,
    // because we need to mock serverlessConfiguration BEFORE instrumentation is initialized!
    public static synchronized void beforeAll() {
        AbstractLambdaTest.initAllButInstrumentation();
        doReturn(SQSEventLambdaFunction.class.getName()).when(Objects.requireNonNull(serverlessConfiguration)).getAwsLambdaHandler();
        AbstractLambdaTest.initInstrumentation();
    }

    @Override
    protected AbstractFunction<SQSEvent, Void> createHandler() {
        return new SQSEventLambdaFunction();
    }

    @Override
    protected SQSEvent createInput() {
        SQSEvent sqsEvent = new SQSEvent();
        sqsEvent.setRecords(List.of(createSqsMessage(true)));
        return sqsEvent;
    }

    @Nonnull
    private SQSEvent.SQSMessage createSqsMessage(boolean useDefaultTraceparent) {
        SQSEvent.SQSMessage sqsMessage = new SQSEvent.SQSMessage();
        sqsMessage.setEventSourceArn(SQS_EVENT_SOURCE_ARN);
        sqsMessage.setMessageId(MESSAGE_ID);
        sqsMessage.setAwsRegion(EVENT_SOURCE_REGION);
        SQSEvent.MessageAttribute sentTimestampAttribute = new SQSEvent.MessageAttribute();
        sentTimestampAttribute.setStringValue(Long.toString(System.currentTimeMillis() - MESSAGE_AGE));
        SQSEvent.MessageAttribute header_1_Attribute = new SQSEvent.MessageAttribute();
        header_1_Attribute.setStringValue(HEADER_1_VALUE);
        SQSEvent.MessageAttribute traceparent_Attribute = new SQSEvent.MessageAttribute();
        traceparent_Attribute.setStringValue(useDefaultTraceparent ? TRACEPARENT_EXAMPLE : TRACEPARENT_EXAMPLE_2);
        SQSEvent.MessageAttribute tracestate_Attribute = new SQSEvent.MessageAttribute();
        tracestate_Attribute.setStringValue(TRACESTATE_EXAMPLE);
        sqsMessage.setMessageAttributes(Map.of(
            "SentTimestamp", sentTimestampAttribute,
            HEADER_1_KEY, header_1_Attribute,
            TraceContextImpl.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, traceparent_Attribute,
            TraceContextImpl.TRACESTATE_HEADER_NAME, tracestate_Attribute
        ));
        sqsMessage.setBody(MESSAGE_BODY);
        return sqsMessage;
    }

    private SQSEvent createSQSEventWithNulls() {
        SQSEvent sqsEvent = new SQSEvent();
        sqsEvent.setRecords(List.of(new SQSEvent.SQSMessage()));
        return sqsEvent;
    }

    @Override
    protected void verifyDistributedTracing(TraceContextImpl traceContext) {
        // batch processing root transaction, distributed tracing is supported through span links
        assertThat(traceContext.getParentId().isEmpty()).isTrue();
        assertThat(traceContext.getTraceState().getSampleRate()).isEqualTo(1d);
    }

    @Test
    public void testBasicCall() {
        getFunction().handleRequest(createInput(), context);
        verifyTransactionDetails();
        verifySpanLink(TRACE_ID_EXAMPLE, PARENT_ID_EXAMPLE);
    }

    private void verifySpanLink(String traceId, String parentId) {
        TransactionImpl transaction = reporter.getFirstTransaction();
        List<TraceContextImpl> spanLinks = transaction.getSpanLinks();
        List<TraceContextImpl> matchedSpanLinks = spanLinks.stream().filter(spanLink -> spanLink.getParentId().toString().equals(parentId)).collect(Collectors.toList());
        assertThat(matchedSpanLinks).hasSize(1);
        assertThat(matchedSpanLinks.get(0).getTraceId().toString()).isEqualTo(traceId);
    }

    private void verifyTransactionDetails() {
        reporter.awaitTransactionCount(1);
        reporter.awaitSpanCount(1);
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("child-span");
        assertThat(reporter.getFirstSpan().getTransaction()).isEqualTo(reporter.getFirstTransaction());
        TransactionImpl transaction = reporter.getFirstTransaction();
        assertThat(reporter.getPartialTransactions()).containsExactly(transaction);
        printTransactionJson(transaction);

        assertThat(transaction.getContext().getMessage().hasContent()).isFalse();

        assertThat(transaction.getNameAsString()).isEqualTo("RECEIVE " + SQS_QUEUE);
        assertThat(transaction.getType()).isEqualTo("messaging");
        assertThat(transaction.getResult()).isEqualTo("success");
        assertThat(transaction.getOutcome()).isEqualTo(Outcome.SUCCESS);

        assertThat(transaction.getContext().getServiceOrigin().hasContent()).isTrue();
        assertThat(transaction.getContext().getServiceOrigin().getName().toString()).isEqualTo(SQS_QUEUE);
        assertThat(transaction.getContext().getServiceOrigin().getId()).isEqualTo(SQS_EVENT_SOURCE_ARN);
        assertThat(transaction.getContext().getServiceOrigin().getVersion()).isNull();

        assertThat(transaction.getContext().getCloudOrigin()).isNotNull();
        assertThat(transaction.getContext().getCloudOrigin().getProvider()).isEqualTo("aws");
        assertThat(transaction.getContext().getCloudOrigin().getServiceName()).isEqualTo("sqs");
        assertThat(transaction.getContext().getCloudOrigin().getRegion()).isEqualTo(EVENT_SOURCE_REGION);
        assertThat(transaction.getContext().getCloudOrigin().getAccountId()).isEqualTo(EVENT_SOURCE_ACCOUNT_ID);

        FaasImpl faas = transaction.getFaas();
        assertThat(faas.getExecution()).isEqualTo(TestContext.AWS_REQUEST_ID);
        assertThat(faas.getId()).isEqualTo(TestContext.FUNCTION_ARN);
        assertThat(faas.getTrigger().getType()).isEqualTo("pubsub");
    }

    @Test
    public void testCallWithNullInput() {
        getFunction().handleRequest(null, context);

        reporter.awaitTransactionCount(1);
        reporter.awaitSpanCount(1);
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("child-span");
        assertThat(reporter.getFirstSpan().getTransaction()).isEqualTo(reporter.getFirstTransaction());
        TransactionImpl transaction = reporter.getFirstTransaction();
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

        FaasImpl faas = transaction.getFaas();
        assertThat(faas.getExecution()).isEqualTo(TestContext.AWS_REQUEST_ID);

        assertThat(faas.getTrigger().getType()).isEqualTo("other");
        assertThat(faas.getTrigger().getRequestId()).isNull();
    }

    @Test
    public void testCallWithMultipleMessagesPerEvent() {
        SQSEvent sqsEvent = new SQSEvent();
        sqsEvent.setRecords(List.of(createSqsMessage(true), createSqsMessage(false)));
        getFunction().handleRequest(sqsEvent, context);
        verifyTransactionDetails();
        verifySpanLink(TRACE_ID_EXAMPLE, PARENT_ID_EXAMPLE);
        verifySpanLink(TRACE_ID_EXAMPLE_2, PARENT_ID_EXAMPLE_2);
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
        TransactionImpl transaction = reporter.getFirstTransaction();
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

        FaasImpl faas = transaction.getFaas();
        assertThat(faas.getExecution()).isEqualTo(TestContext.AWS_REQUEST_ID);

        assertThat(faas.getTrigger().getType()).isEqualTo("pubsub");
        assertThat(faas.getTrigger().getRequestId()).isNull();
    }
}
