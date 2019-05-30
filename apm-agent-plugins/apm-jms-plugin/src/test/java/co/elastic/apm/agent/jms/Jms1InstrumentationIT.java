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
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

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
public class Jms1InstrumentationIT extends AbstractJmsInstrumentationIT {

    private static Connection connection;
    private Session session;

    @BeforeClass
    public static void startQueueAndClient() throws JMSException {
        ConnectionFactory connectionFactory = new PooledConnectionFactory(
            new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false"));
        connection = connectionFactory.createConnection();
        connection.start();
    }

    @AfterClass
    public static void stopQueueAndClient() throws JMSException {
        connection.stop();
    }

    @Before
    public void startSession() throws JMSException {
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    }

    @After
    public void closeSession() throws JMSException {
        // This should also close underlying producers and consumers
        session.close();
    }

    @Override
    protected Queue createQueue(String queueName) throws JMSException {
        return session.createQueue(queueName);
    }

    @Override
    protected Topic createTopic(String topicName) throws JMSException {
        return session.createTopic(topicName);
    }

    @Override
    protected Message createTextMessage(String messageText) throws JMSException {
        return session.createTextMessage(messageText);
    }

    @Override
    protected void send(Destination destination, Message message) throws JMSException {
        session.createProducer(destination).send(message);
    }

    @Override
    protected CompletableFuture<Message> registerListener(Destination destination) throws JMSException {
        MessageConsumer consumer = session.createConsumer(destination);
        final CompletableFuture<Message> incomingMessageFuture = new CompletableFuture<>();
        //noinspection Convert2Lambda,Anonymous2MethodRef - we don't instrument lamdas or method references
        consumer.setMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message) {
                incomingMessageFuture.complete(message);
            }
        });
        return incomingMessageFuture;
    }

    @Override
    protected Message receive(Destination destination) throws JMSException {
        return session.createConsumer(destination).receive();
    }

    @Override
    protected Message receive(Destination destination, long timeout) throws JMSException {
        return session.createConsumer(destination).receive(timeout);
    }

    @Override
    protected Message receiveNoWait(Destination destination) throws JMSException {
        return session.createConsumer(destination).receiveNoWait();
    }
}
