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
package co.elastic.apm.agent.jms;


import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.MessagingConfiguration;
import co.elastic.apm.agent.impl.context.Headers;
import co.elastic.apm.agent.impl.transaction.Id;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.annotation.Nullable;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.TemporaryQueue;
import javax.jms.TextMessage;
import javax.jms.Topic;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static co.elastic.apm.agent.jms.JmsInstrumentationHelper.JMS_EXPIRATION_HEADER;
import static co.elastic.apm.agent.jms.JmsInstrumentationHelper.JMS_MESSAGE_ID_HEADER;
import static co.elastic.apm.agent.jms.JmsInstrumentationHelper.JMS_TIMESTAMP_HEADER;
import static co.elastic.apm.agent.jms.JmsInstrumentationHelper.JMS_TRACE_PARENT_PROPERTY;
import static co.elastic.apm.agent.jms.JmsInstrumentationHelper.MESSAGING_TYPE;
import static co.elastic.apm.agent.jms.JmsInstrumentationHelperImpl.TEMP;
import static co.elastic.apm.agent.jms.JmsInstrumentationHelperImpl.TIBCO_TMP_QUEUE_PREFIX;
import static co.elastic.apm.agent.configuration.MessagingConfiguration.Strategy.BOTH;
import static co.elastic.apm.agent.configuration.MessagingConfiguration.Strategy.POLLING;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class JmsInstrumentationIT extends AbstractInstrumentationTest {

    // Keeping a static reference for resource cleaning
    private static Set<BrokerFacade> staticBrokerFacade = new HashSet<>();

    private static final BlockingQueue<Message> resultQ = new ArrayBlockingQueue<>(5);

    private final BrokerFacade brokerFacade;
    private final CoreConfiguration coreConfiguration;
    private ThreadLocal<Boolean> receiveNoWaitFlow = new ThreadLocal<>();
    private ThreadLocal<Boolean> expectNoTraces = new ThreadLocal<>();

    private Queue noopQ;

    public JmsInstrumentationIT(BrokerFacade brokerFacade) throws Exception {
        this.brokerFacade = brokerFacade;
        if (staticBrokerFacade.add(brokerFacade)) {
            brokerFacade.prepareResources();
        }
        coreConfiguration = config.getConfig(CoreConfiguration.class);
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
        receiveNoWaitFlow.set(false);
        expectNoTraces.set(false);
        reporter.reset();
        Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null).activate();
        transaction.withName("JMS-Test Transaction");
        transaction.withType("request");
        transaction.withResult("success");
        brokerFacade.beforeTest();
        noopQ = brokerFacade.createQueue("NOOP");
        when(coreConfiguration.getCaptureBody()).thenReturn(CoreConfiguration.EventType.ALL);
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
        final Queue queue = createTestQueue();
        doTestSendReceiveOnTracedThread(() -> brokerFacade.receive(queue, 10), queue, false, false);
    }

    @Test
    public void testQueueSendReceiveOnTracedThreadNoTimestamp() throws Exception {
        final Queue queue = createTestQueue();
        doTestSendReceiveOnTracedThread(() -> brokerFacade.receive(queue, 10), queue, false, true);
    }

    @Test
    public void testQueueSendReceiveNoWaitOnTracedThread() throws Exception {
        receiveNoWaitFlow.set(true);
        if (!brokerFacade.shouldTestReceiveNoWait()) {
            return;
        }
        final Queue queue = createTestQueue();
        doTestSendReceiveOnTracedThread(() -> brokerFacade.receiveNoWait(queue), queue, true, false);
    }

    private void doTestSendReceiveOnTracedThread(Callable<Message> receiveMethod, Queue queue,
                                                 boolean sleepBetweenCycles, boolean disableTimestamp) throws Exception {
        final CancellableThread thread = new CancellableThread(() -> {
            Transaction transaction = null;
            Message message = null;
            try {
                transaction = tracer.startTransaction(TraceContext.asRoot(), null, null)
                    .withName("JMS-Test Receiver Transaction")
                    .activate();
                message = receiveMethod.call();

                if (message != null) {
                    // create a span for testing context propagation
                    brokerFacade.send(noopQ, brokerFacade.createTextMessage("testQueueSendReceiveOnTracedThread"), false);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (transaction != null) {
                    transaction.deactivate().end();
                }

                if (message != null) {
                    resultQ.offer(message);
                }
            }
        }, sleepBetweenCycles);
        thread.start();

        // sleeping to allow time for polling spans to be created without yielding a message
        Thread.sleep(50);

        try {
            verifyQueueSendReceiveOnTracedThread(queue, disableTimestamp);
        } finally {
            thread.cancel();
            thread.join(1000);
            assertThat(thread.isDone()).isTrue();
        }
    }

    @Test
    public void testQueueSendReceiveOnNonTracedThread() throws Exception {
        final Queue queue = createTestQueue();
        doTestSendReceiveOnNonTracedThread(() -> brokerFacade.receive(queue, 10), queue, false);
    }

    @Test
    public void testQueueSendReceiveOnNonTracedThreadInActive() throws Exception {
        when(coreConfiguration.isActive()).thenReturn(false);
        final Queue queue = createTestQueue();
        doTestSendReceiveOnNonTracedThread(() -> brokerFacade.receive(queue, 10), queue, false);
    }

    @Test
    public void testQueueSendReceiveNoWaitOnNonTracedThread() throws Exception {
        receiveNoWaitFlow.set(true);
        if (!brokerFacade.shouldTestReceiveNoWait()) {
            return;
        }
        final Queue queue = createTestQueue();
        doTestSendReceiveOnNonTracedThread(() -> brokerFacade.receiveNoWait(queue), queue, true);
    }

    @Test
    public void testPollingTransactionCreationOnly() throws Exception {
        when(config.getConfig(MessagingConfiguration.class).getMessagePollingTransactionStrategy()).thenReturn(POLLING);
        final Queue queue = createTestQueue();
        doTestSendReceiveOnNonTracedThread(() -> brokerFacade.receive(queue, 10), queue, false);
    }

    @Test
    public void testHandlingAndPollingTransactionCreation() throws Exception {
        when(config.getConfig(MessagingConfiguration.class).getMessagePollingTransactionStrategy()).thenReturn(BOTH);
        final Queue queue = createTestQueue();
        doTestSendReceiveOnNonTracedThread(() -> brokerFacade.receive(queue, 10), queue, false);
    }

    @Test
    public void testInactiveReceive() throws Exception {
        when(config.getConfig(CoreConfiguration.class).isActive()).thenReturn(false);
        final Queue queue = createTestQueue();
        doTestSendReceiveOnNonTracedThread(() -> brokerFacade.receive(queue, 10), queue, false);
    }

    @Test
    public void testQueueDisablement() throws Exception {
        when(config.getConfig(MessagingConfiguration.class).getMessagePollingTransactionStrategy()).thenReturn(BOTH);
        when(config.getConfig(MessagingConfiguration.class).getIgnoreMessageQueues())
            .thenReturn(List.of(WildcardMatcher.valueOf("ignore-*")));
        final Queue queue = brokerFacade.createQueue("ignore-this");
        expectNoTraces.set(true);
        doTestSendReceiveOnNonTracedThread(() -> brokerFacade.receive(queue, 10), queue, false);
    }

    private static class MessageHolder {
        @Nullable
        Message message;
    }

    private void doTestSendReceiveOnNonTracedThread(Callable<Message> receiveMethod, Queue queue, boolean sleepBetweenCycles) throws Exception {
        final MessageHolder messageHolder = new MessageHolder();
        final CancellableThread thread = new CancellableThread(() -> {
            try {
                Message message = receiveMethod.call();

                if (messageHolder.message != null) {
                    // only post to the result queue after the following receive is called, to ensure transaction end
                    resultQ.offer(messageHolder.message);
                    messageHolder.message = null;
                }

                if (message != null) {
                    messageHolder.message = message;
                    // create a span for testing context propagation
                    brokerFacade.send(noopQ, brokerFacade.createTextMessage("testQueueSendReceiveOnNonTracedThread"), false);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, sleepBetweenCycles);
        thread.start();

        // sleeping to allow time for polling transactions to be created without yielding a message, thus ignored
        Thread.sleep(50);

        try {
            verifyQueueSendReceiveOnNonTracedThread(queue);
        } finally {
            thread.cancel();
            thread.join(1000);
            assertThat(thread.isDone()).isTrue();
        }
    }

    private Queue createTestQueue() throws Exception {
        String queueName = UUID.randomUUID().toString();
        return brokerFacade.createQueue(queueName);
    }

    private Topic createTestTopic() throws Exception {
        String topicName = UUID.randomUUID().toString();
        return brokerFacade.createTopic(topicName);
    }

    // tests receive as a span
    private void verifyQueueSendReceiveOnTracedThread(Queue queue, boolean disableTimestamp) throws Exception {
        String message = UUID.randomUUID().toString();
        Message outgoingMessage = brokerFacade.createTextMessage(message);
        brokerFacade.send(queue, outgoingMessage, disableTimestamp);
        Message incomingMessage = resultQ.poll(2, TimeUnit.SECONDS);
        assertThat(incomingMessage).isNotNull();
        verifyMessage(message, incomingMessage);

        List<Span> spans = reporter.getSpans();
        int numSpans = spans.size();
        if (!receiveNoWaitFlow.get()) {
            assertThat(numSpans).isGreaterThan(3);
        }

        final String sendToTestQueueSpanName = "JMS SEND to queue " + queue.getQueueName();
        List<Span> sendSpans = spans.stream().filter(span -> span.getNameAsString().equals(sendToTestQueueSpanName)).collect(Collectors.toList());
        assertThat(sendSpans).hasSize(1);
        Span sendSpan = sendSpans.get(0);
        final String receiveFromTestQueueSpanName = "JMS RECEIVE from queue " + queue.getQueueName();
        List<Span> receiveSpans = spans.stream().filter(span -> span.getNameAsString().equals(receiveFromTestQueueSpanName)).collect(Collectors.toList());
        assertThat(receiveSpans).hasSize(1);
        Span receiveSpan = receiveSpans.get(0);
        final String sendToTestNoopQueueSpanName = "JMS SEND to queue NOOP";
        List<Span> sendToNoopSpans = spans.stream().filter(span -> span.getNameAsString().equals(sendToTestNoopQueueSpanName)).collect(Collectors.toList());
        Span sendToNoopSpan = null;
        if (!receiveNoWaitFlow.get()) {
            assertThat(sendToNoopSpans).hasSize(1);
            sendToNoopSpan = sendToNoopSpans.get(0);
        }

        // rest of spans should be receive spans yielding null messages
        final String receiveWithNoMessageSpanName = "JMS RECEIVE";
        assertThat(spans.stream().filter(span -> span.getNameAsString().equals(receiveWithNoMessageSpanName)).count()).isGreaterThanOrEqualTo(numSpans - 3);

        //noinspection ConstantConditions
        Id currentTraceId = tracer.currentTransaction().getTraceContext().getTraceId();
        assertThat(sendSpan.getTraceContext().getTraceId()).isEqualTo(currentTraceId);
        assertThat(sendSpan.getContext().getMessage().getQueueName()).isEqualTo(queue.getQueueName());
        verifySendSpanDestinationDetails(sendSpan, queue.getQueueName());

        Id receiveTraceId = receiveSpan.getTraceContext().getTraceId();
        List<Transaction> receiveTransactions = reporter.getTransactions().stream().filter(transaction -> transaction.getTraceContext().getTraceId().equals(receiveTraceId)).collect(Collectors.toList());
        assertThat(receiveTransactions).hasSize(1);
        Transaction receiveTransaction = receiveTransactions.get(0);
        assertThat(receiveSpan.getTraceContext().getParentId()).isEqualTo(receiveTransaction.getTraceContext().getId());
        assertThat(receiveSpan.getContext().getMessage().getQueueName()).isEqualTo(queue.getQueueName());
        // Body and headers should not be captured for receive spans
        assertThat(receiveSpan.getContext().getMessage().getBodyForRead()).isNull();
        assertThat(receiveSpan.getContext().getMessage().getHeaders()).isEmpty();
        // Age should be captured for receive spans, unless disabled
        if (disableTimestamp) {
            assertThat(receiveSpan.getContext().getMessage().getAge()).isEqualTo(-1L);
        } else {
            assertThat(receiveSpan.getContext().getMessage().getAge()).isGreaterThanOrEqualTo(0);
        }

        if (sendToNoopSpan != null) {
            assertThat(sendToNoopSpan.getTraceContext().getTraceId()).isEqualTo(receiveTraceId);
            assertThat(sendToNoopSpan.getTraceContext().getParentId()).isEqualTo(receiveTransaction.getTraceContext().getId());
            assertThat(sendToNoopSpan.getContext().getMessage().getQueueName()).isEqualTo("NOOP");
            verifySendSpanDestinationDetails(sendToNoopSpan, "NOOP");
        }
    }

    private void verifySendSpanDestinationDetails(Span sendSpan, String destinationName) {
        assertThat(sendSpan.getContext().getDestination().getService().getName().toString()).isEqualTo("jms");
        assertThat(sendSpan.getContext().getDestination().getService().getResource().toString()).isEqualTo("jms/" + destinationName);
        assertThat(sendSpan.getContext().getDestination().getService().getType()).isEqualTo(MESSAGING_TYPE);
    }

    // tests transaction creation following a receive
    private void verifyQueueSendReceiveOnNonTracedThread(Queue queue)
        throws Exception {
        String message = UUID.randomUUID().toString();
        TextMessage outgoingMessage = brokerFacade.createTextMessage(message);
        brokerFacade.send(queue, outgoingMessage, false);
        Message incomingMessage = resultQ.poll(2, TimeUnit.SECONDS);
        assertThat(incomingMessage).isNotNull();
        verifyMessage(message, incomingMessage);
        verifySendReceiveOnNonTracedThread(queue.getQueueName(), outgoingMessage);
    }

    private void verifySendListenOnNonTracedThread(String destinationName, TextMessage message, int expectedReadTransactions) throws JMSException {
        await().atMost(1000, MILLISECONDS).until(() -> reporter.getTransactions().size() == expectedReadTransactions);

        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        Span sendSpan = spans.get(0);
        String spanName = sendSpan.getNameAsString();
        assertThat(spanName).startsWith("JMS SEND to ");
        assertThat(spanName).endsWith(destinationName);

        assertThat(sendSpan.getContext().getMessage().getQueueName()).isEqualTo(destinationName);
        assertThat(sendSpan.getContext().getMessage().getAge()).isEqualTo(-1L);
        verifySendSpanDestinationDetails(sendSpan, destinationName);

        //noinspection ConstantConditions
        Id currentTraceId = tracer.currentTransaction().getTraceContext().getTraceId();
        assertThat(sendSpan.getTraceContext().getTraceId()).isEqualTo(currentTraceId);

        List<Transaction> receiveTransactions = reporter.getTransactions();
        assertThat(receiveTransactions).hasSize(expectedReadTransactions);
        for (Transaction receiveTransaction : receiveTransactions) {
            assertThat(receiveTransaction.getNameAsString()).startsWith("JMS RECEIVE from ");
            assertThat(receiveTransaction.getNameAsString()).endsWith(destinationName);
            assertThat(receiveTransaction.getTraceContext().getTraceId()).isEqualTo(currentTraceId);
            assertThat(receiveTransaction.getTraceContext().getParentId()).isEqualTo(sendSpan.getTraceContext().getId());
            assertThat(receiveTransaction.getType()).isEqualTo(MESSAGING_TYPE);
            assertThat(receiveTransaction.getContext().getMessage().getQueueName()).isEqualTo(destinationName);
            StringBuilder body = receiveTransaction.getContext().getMessage().getBodyForRead();
            assertThat(body).isNotNull();
            assertThat(body.toString()).isEqualTo(message.getText());
            assertThat(receiveTransaction.getContext().getMessage().getAge()).isGreaterThanOrEqualTo(0);
            verifyMessageHeaders(message, receiveTransaction);
        }
    }

    private void verifyMessageHeaders(Message message, Transaction receiveTransaction) throws JMSException {
        Map<String, CharSequence> headersMap = new HashMap<>();
        for (Headers.Header header : receiveTransaction.getContext().getMessage().getHeaders()) {
            headersMap.put(header.getKey(), header.getValue());
        }
        assertThat(headersMap).isNotEmpty();
        assertThat(message.getJMSMessageID()).isEqualTo(headersMap.get(JMS_MESSAGE_ID_HEADER));
        assertThat(String.valueOf(message.getJMSExpiration())).isEqualTo(headersMap.get(JMS_EXPIRATION_HEADER));
        assertThat(String.valueOf(message.getJMSTimestamp())).isEqualTo(headersMap.get(JMS_TIMESTAMP_HEADER));
        assertThat(String.valueOf(message.getObjectProperty("test_string_property"))).isEqualTo(headersMap.get("test_string_property"));
        assertThat(String.valueOf(message.getObjectProperty("test_int_property"))).isEqualTo(headersMap.get("test_int_property"));
        assertThat(String.valueOf(message.getStringProperty("passwd"))).isEqualTo("secret");
        assertThat(headersMap.get("passwd")).isNull();
        assertThat(headersMap.get("null_property")).isEqualTo("null");
        assertThat(String.valueOf(message.getStringProperty(JMS_TRACE_PARENT_PROPERTY))).isNotNull();
        assertThat(headersMap.get(JMS_TRACE_PARENT_PROPERTY)).isNull();
    }

    private void verifySendReceiveOnNonTracedThread(String destinationName, TextMessage message) throws JMSException {
        MessagingConfiguration.Strategy strategy = config.getConfig(MessagingConfiguration.class).getMessagePollingTransactionStrategy();

        List<Span> spans = reporter.getSpans();
        List<Transaction> receiveTransactions = reporter.getTransactions();

        if (expectNoTraces.get()) {
            assertThat(spans).isEmpty();
            assertThat(receiveTransactions).isEmpty();
            return;
        }

        boolean isActive = config.getConfig(CoreConfiguration.class).isActive();
        if (!isActive || strategy == POLLING || receiveNoWaitFlow.get()) {
            assertThat(spans).hasSize(1);
        } else {
            assertThat(spans).hasSize(2);
        }

        Span sendInitialMessageSpan = spans.get(0);
        String spanName = sendInitialMessageSpan.getNameAsString();
        assertThat(spanName).startsWith("JMS SEND to ");
        assertThat(spanName).endsWith(destinationName);
        assertThat(sendInitialMessageSpan.getContext().getMessage().getQueueName()).isEqualTo(destinationName);
        assertThat(sendInitialMessageSpan.getContext().getMessage().getAge()).isEqualTo(-1L);
        verifySendSpanDestinationDetails(sendInitialMessageSpan, destinationName);

        //noinspection ConstantConditions
        Id currentTraceId = tracer.currentTransaction().getTraceContext().getTraceId();
        assertThat(sendInitialMessageSpan.getTraceContext().getTraceId()).isEqualTo(currentTraceId);

        if (!isActive) {
            assertThat(receiveTransactions).isEmpty();
            return;
        } else if (strategy == BOTH) {
            assertThat(receiveTransactions).hasSize(2);
        } else {
            assertThat(receiveTransactions).hasSize(1);
        }

        Id transactionId = null;
        for (Transaction receiveTransaction : receiveTransactions) {
            assertThat(receiveTransaction.getNameAsString()).startsWith("JMS RECEIVE from ");
            assertThat(receiveTransaction.getNameAsString()).endsWith(destinationName);
            assertThat(receiveTransaction.getTraceContext().getTraceId()).isEqualTo(currentTraceId);
            assertThat(receiveTransaction.getTraceContext().getParentId()).isEqualTo(sendInitialMessageSpan.getTraceContext().getId());
            assertThat(receiveTransaction.getType()).isEqualTo(MESSAGING_TYPE);
            assertThat(receiveTransaction.getContext().getMessage().getQueueName()).isEqualTo(destinationName);
            StringBuilder body = receiveTransaction.getContext().getMessage().getBodyForRead();
            assertThat(body).isNotNull();
            assertThat(body.toString()).isEqualTo(message.getText());
            assertThat(receiveTransaction.getContext().getMessage().getAge()).isGreaterThanOrEqualTo(0);
            transactionId = receiveTransaction.getTraceContext().getId();
        }

        if (strategy != POLLING && !receiveNoWaitFlow.get()) {
            Span sendNoopSpan = spans.get(1);
            assertThat(sendNoopSpan.getNameAsString()).isEqualTo("JMS SEND to queue NOOP");
            assertThat(sendNoopSpan.getTraceContext().getTraceId()).isEqualTo(currentTraceId);
            // If both polling and handling transactions are captured, handling transaction would come second
            assertThat(sendNoopSpan.getTraceContext().getParentId()).isEqualTo(transactionId);
            assertThat(sendNoopSpan.getContext().getMessage().getQueueName()).isEqualTo("NOOP");
            verifySendSpanDestinationDetails(sendNoopSpan, "NOOP");
        }
    }

    @Test
    public void testRegisterConcreteListenerImpl() {
        try {
            testQueueSendListen(createTestQueue(), brokerFacade::registerConcreteListenerImplementation);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testTempQueueListener() {
        try {
            testQueueSendListen(brokerFacade.createTempQueue(), brokerFacade::registerConcreteListenerImplementation);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testTibcoTempQueueListener() {
        try {
            testQueueSendListen(brokerFacade.createQueue(TIBCO_TMP_QUEUE_PREFIX + UUID.randomUUID().toString()),
                brokerFacade::registerConcreteListenerImplementation);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testInactiveOnMessage() throws Exception {
        when(config.getConfig(CoreConfiguration.class).isActive()).thenReturn(false);
        Queue queue = createTestQueue();
        CompletableFuture<Message> incomingMessageFuture = brokerFacade.registerConcreteListenerImplementation(queue);
        String message = UUID.randomUUID().toString();
        Message outgoingMessage = brokerFacade.createTextMessage(message);
        brokerFacade.send(queue, outgoingMessage, false);
        Message incomingMessage = incomingMessageFuture.get(3, TimeUnit.SECONDS);
        verifyMessage(message, incomingMessage);
        Thread.sleep(500);
        assertThat(reporter.getTransactions()).isEmpty();
    }

    @Test
    public void testRegisterListenerLambda() {
        try {
            testQueueSendListen(createTestQueue(), brokerFacade::registerListenerLambda);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testRegisterListenerMethodReference() {
        try {
            testQueueSendListen(createTestQueue(), brokerFacade::registerListenerMethodReference);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void testQueueSendListen(Queue queue, Function<Destination, CompletableFuture<Message>> listenerRegistrationFunction)
        throws Exception {
        CompletableFuture<Message> incomingMessageFuture = listenerRegistrationFunction.apply(queue);
        String message = UUID.randomUUID().toString();
        TextMessage outgoingMessage = brokerFacade.createTextMessage(message);
        brokerFacade.send(queue, outgoingMessage, false);
        Message incomingMessage = incomingMessageFuture.get(3, TimeUnit.SECONDS);
        verifyMessage(message, incomingMessage);
        String queueName = queue.getQueueName();
        // special handling for temp queues
        if (queue instanceof TemporaryQueue || queueName.startsWith(TIBCO_TMP_QUEUE_PREFIX)) {
            queueName = TEMP;
        }
        verifySendListenOnNonTracedThread(queueName, outgoingMessage, 1);
    }

    @Test
    public void testTopicWithTwoSubscribers() throws Exception {
        Topic topic = createTestTopic();

        final CompletableFuture<Message> incomingMessageFuture1 = brokerFacade.registerConcreteListenerImplementation(topic);
        final CompletableFuture<Message> incomingMessageFuture2 = brokerFacade.registerConcreteListenerImplementation(topic);

        String message = UUID.randomUUID().toString();
        TextMessage outgoingMessage = brokerFacade.createTextMessage(message);
        brokerFacade.send(topic, outgoingMessage, false);

        Message incomingMessage1 = incomingMessageFuture1.get(3, TimeUnit.SECONDS);
        verifyMessage(message, incomingMessage1);

        Message incomingMessage2 = incomingMessageFuture2.get(3, TimeUnit.SECONDS);
        verifyMessage(message, incomingMessage2);

        verifySendListenOnNonTracedThread(topic.getTopicName(), outgoingMessage, 2);
    }

    private void verifyMessage(String expectedText, Message message) throws JMSException {
        assertThat(message).isInstanceOf(TextMessage.class);
        assertThat(((TextMessage) message).getText()).isEqualTo(expectedText);
    }

    private static class CancellableThread extends Thread {
        private final Runnable target;
        private final boolean sleepBetweenCycles;
        private volatile boolean running = true;
        private volatile boolean done;

        private CancellableThread(Runnable target, boolean sleepBetweenCycles) {
            this.target = target;
            this.sleepBetweenCycles = sleepBetweenCycles;
        }

        @Override
        public void run() {
            while (running) {
                target.run();
                if (sleepBetweenCycles) {
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            done = true;
        }

        void cancel() {
            running = false;
        }

        boolean isDone() {
            return done;
        }
    }
}
