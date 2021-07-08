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
package co.elastic.apm.agent.activemq;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.Scope;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;
import java.util.HashSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DestinationAddressAdviceTest extends AbstractInstrumentationTest {

    private static ActiveMQServerImpl activeMQServer;

    @BeforeAll
    static void setUp() throws Exception {
        HashSet<TransportConfiguration> transports = new HashSet<>();
        transports.add(new TransportConfiguration(InVMAcceptorFactory.class.getName()));
        transports.add(new TransportConfiguration(NettyAcceptorFactory.class.getName(), Map.of("port", 61616)));

        Configuration configuration = new ConfigurationImpl();
        configuration.setAcceptorConfigurations(transports);
        configuration.setSecurityEnabled(false);
        configuration.setPersistenceEnabled(false);

        activeMQServer = new ActiveMQServerImpl(configuration);
        activeMQServer.start();
    }

    @AfterAll
    static void tearDown() throws Exception {
        activeMQServer.stop();
    }

    @Test
    public void testNettyConnection() {
        Transaction transaction = startTestRootTransaction();
        try {
            ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");
            try (JMSContext context = connectionFactory.createContext()) {
                context.createProducer().send(context.createQueue("netty"), context.createMessage());
            }
        } finally {
            transaction.deactivate().end();
        }

        Span span = reporter.getFirstSpan(500);
        assertThat(span.getContext().getDestination().getAddress()).asString().isEqualTo("127.0.0.1");
        assertThat(span.getContext().getDestination().getPort()).isEqualTo(61616);
    }

    @Test
    public void testInVMConnection() {
        Transaction transaction = startTestRootTransaction();
        try {
            ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://0");
            try (JMSContext context = connectionFactory.createContext()) {
                context.createProducer().send(context.createQueue("vm"), context.createMessage());
            }
        } finally {
            transaction.deactivate().end();
        }

        Span span = reporter.getFirstSpan(500);
        assertThat(span.getContext().getDestination().getAddress()).isEmpty();
        assertThat(span.getContext().getDestination().getPort()).isEqualTo(0);
    }

    @Test
    public void testNoExitSpan() throws Exception {
        Transaction transaction = startTestRootTransaction();

        Span nonExitSpan = transaction.createSpan();
        try (Scope scope = nonExitSpan.activateInScope()) {
            ServerLocator locator = ActiveMQClient.createServerLocator("tcp://localhost:61616");

            ClientSession session = locator.createSessionFactory().createSession();

            ClientProducer producer = session.createProducer("no-exit-span");
            producer.send(session.createMessage(false));
        } finally {
            nonExitSpan.end();
            transaction.deactivate().end();
        }

        Span span = reporter.getFirstSpan(500);
        assertThat(span.getContext().getDestination().getAddress()).isEmpty();
        assertThat(span.getContext().getDestination().getPort()).isEqualTo(0);
    }
}
