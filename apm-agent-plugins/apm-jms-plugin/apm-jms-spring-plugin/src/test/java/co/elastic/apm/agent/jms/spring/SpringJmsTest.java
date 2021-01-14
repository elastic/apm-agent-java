/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent.jms.spring;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Id;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQMapMessage;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class SpringJmsTest extends AbstractInstrumentationTest {

    private static final String SPRING_TEST_QUEUE = "Spring-Test-Queue";
    static final BlockingQueue<Map<?, ?>> resultQueue = new ArrayBlockingQueue<>(5);

    private static Connection connection;
    private static ClassPathXmlApplicationContext ctx;

    @BeforeClass
    public static void setup() throws JMSException {
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
        connection = connectionFactory.createConnection();
        connection.start();
        ctx = new ClassPathXmlApplicationContext("app-context.xml");
    }

    @AfterClass
    public static void teardown() throws JMSException {
        ctx.close();
        connection.stop();
    }

    @Test
    public void testSendListenSpringQueue() throws JMSException, InterruptedException {
        try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

            Transaction transaction = tracer.startRootTransaction(null).activate();
            transaction.withName("JMS-Spring-Test Transaction")
                .withType("request")
                .withResult("success");

            final String key1 = "key1";
            final String key2 = "key2";
            final String value1 = UUID.randomUUID().toString();
            final String value2 = UUID.randomUUID().toString();
            MapMessage mapMessage = new ActiveMQMapMessage();
            mapMessage.setString(key1, value1);
            mapMessage.setString(key2, value2);

            Queue queue = session.createQueue(SPRING_TEST_QUEUE);
            MessageProducer producer = session.createProducer(queue);
            producer.send(mapMessage);

            // Let the onMessage instrumentation end the transaction then end the base transaction
            Thread.sleep(500);
            transaction.deactivate().end();

            Map<?, ?> result = resultQueue.poll(1, TimeUnit.SECONDS);

            assertThat(result).isNotNull();
            assertThat(result.size()).isEqualTo(2);
            assertThat(result.get(key1).toString()).isEqualTo(value1);
            assertThat(result.get(key2).toString()).isEqualTo(value2);

            List<Transaction> transactions = reporter.getTransactions();
            assertThat(transactions).hasSize(2);
            Transaction baseTransaction = transactions.get(1);
            Id traceId = baseTransaction.getTraceContext().getTraceId();

            List<Span> spans = reporter.getSpans();
            assertThat(spans).hasSize(1);
            Span sendSpan = spans.get(0);
            assertThat(sendSpan.getNameAsString()).isEqualTo("JMS SEND to queue " + SPRING_TEST_QUEUE);
            assertThat(sendSpan.getTraceContext().getTraceId()).isEqualTo(traceId);

            Transaction receiveTransaction = transactions.get(0);
            verifyReceiveTransaction(traceId, sendSpan, receiveTransaction);
        }
    }

    private void verifyReceiveTransaction(Id traceId, Span sendSpan, Transaction receiveTransaction) {
        assertThat(receiveTransaction.getNameAsString()).isEqualTo("JMS RECEIVE from queue " + SPRING_TEST_QUEUE);
        assertThat(receiveTransaction.getTraceContext().getTraceId()).isEqualTo(traceId);
        assertThat(receiveTransaction.getTraceContext().getParentId()).isEqualTo(sendSpan.getTraceContext().getId());
    }
}
