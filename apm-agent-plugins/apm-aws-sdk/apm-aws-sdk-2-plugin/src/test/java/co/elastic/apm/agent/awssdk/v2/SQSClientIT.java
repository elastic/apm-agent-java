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
package co.elastic.apm.agent.awssdk.v2;

import co.elastic.apm.agent.awssdk.common.AbstractSQSClientIT;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.tracer.configuration.MessagingConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class SQSClientIT extends AbstractSQSClientIT {
    private SqsClient sqs;
    private SqsAsyncClient sqsAsync;

    private final Consumer<Span> messagingAssert = span -> assertThat(span.getContext().getMessage().getQueueName()).isEqualTo(SQS_QUEUE_NAME);

    CoreConfiguration coreConfiguration;
    MessagingConfiguration messagingConfiguration;

    @BeforeEach
    public void setupClient() {
        coreConfiguration = tracer.getConfig(CoreConfiguration.class);
        messagingConfiguration = tracer.getConfig(MessagingConfiguration.class);

        sqs = SqsClient.builder().endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SQS))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                localstack.getAccessKey(), localstack.getSecretKey()
            )))
            .region(Region.of(localstack.getRegion())).build();

        sqsAsync = SqsAsyncClient.builder().endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SQS))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                localstack.getAccessKey(), localstack.getSecretKey()
            )))
            .region(Region.of(localstack.getRegion())).build();
    }

    @Test
    public void testSQSClient() {
        Transaction transaction = startTestRootTransaction("sqs-test");

        newTest(() -> sqs.createQueue(CreateQueueRequest.builder().queueName(SQS_QUEUE_NAME).build()))
            .operationName("CreateQueue")
            .entityName(SQS_QUEUE_NAME)
            .execute();

        newTest(() -> sqs.listQueues())
            .operationName("ListQueues")
            .execute();

        final StringBuilder queueUrl = new StringBuilder();
        newTest(() ->
            queueUrl.append(sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(SQS_QUEUE_NAME).build()).queueUrl()))
            .operationName("GetQueueUrl")
            .entityName(SQS_QUEUE_NAME)
            .execute();

        newTest(() -> sqs.sendMessage(SendMessageRequest.builder()
            .queueUrl(queueUrl.toString())
            .messageAttributes(Collections.singletonMap("myKey", MessageAttributeValue.builder().dataType("String").stringValue("myValue").build()))
            .messageBody(MESSAGE_BODY)
            .build()))
            .operationName("SEND to")
            .entityName(SQS_QUEUE_NAME)
            .action("send")
            .withSpanAssertions(messagingAssert)
            .execute();

        final StringBuilder receiptHandle = new StringBuilder();

        newTest(() -> {
            ReceiveMessageResponse response = sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(queueUrl.toString()).build());
            assertThat(response.hasMessages()).isTrue();
            assertThat(response.messages().get(0).messageAttributes()).containsKey("traceparent");
            assertThat(response.messages().get(0).messageAttributes()).containsKey("tracestate");
            receiptHandle.append(response.messages().get(0).receiptHandle());
            return response;
        })
            .operationName("POLL from")
            .entityName(SQS_QUEUE_NAME)
            .action("poll")
            .withSpanAssertions(messagingAssert)
            .execute();

        newTest(() -> sqs.sendMessageBatch(SendMessageBatchRequest.builder()
            .queueUrl(queueUrl.toString())
            .entries(
                SendMessageBatchRequestEntry.builder().id("first").messageBody(MESSAGE_BODY).build(),
                SendMessageBatchRequestEntry.builder().id("second").messageBody(MESSAGE_BODY).build())
            .build()))
            .operationName("SEND_BATCH to")
            .entityName(SQS_QUEUE_NAME)
            .action("send_batch")
            .withSpanAssertions(messagingAssert)
            .execute();


        newTest(() -> sqs.deleteMessage(
            DeleteMessageRequest.builder()
                .queueUrl(queueUrl.toString()).receiptHandle(receiptHandle.toString())
                .build()))
            .operationName("DELETE from")
            .entityName(SQS_QUEUE_NAME)
            .action("delete")
            .execute();

        newTest(() -> sqs.deleteQueue(DeleteQueueRequest.builder().queueUrl(queueUrl.toString()).build()))
            .operationName("DeleteQueue")
            .entityName(SQS_QUEUE_NAME)
            .execute();

        assertThat(reporter.getSpans()).hasSize(8);

        transaction.deactivate().end();
    }

    @Test
    public void testAsyncSQSClient() {
        Transaction transaction = startTestRootTransaction("sqs-test");

        newTest(() -> sqsAsync.createQueue(CreateQueueRequest.builder().queueName(SQS_QUEUE_NAME).build()))
            .operationName("CreateQueue")
            .entityName(SQS_QUEUE_NAME)
            .async()
            .execute();

        newTest(() -> sqsAsync.listQueues())
            .operationName("ListQueues")
            .async()
            .execute();

        final StringBuilder queueUrl = new StringBuilder();

        newTest(() ->
            queueUrl.append(sqsAsync.getQueueUrl(GetQueueUrlRequest.builder().queueName(SQS_QUEUE_NAME).build()).join().queueUrl()))
            .operationName("GetQueueUrl")
            .entityName(SQS_QUEUE_NAME)
            .async()
            .execute();

        newTest(() -> sqsAsync.sendMessage(SendMessageRequest.builder()
            .queueUrl(queueUrl.toString())
            .messageBody(MESSAGE_BODY)
            .build()))
            .operationName("SEND to")
            .entityName(SQS_QUEUE_NAME)
            .action("send")
            .withSpanAssertions(messagingAssert)
            .async()
            .execute();

        final StringBuilder receiptHandle = new StringBuilder();

        newTest(() -> {
            ReceiveMessageResponse response = sqsAsync.receiveMessage(ReceiveMessageRequest.builder().queueUrl(queueUrl.toString()).build()).join();
            assertThat(response.hasMessages()).isTrue();
            assertThat(response.messages().get(0).messageAttributes()).containsKey("traceparent");
            assertThat(response.messages().get(0).messageAttributes()).containsKey("tracestate");
            receiptHandle.append(response.messages().get(0).receiptHandle());
            return response;
        })
            .operationName("POLL from")
            .entityName(SQS_QUEUE_NAME)
            .action("poll")
            .withSpanAssertions(messagingAssert)
            .async()
            .execute();

        newTest(() -> sqsAsync.sendMessageBatch(SendMessageBatchRequest.builder()
            .queueUrl(queueUrl.toString())
            .entries(
                SendMessageBatchRequestEntry.builder().id("first").messageBody(MESSAGE_BODY).build(),
                SendMessageBatchRequestEntry.builder().id("second").messageBody(MESSAGE_BODY).build())
            .build()))
            .operationName("SEND_BATCH to")
            .entityName(SQS_QUEUE_NAME)
            .action("send_batch")
            .withSpanAssertions(messagingAssert)
            .async()
            .execute();


        newTest(() -> sqsAsync.deleteMessage(
            DeleteMessageRequest.builder()
                .queueUrl(queueUrl.toString()).receiptHandle(receiptHandle.toString())
                .build()))
            .operationName("DELETE from")
            .entityName(SQS_QUEUE_NAME)
            .action("delete")
            .async()
            .execute();

        newTest(() -> sqsAsync.deleteQueue(DeleteQueueRequest.builder().queueUrl(queueUrl.toString()).build()))
            .operationName("DeleteQueue")
            .entityName(SQS_QUEUE_NAME)
            .async()
            .execute();

        transaction.deactivate().end();
    }

    @Test
    public void testNonReceiveMessageWithinMessagingTransaction() {
        sqs.createQueue(CreateQueueRequest.builder().queueName(SQS_QUEUE_NAME).build());

        final StringBuilder queueUrl = new StringBuilder();
        queueUrl.append(sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(SQS_QUEUE_NAME).build()).queueUrl());

        Transaction transaction = startTestRootTransaction("sqs-test");
        transaction.withType("messaging");

        sqs.sendMessage(SendMessageRequest.builder()
            .queueUrl(queueUrl.toString())
            .messageBody(MESSAGE_BODY)
            .build());

        transaction.deactivate().end();

        assertThat(reporter.getFirstTransaction()).isNotNull();
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("sqs-test");
        assertThat(reporter.getFirstSpan()).isNotNull();
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("SQS SEND to " + SQS_QUEUE_NAME);
    }

    @Test
    public void testAsnycNonReceiveMessageWithinMessagingTransaction() {
        sqsAsync.createQueue(CreateQueueRequest.builder().queueName(SQS_QUEUE_NAME).build()).join();

        final StringBuilder queueUrl = new StringBuilder();
        queueUrl.append(sqsAsync.getQueueUrl(GetQueueUrlRequest.builder().queueName(SQS_QUEUE_NAME).build()).join().queueUrl());

        Transaction transaction = startTestRootTransaction("sqs-test");
        transaction.withType("messaging");

        sqsAsync.sendMessage(SendMessageRequest.builder()
            .queueUrl(queueUrl.toString())
            .messageBody(MESSAGE_BODY)
            .build()).join();

        transaction.deactivate().end();

        assertThat(reporter.getFirstTransaction()).isNotNull();
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("sqs-test");
        assertThat(reporter.getFirstSpan()).isNotNull();
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("SQS SEND to " + SQS_QUEUE_NAME);
    }

    @Test
    public void testReceiveMessageWithinMessagingTransaction() {
        when(messagingConfiguration.shouldEndMessagingTransactionOnPoll()).thenReturn(false);
        sqs.createQueue(CreateQueueRequest.builder().queueName(SQS_QUEUE_NAME).build());

        final StringBuilder queueUrl = new StringBuilder();
        queueUrl.append(sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(SQS_QUEUE_NAME).build()).queueUrl());

        Transaction transaction = startTestRootTransaction("sqs-test");
        transaction.withType("messaging");

        sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(queueUrl.toString()).build());

        transaction.deactivate().end();


        assertThat(reporter.getFirstTransaction()).isNotNull();
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("sqs-test");
        assertThat(reporter.getFirstSpan()).isNotNull();
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("SQS POLL from " + SQS_QUEUE_NAME);
    }

    @Override
    public void sendMessage(String queueUrl) {
        sqs.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody(MESSAGE_BODY).build());
    }

    @Override
    public void receiveMessages(String queueUrl, int numMessages) {
        ReceiveMessageResponse response = sqs.receiveMessage(ReceiveMessageRequest.builder().maxNumberOfMessages(numMessages).queueUrl(queueUrl).build());
        for (Message message : response.messages()) {
            executeChildSpan();
        }
    }

    @Override
    public void receiveMessagesAsync(String queueUrl, int numMessages) {
        ReceiveMessageResponse response = sqsAsync.receiveMessage(ReceiveMessageRequest.builder().maxNumberOfMessages(numMessages).queueUrl(queueUrl).build()).join();
        for (Message message : response.messages()) {
            executeChildSpan();
        }
    }

    @Override
    public void executeQueueExclusion(String queueUrl, String ignoredQueueUrl) {
        sqs.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody(MESSAGE_BODY).build());
        sqs.sendMessage(SendMessageRequest.builder().queueUrl(ignoredQueueUrl).messageBody(MESSAGE_BODY).build());
        sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(queueUrl).build());
        sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(ignoredQueueUrl).build());
    }

    @Override
    public void executeAsyncQueueExclusion(String queueUrl, String ignoredQueueUrl) {
        sqsAsync.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody(MESSAGE_BODY).build()).join();
        sqsAsync.sendMessage(SendMessageRequest.builder().queueUrl(ignoredQueueUrl).messageBody(MESSAGE_BODY).build()).join();
        sqsAsync.receiveMessage(ReceiveMessageRequest.builder().queueUrl(queueUrl).build()).join();
        sqsAsync.receiveMessage(ReceiveMessageRequest.builder().queueUrl(ignoredQueueUrl).build()).join();
    }

    @Override
    protected String setupQueue() {
        CreateQueueResponse response = sqs.createQueue(CreateQueueRequest.builder().queueName(SQS_QUEUE_NAME).build());
        return response.queueUrl();
    }

    @Override
    protected String setupIgnoreQueue() {
        CreateQueueResponse response = sqs.createQueue(CreateQueueRequest.builder().queueName(SQS_IGNORED_QUEUE_NAME).build());
        return response.queueUrl();
    }

    @Override
    protected String awsService() {
        return "SQS";
    }

    @Override
    protected String type() {
        return "messaging";
    }

    @Override
    protected String subtype() {
        return "sqs";
    }

    @Nullable
    @Override
    protected String expectedTargetName(@Nullable String entityName) {
        return entityName; //entityName is queue name
    }

    @Override
    protected LocalStackContainer.Service localstackService() {
        return LocalStackContainer.Service.SQS;
    }
}
