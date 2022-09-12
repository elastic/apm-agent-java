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
import co.elastic.apm.agent.configuration.MessagingConfiguration;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
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

import java.util.Collections;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

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

        executeTest("CreateQueue", SQS_QUEUE_NAME, () -> sqs.createQueue(CreateQueueRequest.builder().queueName(SQS_QUEUE_NAME).build()));
        executeTest("ListQueues", null, () -> sqs.listQueues());
        final StringBuilder queueUrl = new StringBuilder();
        executeTest("GetQueueUrl", SQS_QUEUE_NAME, () ->
            queueUrl.append(sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(SQS_QUEUE_NAME).build()).queueUrl()));

        executeTest("SEND to", "send", SQS_QUEUE_NAME, () -> sqs.sendMessage(SendMessageRequest.builder()
            .queueUrl(queueUrl.toString())
            .messageAttributes(Collections.singletonMap("myKey", MessageAttributeValue.builder().dataType("String").stringValue("myValue").build()))
            .messageBody(MESSAGE_BODY)
            .build()), messagingAssert);

        final StringBuilder receiptHandle = new StringBuilder();
        executeTest("POLL from", "poll", SQS_QUEUE_NAME, () -> {
            ReceiveMessageResponse response = sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(queueUrl.toString()).build());
            assertThat(response.hasMessages()).isTrue();
            assertThat(response.messages().get(0).messageAttributes()).containsKey("traceparent");
            assertThat(response.messages().get(0).messageAttributes()).containsKey("tracestate");
            receiptHandle.append(response.messages().get(0).receiptHandle());
            return response;
        }, messagingAssert);

        executeTest("SEND_BATCH to", "send_batch", SQS_QUEUE_NAME, () -> sqs.sendMessageBatch(SendMessageBatchRequest.builder()
            .queueUrl(queueUrl.toString())
            .entries(
                SendMessageBatchRequestEntry.builder().id("first").messageBody(MESSAGE_BODY).build(),
                SendMessageBatchRequestEntry.builder().id("second").messageBody(MESSAGE_BODY).build())
            .build()), messagingAssert);


        executeTest("DELETE from", "delete", SQS_QUEUE_NAME, () -> sqs.deleteMessage(
            DeleteMessageRequest.builder()
                .queueUrl(queueUrl.toString()).receiptHandle(receiptHandle.toString())
                .build()));

        executeTest("DeleteQueue", SQS_QUEUE_NAME, () -> sqs.deleteQueue(DeleteQueueRequest.builder().queueUrl(queueUrl.toString()).build()));

        assertThat(reporter.getSpans()).allMatch(AbstractSpan::isSync);
        transaction.deactivate().end();
    }

    @Test
    public void testAsyncSQSClient() {
        Transaction transaction = startTestRootTransaction("sqs-test");

        executeTest("CreateQueue", SQS_QUEUE_NAME, () -> sqsAsync.createQueue(CreateQueueRequest.builder().queueName(SQS_QUEUE_NAME).build()));
        executeTest("ListQueues", null, () -> sqsAsync.listQueues());
        final StringBuilder queueUrl = new StringBuilder();
        executeTest("GetQueueUrl", SQS_QUEUE_NAME, () ->
            queueUrl.append(sqsAsync.getQueueUrl(GetQueueUrlRequest.builder().queueName(SQS_QUEUE_NAME).build()).join().queueUrl()));

        executeTest("SEND to", "send", SQS_QUEUE_NAME, () -> sqsAsync.sendMessage(SendMessageRequest.builder()
            .queueUrl(queueUrl.toString())
            .messageBody(MESSAGE_BODY)
            .build()), messagingAssert);

        final StringBuilder receiptHandle = new StringBuilder();
        executeTest("POLL from", "poll", SQS_QUEUE_NAME, () -> {
            ReceiveMessageResponse response = sqsAsync.receiveMessage(ReceiveMessageRequest.builder().queueUrl(queueUrl.toString()).build()).join();
            assertThat(response.hasMessages()).isTrue();
            assertThat(response.messages().get(0).messageAttributes()).containsKey("traceparent");
            assertThat(response.messages().get(0).messageAttributes()).containsKey("tracestate");
            receiptHandle.append(response.messages().get(0).receiptHandle());
            return response;
        }, messagingAssert);

        executeTest("SEND_BATCH to", "send_batch", SQS_QUEUE_NAME, () -> sqsAsync.sendMessageBatch(SendMessageBatchRequest.builder()
            .queueUrl(queueUrl.toString())
            .entries(
                SendMessageBatchRequestEntry.builder().id("first").messageBody(MESSAGE_BODY).build(),
                SendMessageBatchRequestEntry.builder().id("second").messageBody(MESSAGE_BODY).build())
            .build()), messagingAssert);


        executeTest("DELETE from", "delete", SQS_QUEUE_NAME, () -> sqsAsync.deleteMessage(
            DeleteMessageRequest.builder()
                .queueUrl(queueUrl.toString()).receiptHandle(receiptHandle.toString())
                .build()));

        executeTest("DeleteQueue", SQS_QUEUE_NAME, () -> sqsAsync.deleteQueue(DeleteQueueRequest.builder().queueUrl(queueUrl.toString()).build()));

        assertThat(reporter.getSpans()).noneMatch(AbstractSpan::isSync);
        transaction.deactivate().end();
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
    protected LocalStackContainer.Service localstackService() {
        return LocalStackContainer.Service.SQS;
    }
}
