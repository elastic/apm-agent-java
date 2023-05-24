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
package co.elastic.apm.agent.awssdk.v1;

import co.elastic.apm.agent.awssdk.common.AbstractSQSClientIT;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.tracer.configuration.MessagingConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClient;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class SQSClientIT extends AbstractSQSClientIT {
    private AmazonSQS sqs;
    private AmazonSQSAsync sqsAsync;

    private final Consumer<Span> messagingAssert = span -> assertThat(span.getContext().getMessage().getQueueName()).isEqualTo(SQS_QUEUE_NAME);

    CoreConfiguration coreConfiguration;
    MessagingConfiguration messagingConfiguration;

    @BeforeEach
    public void setupClient() {
        coreConfiguration = tracer.getConfig(CoreConfiguration.class);
        messagingConfiguration = tracer.getConfig(MessagingConfiguration.class);

        sqs = AmazonSQSClient.builder()
            .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(localstack.getEndpointOverride(LocalStackContainer.Service.SQS).toString(), localstack.getRegion()))
            .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(localstack.getAccessKey(), localstack.getSecretKey())))
            .build();

        sqsAsync = AmazonSQSAsyncClient.asyncBuilder()
            .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString(), localstack.getRegion()))
            .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(localstack.getAccessKey(), localstack.getSecretKey())))
            .build();
    }

    @Test
    public void testSQSClient() {
        Transaction transaction = startTestRootTransaction("sqs-test");

        newTest(() -> sqs.createQueue(SQS_QUEUE_NAME))
            .operationName("CreateQueue")
            .entityName(SQS_QUEUE_NAME)
            .action("CreateQueue")
            .withSpanAssertions(messagingAssert)
            .execute();

        newTest(() -> sqs.listQueues())
            .operationName("ListQueues")
            .execute();

        final StringBuilder queueUrl = new StringBuilder();

        newTest(() -> queueUrl.append(sqs.getQueueUrl(SQS_QUEUE_NAME).getQueueUrl()))
            .operationName("GetQueueUrl")
            .entityName(SQS_QUEUE_NAME)
            .action("GetQueueUrl")
            .withSpanAssertions(messagingAssert)
            .execute();

        newTest(() -> sqs.sendMessage(
            new SendMessageRequest(queueUrl.toString(), MESSAGE_BODY)))
            .operationName("SEND to")
            .entityName(SQS_QUEUE_NAME)
            .action("send")
            .withSpanAssertions(messagingAssert)
            .execute();

        final StringBuilder receiptHandle = new StringBuilder();

        newTest(() -> {
            ReceiveMessageResult result = sqs.receiveMessage(queueUrl.toString());
            assertThat(result.getMessages().size()).isEqualTo(1);
            Message message = result.getMessages().get(0);
            assertThat(message.getMessageAttributes()).containsKey("traceparent");
            assertThat(message.getMessageAttributes()).containsKey("tracestate");
            receiptHandle.append(message.getReceiptHandle());
            return result;
        })
            .operationName("POLL from")
            .entityName(SQS_QUEUE_NAME)
            .action("poll")
            .withSpanAssertions(messagingAssert)
            .execute();

        List<SendMessageBatchRequestEntry> batchMessages = new ArrayList<>();
        batchMessages.add(new SendMessageBatchRequestEntry("first", MESSAGE_BODY));
        batchMessages.add(new SendMessageBatchRequestEntry("second", MESSAGE_BODY));

        newTest(() -> sqs.sendMessageBatch(queueUrl.toString(), batchMessages))
            .operationName("SEND_BATCH to")
            .entityName(SQS_QUEUE_NAME)
            .action("send_batch")
            .withSpanAssertions(messagingAssert)
            .execute();

        newTest(() -> sqs.deleteMessage(queueUrl.toString(), receiptHandle.toString()))
            .operationName("DELETE from")
            .entityName(SQS_QUEUE_NAME)
            .action("delete")
            .withSpanAssertions(messagingAssert)
            .execute();

        newTest(() -> sqs.deleteQueue(queueUrl.toString()))
            .operationName("DeleteQueue")
            .entityName(SQS_QUEUE_NAME)
            .action("DeleteQueue")
            .withSpanAssertions(messagingAssert)
            .execute();

        assertThat(reporter.getSpans()).hasSize(8);

        transaction.deactivate().end();
    }

    @Test
    public void testAsyncSQSClient() {
        Transaction transaction = startTestRootTransaction("sqs-test");

        newTest(() -> sqsAsync.createQueueAsync(SQS_QUEUE_NAME))
            .operationName("CreateQueue")
            .entityName(SQS_QUEUE_NAME)
            .action("CreateQueue")
            .withSpanAssertions(messagingAssert)
            .async()
            .execute();

        newTest(() -> sqsAsync.listQueuesAsync())
            .operationName("ListQueues")
            .async()
            .execute();

        final StringBuilder queueUrl = new StringBuilder();

        newTest(() -> {
            try {
                return queueUrl.append(sqsAsync.getQueueUrlAsync(SQS_QUEUE_NAME).get().getQueueUrl());
            } catch (Exception e1) {
                throw new RuntimeException(e1);
            }
        })
            .operationName("GetQueueUrl")
            .entityName(SQS_QUEUE_NAME)
            .action("GetQueueUrl")
            .withSpanAssertions(messagingAssert)
            .async()
            .execute();

        //.addMessageAttributesEntry("myKey", new MessageAttributeValue().withDataType("String").withStringValue("myValue"))

        newTest(() -> sqsAsync.sendMessageAsync(
            new SendMessageRequest(queueUrl.toString(), MESSAGE_BODY)
            //.addMessageAttributesEntry("myKey", new MessageAttributeValue().withDataType("String").withStringValue("myValue"))
        ))
            .operationName("SEND to")
            .entityName(SQS_QUEUE_NAME)
            .action("send")
            .withSpanAssertions(messagingAssert)
            .async()
            .execute();

        final StringBuilder receiptHandle = new StringBuilder();

        newTest(() -> {
            ReceiveMessageResult result;
            try {
                result = sqsAsync.receiveMessageAsync(queueUrl.toString()).get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            assertThat(result.getMessages().size()).isEqualTo(1);
            Message message = result.getMessages().get(0);
            assertThat(message.getMessageAttributes()).containsKey("traceparent");
            assertThat(message.getMessageAttributes()).containsKey("tracestate");
            receiptHandle.append(message.getReceiptHandle());
            return result;
        })
            .operationName("POLL from")
            .entityName(SQS_QUEUE_NAME)
            .action("poll")
            .withSpanAssertions(messagingAssert)
            .async()
            .execute();

        List<SendMessageBatchRequestEntry> batchMessages = new ArrayList<>();
        batchMessages.add(new SendMessageBatchRequestEntry("first", MESSAGE_BODY));
        batchMessages.add(new SendMessageBatchRequestEntry("second", MESSAGE_BODY));

        newTest(() -> sqsAsync.sendMessageBatchAsync(queueUrl.toString(), batchMessages))
            .operationName("SEND_BATCH to")
            .entityName(SQS_QUEUE_NAME)
            .action("send_batch")
            .withSpanAssertions(messagingAssert)
            .async()
            .execute();

        newTest(() -> sqsAsync.deleteMessageAsync(queueUrl.toString(), receiptHandle.toString()))
            .operationName("DELETE from")
            .entityName(SQS_QUEUE_NAME)
            .action("delete")
            .withSpanAssertions(messagingAssert)
            .async()
            .execute();

        newTest(() -> sqsAsync.deleteQueueAsync(queueUrl.toString()))
            .operationName("DeleteQueue")
            .entityName(SQS_QUEUE_NAME)
            .action("DeleteQueue")
            .withSpanAssertions(messagingAssert)
            .async()
            .execute();

        assertThat(reporter.getSpans()).hasSize(8);

        transaction.deactivate().end();
    }

    @Override
    public void sendMessage(String queueUrl) {
        sqs.sendMessage(queueUrl, MESSAGE_BODY);
    }

    @Override
    public void receiveMessages(String queueUrl, int numMessages) {
        ReceiveMessageRequest request = new ReceiveMessageRequest()
            .withQueueUrl(queueUrl).withMaxNumberOfMessages(numMessages);
        ReceiveMessageResult response = sqs.receiveMessage(request);
        for (Message message : response.getMessages()) {
            executeChildSpan();
        }
    }

    @Override
    public void receiveMessagesAsync(String queueUrl, int numMessages) {
        try {
            ReceiveMessageRequest request = new ReceiveMessageRequest()
                .withQueueUrl(queueUrl).withMaxNumberOfMessages(numMessages);

            ReceiveMessageResult response = sqsAsync.receiveMessageAsync(request).get(5, TimeUnit.SECONDS);
            for (Message message : response.getMessages()) {
                executeChildSpan();
            }
        } catch (Exception e) {
            fail("Unexpected exception!", e);
        }
    }

    @Override
    public void executeQueueExclusion(String queueUrl, String ignoredQueueUrl) {
        sqs.sendMessage(queueUrl, MESSAGE_BODY);
        sqs.sendMessage(ignoredQueueUrl, MESSAGE_BODY);
        sqs.receiveMessage(queueUrl);
        sqs.receiveMessage(ignoredQueueUrl);
    }

    @Override
    public void executeAsyncQueueExclusion(String queueUrl, String ignoredQueueUrl) {
        try {
            sqsAsync.sendMessageAsync(queueUrl, MESSAGE_BODY).get(5, TimeUnit.SECONDS);
            sqsAsync.sendMessageAsync(ignoredQueueUrl, MESSAGE_BODY).get(5, TimeUnit.SECONDS);
            sqsAsync.receiveMessageAsync(queueUrl).get(5, TimeUnit.SECONDS);
            sqsAsync.receiveMessageAsync(ignoredQueueUrl).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            fail("Unexpected exception!", e);
        }
    }

    @Override
    protected String setupQueue() {
        return sqs.createQueue(SQS_QUEUE_NAME).getQueueUrl();
    }

    @Override
    protected String setupIgnoreQueue() {
        return sqs.createQueue(SQS_IGNORED_QUEUE_NAME).getQueueUrl();
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
