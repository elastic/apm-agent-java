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
package co.elastic.apm.agent.jms;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TemporaryQueue;
import jakarta.jms.TemporaryTopic;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * ActiveMQ tests for JMS 1 API.
 * Testing with the Pooled connection factory adds testing for name-based pre-matcher-filter, and tests for nested receives
 */
// TODO Need to wait until Artemis is fully on jakarta.jms,
//  currently ActiveMQConnectionFacto
class JakartaActiveMqFacade implements JakartaBrokerFacade {

    private Connection connection;
    private Session session;
    private final Map<Destination, MessageConsumer> consumerCache = new HashMap<>();

    @Override
    public void prepareResources() throws JMSException {
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
        connection = connectionFactory.createConnection();
        connection.start();
    }

    @Override
    public void closeResources() throws JMSException {
        connection.stop();
    }

    @Override
    public void beforeTest() throws JMSException {
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    }

    @Override
    public void afterTest() throws JMSException {
        // This should also close underlying producers and consumers
        session.close();
        consumerCache.clear();
    }

    @Override
    public Queue createQueue(String queueName) throws JMSException {
        return session.createQueue(queueName);
    }

    @Override
    public TemporaryQueue createTempQueue() throws Exception {
        return session.createTemporaryQueue();
    }

    @Override
    public Topic createTopic(String topicName) throws JMSException {
        return session.createTopic(topicName);
    }

    @Override
    public TemporaryTopic createTempTopic() throws Exception {
        return session.createTemporaryTopic();
    }

    @Override
    public TextMessage createTextMessage(String messageText) throws JMSException {
        TextMessage message = session.createTextMessage(messageText);
        message.setStringProperty("test_string_property", "test123");
        message.setIntProperty("test_int_property", 123);
        message.setStringProperty("passwd", "secret");
        message.setStringProperty("null_property", null);
        return message;
    }

    @Override
    public void send(Destination destination, Message message, boolean disableTimestamp) throws JMSException {
        MessageProducer producer = session.createProducer(destination);
        if (disableTimestamp) {
            producer.setDisableMessageTimestamp(true);
        }
        producer.send(message);
    }

    @Override
    public CompletableFuture<Message> registerConcreteListenerImplementation(Destination destination) {
        final CompletableFuture<Message> incomingMessageFuture = new CompletableFuture<>();
        try {
            MessageConsumer consumer = session.createConsumer(destination);
            //noinspection Convert2Lambda,Anonymous2MethodRef
            consumer.setMessageListener(new MessageListener() {
                @Override
                public void onMessage(Message message) {
                    incomingMessageFuture.complete(message);
                }
            });
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
        return incomingMessageFuture;
    }

    @Override
    public CompletableFuture<Message> registerListenerLambda(Destination destination) {
        final CompletableFuture<Message> incomingMessageFuture = new CompletableFuture<>();
        try {
            MessageConsumer consumer = session.createConsumer(destination);
            //noinspection Convert2MethodRef
            consumer.setMessageListener(message -> incomingMessageFuture.complete(message));
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
        return incomingMessageFuture;
    }

    @Override
    public CompletableFuture<Message> registerListenerMethodReference(Destination destination) {
        final CompletableFuture<Message> incomingMessageFuture = new CompletableFuture<>();
        try {
            MessageConsumer consumer = session.createConsumer(destination);
            consumer.setMessageListener(incomingMessageFuture::complete);
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
        return incomingMessageFuture;
    }

    @Override
    public Message receive(Destination destination) throws JMSException {
        return getOrCreateQueueConsumer(destination).receive();
    }

    @Override
    public Message receive(Destination destination, long timeout) throws JMSException {
        return getOrCreateQueueConsumer(destination).receive(timeout);
    }

    @Override
    public boolean shouldTestReceiveNoWait() {
        return false;
    }

    @Override
    public Message receiveNoWait(Destination destination) throws JMSException {
        return getOrCreateQueueConsumer(destination).receiveNoWait();
    }

    private MessageConsumer getOrCreateQueueConsumer(Destination destination) throws JMSException {
        MessageConsumer consumer = consumerCache.get(destination);
        if (consumer == null) {
            consumer = session.createConsumer(destination);
            consumerCache.put(destination, consumer);
        }
        return consumer;
    }

}
