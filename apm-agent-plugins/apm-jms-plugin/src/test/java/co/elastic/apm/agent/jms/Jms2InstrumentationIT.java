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

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import javax.jms.Destination;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.Topic;
import java.io.File;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;

/**
 * ActiveMQ Artemis tests for JMS 2 API
 */
public class Jms2InstrumentationIT extends AbstractJmsInstrumentationIT {

    private static ActiveMQConnectionFactory connectionFactory;
    private static ActiveMQServerImpl activeMQServer;
    private JMSContext context;

    @BeforeClass
    public static void startQueueAndClient() throws Exception {
        Configuration configuration = new ConfigurationImpl();

        HashSet<TransportConfiguration> transports = new HashSet<>();
        transports.add(new TransportConfiguration(InVMAcceptorFactory.class.getName()));
        configuration.setAcceptorConfigurations(transports);
        configuration.setSecurityEnabled(false);

        File targetDir = new File(System.getProperty("user.dir") + "/target");
        configuration.setBrokerInstance(targetDir);
        configuration.setPersistenceEnabled(false);

        activeMQServer = new ActiveMQServerImpl(configuration);
        activeMQServer.start();

        connectionFactory = new ActiveMQConnectionFactory("vm://0");
    }

    @AfterClass
    public static void stopQueueAndClient() throws Exception {
        activeMQServer.stop();
    }

    @Before
    public void startContext() {
        context = connectionFactory.createContext();
    }

    @After
    public void closeContext() {
        // This should also close underlying producers and consumers
        context.close();
    }

    @Override
    protected Queue createQueue(String queueName) {
        return context.createQueue(queueName);
    }

    @Override
    protected Topic createTopic(String topicName) {
        return context.createTopic(topicName);
    }

    @Override
    protected Message createTextMessage(String messageText) {
        return context.createTextMessage(messageText);
    }

    @Override
    protected void send(Destination destination, Message message) {
        context.createProducer().send(destination, message);
    }

    @Override
    protected CompletableFuture<Message> registerConcreteListenerImplementation(Destination destination) {
        JMSConsumer consumer = context.createConsumer(destination);
        final CompletableFuture<Message> incomingMessageFuture = new CompletableFuture<>();
        //noinspection Convert2Lambda,Anonymous2MethodRef
        consumer.setMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message) {
                incomingMessageFuture.complete(message);
            }
        });
        return incomingMessageFuture;
    }

    @Override
    protected CompletableFuture<Message> registerListenerLambda(Destination destination) {
        JMSConsumer consumer = context.createConsumer(destination);
        final CompletableFuture<Message> incomingMessageFuture = new CompletableFuture<>();
        // ActiveMQ Artemis wraps listeners with actual MessageListener instances
        // of org.apache.activemq.artemis.jms.client.ActiveMQJMSConsumer$MessageListenerWrapper anyway..
        //noinspection Convert2MethodRef
        consumer.setMessageListener(message -> incomingMessageFuture.complete(message));
        return incomingMessageFuture;
    }

    @Override
    protected CompletableFuture<Message> registerListenerMethodReference(Destination destination) {
        JMSConsumer consumer = context.createConsumer(destination);
        final CompletableFuture<Message> incomingMessageFuture = new CompletableFuture<>();
        // ActiveMQ Artemis wraps listeners with actual MessageListener instances
        // of org.apache.activemq.artemis.jms.client.ActiveMQJMSConsumer$MessageListenerWrapper anyway..
        consumer.setMessageListener(incomingMessageFuture::complete);
        return incomingMessageFuture;
    }

    @Override
    protected Message receive(Destination destination) {
        return context.createConsumer(destination).receive();
    }

    @Override
    protected Message receive(Destination destination, long timeout) {
        return context.createConsumer(destination).receive(timeout);
    }

    @Override
    protected Message receiveNoWait(Destination destination) {
        return context.createConsumer(destination).receiveNoWait();
    }
}
