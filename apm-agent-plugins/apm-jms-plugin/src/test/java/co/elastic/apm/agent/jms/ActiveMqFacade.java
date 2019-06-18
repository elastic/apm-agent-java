/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.jms;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.pool.PooledConnectionFactory;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;
import java.util.concurrent.CompletableFuture;

/**
 * ActiveMQ tests for JMS 1 API.
 * Testing with the Pooled connection factory adds testing for name-based pre-matcher-filter, and tests for nested receives
 */
class ActiveMqFacade implements BrokerFacade {

    private Connection connection;
    private Session session;

    @Override
    public void prepareResources() throws JMSException {
        ConnectionFactory connectionFactory = new PooledConnectionFactory(
            new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false"));
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
    }

    @Override
    public Queue createQueue(String queueName) throws JMSException {
        return session.createQueue(queueName);
    }

    @Override
    public Topic createTopic(String topicName) throws JMSException {
        return session.createTopic(topicName);
    }

    @Override
    public Message createTextMessage(String messageText) throws JMSException {
        return session.createTextMessage(messageText);
    }

    @Override
    public void send(Destination destination, Message message) throws JMSException {
        session.createProducer(destination).send(message);
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
        return session.createConsumer(destination).receive();
    }

    @Override
    public Message receive(Destination destination, long timeout) throws JMSException {
        return session.createConsumer(destination).receive(timeout);
    }

    @Override
    public Message receiveNoWait(Destination destination) throws JMSException {
        return session.createConsumer(destination).receiveNoWait();
    }
}
