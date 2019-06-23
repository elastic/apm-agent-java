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


import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Id;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.TextMessage;
import javax.jms.Topic;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class JmsInstrumentationIT extends AbstractInstrumentationTest {

    // Keeping a static reference for resource cleaning
    private static Set<BrokerFacade> staticBrokerFacade = new HashSet<>();

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final BrokerFacade brokerFacade;

    public JmsInstrumentationIT(BrokerFacade brokerFacade) throws Exception {
        this.brokerFacade = brokerFacade;
        if (staticBrokerFacade.add(brokerFacade)) {
            brokerFacade.prepareResources();
        }
    }

    @Parameterized.Parameters(name = "BrokerFacade={0}")
    public static Iterable<Object[]> brokerFacades() {
        return Arrays.asList(new Object[][]{{new ActiveMqFacade()}, {new ActiveMqArtemisFacade()}});
    }

    @AfterClass
    public static void closeResources() throws Exception {
        for (BrokerFacade brokerFacade : staticBrokerFacade) {
            brokerFacade.closeResources();
        }
        staticBrokerFacade.clear();
    }

    @Before
    public void startTransaction() throws Exception {
        reporter.reset();
        Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null).activate();
        transaction.setName("JMS-Test Transaction");
        transaction.withType("request");
        transaction.withResult("success");
        brokerFacade.beforeTest();
    }

    @After
    public void endTransaction() throws Exception {
        Transaction currentTransaction = tracer.currentTransaction();
        if (currentTransaction != null) {
            currentTransaction.deactivate().end();
        }
        brokerFacade.afterTest();
        reporter.reset();
    }

    @Test
    public void testQueueSendReceiveOnTracedThread() throws Exception {
        final Queue queue = createQueue();
        testQueueSendReceiveOnTracedThread(() -> brokerFacade.receive(queue, 2000), queue, false);
    }

    @Test
    public void testQueueSendReceiveNoWaitOnTracedThread() throws Exception {
        final Queue queue = createQueue();
        testQueueSendReceiveOnTracedThread(() -> brokerFacade.receiveNoWait(queue), queue, true);
    }

    @Test
    public void testQueueSendReceiveOnNonTracedThread() throws Exception {
        final Queue queue = createQueue();
        testQueueSendReceiveOnNonTracedThread(() -> brokerFacade.receive(queue, 2000), queue, false);
    }

    @Test
    public void testQueueSendReceiveNoWaitOnNonTracedThread() throws Exception {
        final Queue queue = createQueue();
        testQueueSendReceiveOnNonTracedThread(() -> brokerFacade.receiveNoWait(queue), queue, true);
    }

    private Queue createQueue() throws Exception {
        String queueName = UUID.randomUUID().toString();
        return brokerFacade.createQueue(queueName);
    }

    private Topic createTopic() throws Exception {
        String topicName = UUID.randomUUID().toString();
        return brokerFacade.createTopic(topicName);
    }

    private void testQueueSendReceiveOnTracedThread(Callable<Message> receiveMethod, Queue queue, boolean waitAfterSend) throws Exception {
        String message = UUID.randomUUID().toString();
        Message outgoingMessage = brokerFacade.createTextMessage(message);
        brokerFacade.send(queue, outgoingMessage);
        if (waitAfterSend) {
            Thread.sleep(100);
        }
        Message incomingMessage = receiveOnTracedThread(receiveMethod).get(3, TimeUnit.SECONDS);
        verifyMessage(message, incomingMessage);

        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(2);
        Iterator<Span> iterator = spans.iterator();
        Span sendSpan = iterator.next();
        assertThat(sendSpan.getName().toString()).isEqualTo("JMS SEND to queue " + queue.getQueueName());
        Span receiveSpan = iterator.next();
        assertThat(receiveSpan.getName().toString()).isEqualTo("JMS RECEIVE from queue " + queue.getQueueName());

        //noinspection ConstantConditions
        Id currentTraceId = tracer.currentTransaction().getTraceContext().getTraceId();
        assertThat(sendSpan.getTraceContext().getTraceId()).isEqualTo(currentTraceId);

        Transaction receiveTransaction = reporter.getFirstTransaction();
        assertThat(receiveTransaction.getTraceContext().getTraceId()).isNotEqualTo(currentTraceId);
        assertThat(receiveSpan.getTraceContext().getTraceId()).isEqualTo(receiveTransaction.getTraceContext().getTraceId());
        assertThat(receiveSpan.getTraceContext().getParentId()).isEqualTo(receiveTransaction.getTraceContext().getId());

    }

    private void testQueueSendReceiveOnNonTracedThread(Callable<Message> receiveMethod, Queue queue, boolean waitAfterSend)
        throws Exception {
        String message = UUID.randomUUID().toString();
        Message outgoingMessage = brokerFacade.createTextMessage(message);
        brokerFacade.send(queue, outgoingMessage);
        if (waitAfterSend) {
            Thread.sleep(100);
        }
        Message incomingMessage = receiveOnNonTracedThread(receiveMethod).get(3, TimeUnit.SECONDS);
        verifyMessage(message, incomingMessage);
        verifySendReceiveOnNonTracedThread(queue.getQueueName(), 1);
    }

    private void verifySendReceiveOnNonTracedThread(String destinationName, int expectedReadTransactions) {
        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        Span sendSpan = spans.get(0);
        assertThat(sendSpan.getName().toString()).startsWith("JMS SEND to ");
        assertThat(sendSpan.getName().toString()).endsWith(destinationName);

        //noinspection ConstantConditions
        Id currentTraceId = tracer.currentTransaction().getTraceContext().getTraceId();
        assertThat(sendSpan.getTraceContext().getTraceId()).isEqualTo(currentTraceId);

        List<Transaction> receiveTransactions = reporter.getTransactions();
        assertThat(receiveTransactions).hasSize(expectedReadTransactions);
        for (Transaction receiveTransaction : receiveTransactions) {
            assertThat(receiveTransaction.getName().toString()).startsWith("JMS RECEIVE from ");
            assertThat(receiveTransaction.getName().toString()).endsWith(destinationName);
            assertThat(receiveTransaction.getTraceContext().getTraceId()).isEqualTo(currentTraceId);
            assertThat(receiveTransaction.getTraceContext().getParentId()).isEqualTo(sendSpan.getTraceContext().getId());
        }
    }

    @Test
    public void testRegisterConcreteListenerImpl() {
        try {
            testQueueSendListen(brokerFacade::registerConcreteListenerImplementation);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testRegisterListenerLambda() {
        try {
            testQueueSendListen(brokerFacade::registerListenerLambda);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testRegisterListenerMethodReference() {
        try {
            testQueueSendListen(brokerFacade::registerListenerMethodReference);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void testQueueSendListen(Function<Destination, CompletableFuture<Message>> listenerRegistrationFunction)
        throws Exception {
        Queue queue = createQueue();
        CompletableFuture<Message> incomingMessageFuture = listenerRegistrationFunction.apply(queue);
        String message = UUID.randomUUID().toString();
        Message outgoingMessage = brokerFacade.createTextMessage(message);
        brokerFacade.send(queue, outgoingMessage);
        Message incomingMessage = incomingMessageFuture.get(3, TimeUnit.SECONDS);
        verifyMessage(message, incomingMessage);
        Thread.sleep(500);
        verifySendReceiveOnNonTracedThread(queue.getQueueName(), 1);
    }

    @Test
    public void testTopicWithTwoSubscribers() throws Exception {
        Topic topic = createTopic();

        final CompletableFuture<Message> incomingMessageFuture1 = brokerFacade.registerConcreteListenerImplementation(topic);
        final CompletableFuture<Message> incomingMessageFuture2 = brokerFacade.registerConcreteListenerImplementation(topic);

        String message = UUID.randomUUID().toString();
        Message outgoingMessage = brokerFacade.createTextMessage(message);
        brokerFacade.send(topic, outgoingMessage);

        Message incomingMessage1 = incomingMessageFuture1.get(3, TimeUnit.SECONDS);
        verifyMessage(message, incomingMessage1);

        Message incomingMessage2 = incomingMessageFuture2.get(3, TimeUnit.SECONDS);
        verifyMessage(message, incomingMessage2);

        Thread.sleep(500);
        verifySendReceiveOnNonTracedThread(topic.getTopicName(), 2);
    }

    private void verifyMessage(String expectedText, Message message) throws JMSException {
        assertThat(message).isInstanceOf(TextMessage.class);
        assertThat(((TextMessage) message).getText()).isEqualTo(expectedText);
    }

    /**
     * Receive on a non-traced thread, i.e. where no transaction is traced and receive event should be traced as the
     * thread's transaction.
     *
     * @param receiveMethod receive method to use
     * @return received Message
     */
    private Future<Message> receiveOnNonTracedThread(Callable<Message> receiveMethod) {
        return executor.submit(receiveMethod);
    }

    /**
     * Receive on a traced thread, i.e. where a transaction is traced and receive event should be traced as a span.
     *
     * @param receiveMethod receive method to use
     * @return received Message
     */
    private Future<Message> receiveOnTracedThread(final Callable<Message> receiveMethod) {
        return executor.submit(() -> {
            Transaction transaction = null;
            Message message = null;
            try {
                transaction = tracer.startTransaction(TraceContext.asRoot(), null, null)
                    .withName("JMS-Test Receiver Transaction")
                    .activate();
                message = receiveMethod.call();
            } finally {
                if (transaction != null) {
                    transaction.deactivate().end();
                }
            }
            return message;
        });
    }
}
