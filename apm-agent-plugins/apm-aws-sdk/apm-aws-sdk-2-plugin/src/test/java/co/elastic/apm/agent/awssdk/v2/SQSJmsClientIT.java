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

import co.elastic.apm.agent.awssdk.common.AbstractAwsClientIT;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.MessagingConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.tracer.Scope;
import com.amazon.sqs.javamessaging.AmazonSQSMessagingClientWrapper;
import com.amazon.sqs.javamessaging.ProviderConfiguration;
import com.amazon.sqs.javamessaging.SQSConnection;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

import javax.annotation.Nullable;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class SQSJmsClientIT extends AbstractAwsClientIT {
    AmazonSQSMessagingClientWrapper client;
    SQSConnection connection;
    SQSConnection receivingConnection;
    SqsClient sqs;
    CoreConfiguration coreConfiguration;
    MessagingConfiguration messagingConfiguration;

    @BeforeEach
    public void setupClient() throws JMSException {
        coreConfiguration = tracer.getConfig(CoreConfiguration.class);
        messagingConfiguration = tracer.getConfig(MessagingConfiguration.class);

        sqs = SqsClient.builder().endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SQS))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                localstack.getAccessKey(), localstack.getSecretKey()
            )))
            .region(Region.of(localstack.getRegion())).build();


        SQSConnectionFactory connectionFactory = new SQSConnectionFactory(new ProviderConfiguration(), sqs);
        connection = connectionFactory.createConnection();
        receivingConnection = connectionFactory.createConnection();
        client = connection.getWrappedAmazonSQSClient();
    }

    @AfterEach
    public void tearDown() throws JMSException {
        connection.close();
        receivingConnection.close();
    }


    @Test
    public void testSend() throws JMSException {
        client.createQueue(SQS_QUEUE_NAME);

        Transaction transaction = startTestRootTransaction("sqs-test");

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = session.createQueue(SQS_QUEUE_NAME);
        final MessageProducer producer = session.createProducer(queue);
        final TextMessage message = session.createTextMessage(MESSAGE_BODY);

        producer.send(message);
        session.close();

        assertThat(reporter.getNumReportedSpans()).isEqualTo(2);
        assertThat(reporter.getSpanByName("SQS GetQueueUrl " + SQS_QUEUE_NAME).isChildOf(transaction)).isTrue();
        assertThat(reporter.getSpanByName("JMS SEND to queue " + SQS_QUEUE_NAME).isChildOf(transaction)).isTrue();

        transaction.deactivate().end();
    }

    @Test
    public void testReceiveWithinTransaction() throws JMSException {
        client.createQueue(SQS_QUEUE_NAME);

        // SEND message
        Transaction sendTransaction = startTestRootTransaction("sqs-test-send");

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = session.createQueue(SQS_QUEUE_NAME);
        final MessageProducer producer = session.createProducer(queue);
        final TextMessage message = session.createTextMessage(MESSAGE_BODY);

        producer.send(message);
        session.close();
        sendTransaction.deactivate().end();


        // RECEIVE Message
        Session receivingSession = receivingConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue receivingQueue = receivingSession.createQueue(SQS_QUEUE_NAME);
        final MessageConsumer consumer = receivingSession.createConsumer(receivingQueue);
        receivingConnection.start();


        Transaction receiveTransaction = startTestRootTransaction("sqs-test-receive");

        Message receivedMessage = consumer.receive(2000);
        assertThat(receivedMessage.getStringProperty("traceparent")).isNotNull();
        assertThat(receivedMessage.getStringProperty("tracestate")).isNotNull();
        assertThat(receivedMessage).isInstanceOf(TextMessage.class);
        assertThat(((TextMessage) receivedMessage).getText()).isEqualTo(MESSAGE_BODY);

        receivingSession.close();

        receiveTransaction.deactivate().end();

        assertThat(reporter.getNumReportedTransactions()).isEqualTo(2);
        assertThat(reporter.getNumReportedSpans()).isEqualTo(4);
        Span jmsReceiveSpan = reporter.getSpanByName("JMS RECEIVE from queue " + SQS_QUEUE_NAME);
        assertThat(reporter.getSpanByName("SQS DELETE from " + SQS_QUEUE_NAME).isChildOf(jmsReceiveSpan)).isTrue();
        assertThat(jmsReceiveSpan.isChildOf(receiveTransaction)).isTrue();
        assertThat(reporter.getSpans()).allMatch(AbstractSpan::isSync);
    }

    @Test
    public void testReceiveOutsideTransaction() throws JMSException {
        doReturn(MessagingConfiguration.JmsStrategy.POLLING).when(messagingConfiguration).getMessagePollingTransactionStrategy();
        client.createQueue(SQS_QUEUE_NAME);

        // SEND message
        Transaction sendTransaction = startTestRootTransaction("sqs-test-send");

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = session.createQueue(SQS_QUEUE_NAME);
        final MessageProducer producer = session.createProducer(queue);
        final TextMessage message = session.createTextMessage(MESSAGE_BODY);

        producer.send(message);
        session.close();
        sendTransaction.deactivate().end();


        // RECEIVE Message
        Session receivingSession = receivingConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue receivingQueue = receivingSession.createQueue(SQS_QUEUE_NAME);
        final MessageConsumer consumer = receivingSession.createConsumer(receivingQueue);
        receivingConnection.start();

        Message receivedMessage = consumer.receive(2000);
        assertThat(receivedMessage.getStringProperty("traceparent")).isNotNull();
        assertThat(receivedMessage.getStringProperty("tracestate")).isNotNull();
        assertThat(receivedMessage).isInstanceOf(TextMessage.class);
        assertThat(((TextMessage) receivedMessage).getText()).isEqualTo(MESSAGE_BODY);

        receivingSession.close();

        assertThat(reporter.getNumReportedTransactions()).isEqualTo(2);
        assertThat(reporter.getNumReportedSpans()).isEqualTo(3);
        assertThat(reporter.getSpanByName("SQS GetQueueUrl " + SQS_QUEUE_NAME).isChildOf(sendTransaction)).isTrue();
        assertThat(reporter.getSpanByName("JMS SEND to queue " + SQS_QUEUE_NAME).isChildOf(sendTransaction)).isTrue();
        reporter.getSpanByName("SQS DELETE from " + SQS_QUEUE_NAME);
        assertThat(reporter.getSpans()).allMatch(AbstractSpan::isSync);
    }

    @Test
    public void testOnMessage() throws JMSException {
        client.createQueue(SQS_QUEUE_NAME);

        // Setup Message listener
        Session receivingSession = receivingConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue receivingQueue = receivingSession.createQueue(SQS_QUEUE_NAME);
        final MessageConsumer consumer = receivingSession.createConsumer(receivingQueue);
        consumer.setMessageListener(new SQSListener());
        receivingConnection.start();

        // SEND message
        Transaction sendTransaction = startTestRootTransaction("sqs-test-send");

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = session.createQueue(SQS_QUEUE_NAME);
        final MessageProducer producer = session.createProducer(queue);
        final TextMessage message = session.createTextMessage(MESSAGE_BODY);

        producer.send(message);
        session.close();
        sendTransaction.deactivate().end();

        reporter.awaitTransactionCount(2);

        receivingSession.close();
        assertThat(reporter.getNumReportedTransactions()).isEqualTo(2);

        Optional<Transaction> optTransaction = reporter.getTransactions().stream().filter(t -> t.getNameAsString().startsWith("JMS RECEIVE")).findAny();
        assertThat(optTransaction.isPresent()).isTrue();
        Transaction receivingTransaction = optTransaction.get();

        assertThat(reporter.getNumReportedSpans()).isEqualTo(3);
        assertThat(reporter.getSpanByName("on-message-child").isChildOf(receivingTransaction)).isTrue();
        reporter.getSpanByName("JMS SEND to queue " + SQS_QUEUE_NAME);
        reporter.getSpanByName("SQS GetQueueUrl " + SQS_QUEUE_NAME);
        assertThat(reporter.getSpans()).allMatch(AbstractSpan::isSync);
    }

    @Test
    public void testOnMessageWithNonJmssender() throws JMSException {
        String queueUrl = client.createQueue(SQS_QUEUE_NAME).queueUrl();

        // Setup Message listener
        Session receivingSession = receivingConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue receivingQueue = receivingSession.createQueue(SQS_QUEUE_NAME);
        final MessageConsumer consumer = receivingSession.createConsumer(receivingQueue);
        consumer.setMessageListener(new SQSListener());
        receivingConnection.start();

        // SEND message
        Transaction sendTransaction = startTestRootTransaction("sqs-test-send");

        sqs.sendMessage(software.amazon.awssdk.services.sqs.model.SendMessageRequest.builder()
            .queueUrl(queueUrl)
            .messageAttributes(Collections.singletonMap("myKey", MessageAttributeValue.builder().dataType("String").stringValue("myValue").build()))
            .messageBody(MESSAGE_BODY)
            .build());

        sendTransaction.deactivate().end();

        reporter.awaitTransactionCount(2);

        receivingSession.close();
        assertThat(reporter.getNumReportedTransactions()).isEqualTo(2);

        Optional<Transaction> optTransaction = reporter.getTransactions().stream().filter(t -> t.getNameAsString().startsWith("JMS RECEIVE")).findAny();
        assertThat(optTransaction.isPresent()).isTrue();
        Transaction receivingTransaction = optTransaction.get();

        assertThat(reporter.getNumReportedSpans()).isEqualTo(2);
        assertThat(reporter.getSpanByName("on-message-child").isChildOf(receivingTransaction)).isTrue();
        reporter.getSpanByName("SQS SEND to " + SQS_QUEUE_NAME);
        assertThat(reporter.getSpans()).allMatch(AbstractSpan::isSync);
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

    static class SQSListener implements MessageListener {

        @Override
        public void onMessage(Message message) {
            try {
                Tracer tracer = GlobalTracer.get().require(ElasticApmTracer.class);
                assertThat(tracer).isNotNull();
                AbstractSpan<?> parent = tracer.getActive();
                assertThat(parent).isNotNull();
                Span child = parent.createSpan();
                assertThat(child).isNotNull();
                child.withName("on-message-child");
                try (Scope ignored = child.activateInScope()) {
                    // Do some work
                    ((TextMessage) message).getText();
                } finally {
                    child.end();
                }
            } catch (JMSException e) {
                e.printStackTrace();
            }
        }
    }
}
