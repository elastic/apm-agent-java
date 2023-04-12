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
import co.elastic.apm.agent.awslambda.lambdas.SNSEventLambdaFunction;
import co.elastic.apm.agent.awslambda.lambdas.TestContext;
import co.elastic.apm.agent.impl.transaction.Faas;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class SNSEventLambdaTest extends AbstractLambdaTest<SNSEvent, Void> {

    @BeforeAll
    // Need to overwrite the beforeAll() method from parent,
    // because we need to mock serverlessConfiguration BEFORE instrumentation is initialized!
    public static synchronized void beforeAll() {
        AbstractLambdaTest.initAllButInstrumentation();
        doReturn(SNSEventLambdaFunction.class.getName()).when(Objects.requireNonNull(serverlessConfiguration)).getAwsLambdaHandler();
        AbstractLambdaTest.initInstrumentation();
    }

    @Override
    protected AbstractFunction<SNSEvent, Void> createHandler() {
        return  new SNSEventLambdaFunction();
    }

    @Override
    protected SNSEvent createInput() {
        SNSEvent snsEvent = new SNSEvent();
        snsEvent.setRecords(List.of(createSnsRecord(true)));
        return snsEvent;
    }

    @Nonnull
    private SNSEvent.SNSRecord createSnsRecord(boolean useDefaultTraceparent) {
        SNSEvent.SNS sns = new SNSEvent.SNS();
        sns.setMessageId(MESSAGE_ID);
        sns.setMessage(MESSAGE_BODY);
        sns.setTopicArn(SNS_EVENT_SOURCE_ARN);
        sns.setTimestamp(new DateTime(System.currentTimeMillis() - MESSAGE_AGE));
        SNSEvent.MessageAttribute header_1_Attribute = new SNSEvent.MessageAttribute();
        header_1_Attribute.setValue(HEADER_1_VALUE);
        SNSEvent.MessageAttribute header_2_Attribute = new SNSEvent.MessageAttribute();
        header_2_Attribute.setValue(HEADER_2_VALUE);
        SNSEvent.MessageAttribute traceparent_Attribute = new SNSEvent.MessageAttribute();
        traceparent_Attribute.setValue(useDefaultTraceparent ? TRACEPARENT_EXAMPLE : TRACEPARENT_EXAMPLE_2);
        SNSEvent.MessageAttribute tracestate_Attribute = new SNSEvent.MessageAttribute();
        tracestate_Attribute.setValue(TRACESTATE_EXAMPLE);
        sns.setMessageAttributes(Map.of(
            HEADER_1_KEY,header_1_Attribute,
            HEADER_2_KEY, header_2_Attribute,
            TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, traceparent_Attribute,
            TraceContext.TRACESTATE_HEADER_NAME, tracestate_Attribute
        ));

        SNSEvent.SNSRecord record = new SNSEvent.SNSRecord();
        record.setEventSource(SNS_EVENT_SOURCE_ARN);
        record.setEventVersion(SNS_EVENT_VERSION);
        record.setSns(sns);

        return record;
    }

    @Override
    protected void verifyDistributedTracing(TraceContext traceContext) {
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
        Transaction transaction = reporter.getFirstTransaction();
        List<TraceContext> spanLinks = transaction.getSpanLinks();
        List<TraceContext> matchedSpanLinks = spanLinks.stream().filter(spanLink -> spanLink.getParentId().toString().equals(parentId)).collect(Collectors.toList());
        assertThat(matchedSpanLinks).hasSize(1);
        assertThat(matchedSpanLinks.get(0).getTraceId().toString()).isEqualTo(traceId);
    }

    private void verifyTransactionDetails() {
        reporter.awaitTransactionCount(1);
        reporter.awaitSpanCount(1);
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("child-span");
        assertThat(reporter.getFirstSpan().getTransaction()).isEqualTo(reporter.getFirstTransaction());
        Transaction transaction = reporter.getFirstTransaction();
        printTransactionJson(transaction);

        assertThat(transaction.getNameAsString()).isEqualTo("RECEIVE " + SNS_TOPIC);
        assertThat(transaction.getType()).isEqualTo("messaging");
        assertThat(transaction.getResult()).isEqualTo("success");
        assertThat(transaction.getOutcome()).isEqualTo(Outcome.SUCCESS);

        assertThat(transaction.getContext().getMessage().hasContent()).isFalse();

        assertThat(transaction.getContext().getServiceOrigin().hasContent()).isTrue();
        assertThat(transaction.getContext().getServiceOrigin().getName().toString()).isEqualTo(SNS_TOPIC);
        assertThat(transaction.getContext().getServiceOrigin().getId()).isEqualTo(SNS_EVENT_SOURCE_ARN);
        assertThat(transaction.getContext().getServiceOrigin().getVersion()).isEqualTo(SNS_EVENT_VERSION);

        assertThat(transaction.getContext().getCloudOrigin()).isNotNull();
        assertThat(transaction.getContext().getCloudOrigin().getProvider()).isEqualTo("aws");
        assertThat(transaction.getContext().getCloudOrigin().getServiceName()).isEqualTo("sns");
        assertThat(transaction.getContext().getCloudOrigin().getRegion()).isEqualTo(EVENT_SOURCE_REGION);
        assertThat(transaction.getContext().getCloudOrigin().getAccountId()).isEqualTo(EVENT_SOURCE_ACCOUNT_ID);

        Faas faas = transaction.getFaas();
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
        SNSEvent snsEvent = new SNSEvent();
        snsEvent.setRecords(List.of(createSnsRecord(true), createSnsRecord(false)));
        getFunction().handleRequest(snsEvent, context);
        verifyTransactionDetails();
        verifySpanLink(TRACE_ID_EXAMPLE, PARENT_ID_EXAMPLE);
        verifySpanLink(TRACE_ID_EXAMPLE_2, PARENT_ID_EXAMPLE_2);
    }

    @Test
    public void testCallWithEmptyRecord() {
        SNSEvent snsEvent = new SNSEvent();
        snsEvent.setRecords(List.of(new SNSEvent.SNSRecord()));
        getFunction().handleRequest(snsEvent, context);
        validateResultsForUnspecifiedRecord();
    }

    @Test
    public void testCallWithEmptySNS() {
        SNSEvent snsEvent = new SNSEvent();
        SNSEvent.SNSRecord snsRecord = new SNSEvent.SNSRecord();
        snsRecord.setSns(new SNSEvent.SNS());
        snsEvent.setRecords(List.of(snsRecord));
        getFunction().handleRequest(snsEvent, context);
        validateResultsForUnspecifiedRecord();
    }

    private void validateResultsForUnspecifiedRecord() {
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
        assertThat(transaction.getContext().getCloudOrigin().getServiceName()).isEqualTo("sns");
        assertThat(transaction.getContext().getCloudOrigin().getRegion()).isNull();
        assertThat(transaction.getContext().getCloudOrigin().getAccountId()).isNull();

        assertThat(transaction.getContext().getServiceOrigin().hasContent()).isFalse();

        Faas faas = transaction.getFaas();
        assertThat(faas.getExecution()).isEqualTo(TestContext.AWS_REQUEST_ID);

        assertThat(faas.getTrigger().getType()).isEqualTo("pubsub");
        assertThat(faas.getTrigger().getRequestId()).isNull();
    }
}
