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
import co.elastic.apm.agent.impl.context.Headers;
import co.elastic.apm.agent.impl.transaction.Faas;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class SNSEventLambdaTest extends AbstractLambdaTest<SNSEvent, Void> {

    @BeforeAll
    // Need to overwrite the beforeAll() method from parent,
    // because we need to mock serverlessConfiguration BEFORE instrumentation is initialized!
    public static synchronized void beforeAll() {
        AbstractLambdaTest.initAllButInstrumentation();
        when(Objects.requireNonNull(serverlessConfiguration).getAwsLambdaHandler()).thenReturn(SNSEventLambdaFunction.class.getName());
        AbstractLambdaTest.initInstrumentation();
    }

    @Override
    protected AbstractFunction<SNSEvent, Void> createHandler() {
        return  new SNSEventLambdaFunction();
    }

    @Override
    protected SNSEvent createInput() {
        SNSEvent snsEvent = new SNSEvent();
        snsEvent.setRecords(List.of(createSnsRecord()));
        return snsEvent;
    }

    @Nonnull
    private SNSEvent.SNSRecord createSnsRecord() {
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
        traceparent_Attribute.setValue(TRACEPARENT_EXAMPLE);
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

        assertThat(transaction.getNameAsString()).isEqualTo("RECEIVE " + SNS_TOPIC);
        assertThat(transaction.getType()).isEqualTo("messaging");
        assertThat(transaction.getResult()).isEqualTo("success");
        assertThat(transaction.getOutcome()).isEqualTo(Outcome.SUCCESS);

        assertThat(transaction.getContext().getMessage().hasContent()).isTrue();
        assertThat(transaction.getContext().getMessage().getQueueName()).isEqualTo(SNS_TOPIC);
        assertThat(transaction.getContext().getMessage().getBodyForRead()).isNull();
        assertThat(transaction.getContext().getMessage().getHeaders().isEmpty()).isFalse();
        Map<String, String> attributesMap = new HashMap<>();
        for (Headers.Header header : transaction.getContext().getMessage().getHeaders()) {
            attributesMap.put(header.getKey(), Objects.requireNonNull(header.getValue()).toString());
        }
        assertThat(attributesMap.get(HEADER_1_KEY)).isEqualTo(HEADER_1_VALUE);
        assertThat(attributesMap.get(HEADER_2_KEY)).isEqualTo(HEADER_2_VALUE);
        assertThat(transaction.getContext().getMessage().getAge()).isBetween(MESSAGE_AGE, MESSAGE_AGE + (afterFunctionTimestamp - beforeFunctionTimestamp));

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
        SNSEvent snsEvent = new SNSEvent();
        snsEvent.setRecords(List.of(createSnsRecord(), createSnsRecord()));
        getFunction().handleRequest(snsEvent, context);

        validateResultsForUnspecifiedRecord();
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
