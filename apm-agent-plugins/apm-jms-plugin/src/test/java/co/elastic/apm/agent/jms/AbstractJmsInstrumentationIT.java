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
import org.junit.Before;
import org.junit.Test;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.TextMessage;
import javax.jms.Topic;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractJmsInstrumentationIT extends AbstractInstrumentationTest {

    private static final String TEST_Q_NAME = "TestQ";
    private static final String TEST_TOPIC_NAME = "Test-Topic";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Before
    public void startTransaction() {
        reporter.reset();
        Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null).activate();
        transaction.setName("JMS-Test Transaction");
        transaction.withType("request");
        transaction.withResult("success");
    }

    @After
    public void endTransaction() {
        Transaction currentTransaction = tracer.currentTransaction();
        if (currentTransaction != null) {
            currentTransaction.deactivate().end();
        }
        reporter.reset();
    }

    @Test
    public void testQueueSendReceiveOnTracedThread() throws JMSException, ExecutionException,
        InterruptedException, TimeoutException {
        final Queue queue = createQueue(TEST_Q_NAME);
        testQueueSendReceiveOnTracedThread(() -> receive(queue), queue);
    }

    @Test
    public void testQueueSendReceiveNoWaitOnTracedThread() throws JMSException, ExecutionException, InterruptedException, TimeoutException {
        final Queue queue = createQueue(TEST_Q_NAME);
        testQueueSendReceiveOnTracedThread(() -> receiveNoWait(queue), queue);
    }

    @Test
    public void testQueueSendReceiveOnNonTracedThread() throws JMSException, ExecutionException,
        InterruptedException, TimeoutException {
        final Queue queue = createQueue(TEST_Q_NAME);
        testQueueSendReceiveOnNonTracedThread(() -> receive(queue), queue);
    }

    @Test
    public void testQueueSendReceiveNoWaitOnNonTracedThread() throws JMSException, ExecutionException, InterruptedException, TimeoutException {
        final Queue queue = createQueue(TEST_Q_NAME);
        testQueueSendReceiveOnNonTracedThread(() -> receiveNoWait(queue), queue);
    }

    private void testQueueSendReceiveOnTracedThread(Callable<Message> receiveMethod, Queue queue)
        throws JMSException, ExecutionException, InterruptedException, TimeoutException {
        String message = UUID.randomUUID().toString();
        Message outgoingMessage = createTextMessage(message);
        send(queue, outgoingMessage);
        Message incomingMessage = receiveOnTracedThread(receiveMethod).get(1, TimeUnit.SECONDS);
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

    private void testQueueSendReceiveOnNonTracedThread(Callable<Message> receiveMethod, Queue queue)
        throws JMSException, ExecutionException, InterruptedException, TimeoutException {
        String message = UUID.randomUUID().toString();
        Message outgoingMessage = createTextMessage(message);
        send(queue, outgoingMessage);
        Message incomingMessage = receiveOnNonTracedThread(receiveMethod).get(1, TimeUnit.SECONDS);
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
            testQueueSendListen(this::registerConcreteListenerImplementation);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testRegisterListenerLambda() {
        try {
            testQueueSendListen(this::registerListenerLambda);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testRegisterListenerMethodReference() {
        try {
            testQueueSendListen(this::registerListenerMethodReference);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void testQueueSendListen(Function<Destination, CompletableFuture<Message>> listenerRegistrationFunction)
        throws JMSException, ExecutionException, InterruptedException, TimeoutException {
        Queue queue = createQueue(TEST_Q_NAME);
        CompletableFuture<Message> incomingMessageFuture = listenerRegistrationFunction.apply(queue);
        String message = UUID.randomUUID().toString();
        Message outgoingMessage = createTextMessage(message);
        send(queue, outgoingMessage);
        Message incomingMessage = incomingMessageFuture.get(1, TimeUnit.SECONDS);
        verifyMessage(message, incomingMessage);
        Thread.sleep(500);
        verifySendReceiveOnNonTracedThread(queue.getQueueName(), 1);
    }

    @Test
    public void testTopicWithTwoSubscribers() throws JMSException, ExecutionException, InterruptedException, TimeoutException {
        Topic topic = createTopic(TEST_TOPIC_NAME);

        final CompletableFuture<Message> incomingMessageFuture1 = registerConcreteListenerImplementation(topic);
        final CompletableFuture<Message> incomingMessageFuture2 = registerConcreteListenerImplementation(topic);

        String message = UUID.randomUUID().toString();
        Message outgoingMessage = createTextMessage(message);
        send(topic, outgoingMessage);

        Message incomingMessage1 = incomingMessageFuture1.get(1, TimeUnit.SECONDS);
        verifyMessage(message, incomingMessage1);

        Message incomingMessage2 = incomingMessageFuture2.get(1, TimeUnit.SECONDS);
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
            Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null)
                .withName("JMS-Test Receiver Transaction")
                .activate();
            Message message = receiveMethod.call();
            transaction.deactivate().end();
            return message;
        });
    }

    protected abstract Queue createQueue(String queueName) throws JMSException;

    protected abstract Topic createTopic(String topicName) throws JMSException;

    protected abstract Message createTextMessage(String messageText) throws JMSException;

    protected abstract void send(Destination destination, Message message) throws JMSException;

    protected abstract CompletableFuture<Message> registerConcreteListenerImplementation(Destination destination);

    protected abstract CompletableFuture<Message> registerListenerLambda(Destination destination);

    protected abstract CompletableFuture<Message> registerListenerMethodReference(Destination destination);

    protected abstract Message receive(Destination destination) throws JMSException;

    protected abstract Message receive(Destination destination, long timeout) throws JMSException;

    protected abstract Message receiveNoWait(Destination destination) throws JMSException;
}
