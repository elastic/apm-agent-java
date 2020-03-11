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
package co.elastic.apm.agent.rocketmq;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.MessagingConfiguration;
import co.elastic.apm.agent.impl.TracerInternalApiUtils;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.context.Headers;
import co.elastic.apm.agent.impl.context.SpanContext;
import co.elastic.apm.agent.impl.context.TransactionContext;
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.impl.sampling.Sampler;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.rocketmq.container.RocketMQContainer;
import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.consumer.PullResult;
import org.apache.rocketmq.client.consumer.PullStatus;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

public abstract class AbstractRocketMQConsumerInstrumentationTest extends AbstractInstrumentationTest {

    protected static final String REQUEST_TOPIC = "Request-Topic";
    private static final Logger logger = LoggerFactory.getLogger(AbstractRocketMQConsumerInstrumentationTest.class);
    private static final String REPLY_TOPIC = "Reply-Topic";

    private static final String FIRST_MESSAGE_BODY = "First message body";
    private static final String SECOND_MESSAGE_BODY = "Second message body";

    private static final String TEST_HEADER_KEY = "Test-Header";
    private static final String PASSWORD_HEADER_KEY = "password";
    private static final String TEST_PROPERTY_VALUE = "Test-Property-Value";
    protected static RocketMQContainer rocketmq;
    private static TestScenario testScenario;
    private static RecordIterationMode iterationMode;
    @Nonnull
    private static DefaultMQProducer producer;

    @Nonnull
    private static ReplyConsumer replyConsumer;

    private final CoreConfiguration coreConfiguration;
    private final MessagingConfiguration messagingConfiguration;

    public AbstractRocketMQConsumerInstrumentationTest() {
        this.coreConfiguration = config.getConfig(CoreConfiguration.class);
        this.messagingConfiguration = config.getConfig(MessagingConfiguration.class);
    }

    @BeforeClass
    public static void setup() throws MQClientException {
        rocketmq = new RocketMQContainer();
        rocketmq.start();

        producer = new DefaultMQProducer("Request-Consumer");
        producer.setNamesrvAddr(rocketmq.getNameServer());
        producer.start();

        Exception createTopicExp = null;
        for (int retry = 0; retry < 3; retry++) {
            try {
                producer.createTopic(MixAll.DEFAULT_TOPIC, REQUEST_TOPIC, 1);
                producer.createTopic(MixAll.DEFAULT_TOPIC, REPLY_TOPIC, 1);
            } catch (Exception exception) {
                createTopicExp = exception;
            }
        }
        if (createTopicExp != null) {
            throw new IllegalStateException("Create topic failure.", createTopicExp);
        }

        replyConsumer = new ReplyConsumer();
        replyConsumer.start();
    }

    static void doConsume(List<MessageExt> msgs) {
        try {
            if (iterationMode == RecordIterationMode.ITERABLE_FOR) {
                for (MessageExt msg: msgs) {
                    producer.send(new Message(REPLY_TOPIC, msg.getBody()));
                }
            } else if (iterationMode == RecordIterationMode.ITERABLE_FOREACH) {
                msgs.forEach(new RequestMessageConsumer());
            } else if (iterationMode == RecordIterationMode.ITERABLE_SPLITERATOR) {
                msgs.spliterator().forEachRemaining(new RequestMessageConsumer());
            }  else if (iterationMode == RecordIterationMode.SUB_LIST) {
                for (MessageExt msg : msgs.subList(0, 1)) {
                    producer.send(new Message(REPLY_TOPIC, msg.getBody()));
                }
            } else if (iterationMode == RecordIterationMode.PARTIALLY_ITERATE) {
                Iterator<MessageExt> iterator = msgs.iterator();
                while (iterator.hasNext()) {
                    MessageExt msg = iterator.next();
                    producer.send(new Message(REPLY_TOPIC, msg.getBody()));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @AfterClass
    public static void tearDown() {
        producer.shutdown();
        replyConsumer.shutdown();
        rocketmq.stop();
    }

    @Before
    public void startTransaction() {
        reporter.reset();
        startAndActivateTransaction(null);
        testScenario = TestScenario.NORMAL;
        iterationMode = RecordIterationMode.ITERABLE_FOR;
    }

    private void startAndActivateTransaction(@Nullable Sampler sampler) {
        Transaction transaction;
        if (sampler == null) {
            transaction = tracer.startRootTransaction(null);
        } else {
            transaction = tracer.startRootTransaction(sampler, -1, null);
        }
        if (transaction != null) {
            transaction.activate()
                .withName("RocketMQ-Test Transaction")
                .withType("test")
                .withResult("success");
        }
    }

    @After
    public void endTransaction() {
        Transaction currentTransaction = tracer.currentTransaction();
        if (currentTransaction != null) {
            currentTransaction.deactivate().end();
        }
    }

    @Test
    public void testSndTwoMessage_IterableFor() throws Exception {
        iterationMode = RecordIterationMode.ITERABLE_FOR;
        sendTwoMessageAndConsumeReplies();
        verifyTracing();
    }

    @Test
    public void testSendTwoMessage_IterableForEach() throws Exception {
        iterationMode = RecordIterationMode.ITERABLE_FOREACH;
        sendTwoMessageAndConsumeReplies();
        verifyTracing();
    }

    @Test
    public void testSendTwoMessage_IterableSpliterator() throws Exception {
        iterationMode = RecordIterationMode.ITERABLE_SPLITERATOR;
        sendTwoMessageAndConsumeReplies();
        verifyTracing();
    }

    @Test
    public void testSendTwoMessage_RecordListSubList() throws Exception {
        iterationMode = RecordIterationMode.SUB_LIST;
        sendTwoMessageAndConsumeReplies();
        verifyTracing();
    }

    @Test
    public void testSendTwoMessage_PartiallyIterate() throws Exception {
        iterationMode = RecordIterationMode.PARTIALLY_ITERATE;
        sendTwoMessageAndConsumeReplies();
        verifyTracing();
    }

    @Test
    public void testBodyCaptureEnabled() throws Exception {
        when(coreConfiguration.getCaptureBody()).thenReturn(CoreConfiguration.EventType.ALL);
        testScenario = TestScenario.BODY_CAPTURE_ENABLED;
        iterationMode = RecordIterationMode.ITERABLE_FOR;
        sendTwoMessageAndConsumeReplies();
        verifyTracing();
    }

    @Test
    public void testHeaderCaptureDisabled() throws Exception {
        when(coreConfiguration.isCaptureHeaders()).thenReturn(false);
        testScenario = TestScenario.HEADERS_CAPTURE_DISABLED;
        iterationMode = RecordIterationMode.ITERABLE_FOR;
        sendTwoMessageAndConsumeReplies();
        verifyTracing();
    }

    @Test
    public void testIgnoreTopic() throws Exception {
        when(messagingConfiguration.getIgnoreMessageQueues()).thenReturn(List.of(WildcardMatcher.valueOf(REQUEST_TOPIC)));
        testScenario = TestScenario.IGNORE_REQUEST_TOPIC;
        iterationMode = RecordIterationMode.ITERABLE_FOR;
        sendTwoMessageAndConsumeReplies();

        List<Span> spans = reporter.getSpans();
        assertThat(spans).isEmpty();

        List<Transaction> transactions = reporter.getTransactions();
        assertThat(transactions).isEmpty();
    }

    @Test
    public void testTransactionCreationWithoutContext() throws Exception {
        testScenario = TestScenario.NO_CONTEXT_PROPAGATION;
        iterationMode = RecordIterationMode.ITERABLE_FOR;
        tracer.currentTransaction().deactivate().end();
        reporter.reset();

        sendTwoMessageAndConsumeReplies();

        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(2);
        Span sendSpan1 = spans.get(0);
        verifySendSpanContents(sendSpan1, REPLY_TOPIC);
        Span sendSpan2 = spans.get(1);
        verifySendSpanContents(sendSpan2, REPLY_TOPIC);
        List<Transaction> transactions = reporter.getTransactions();
        assertThat(transactions).hasSize(4);
        verifyConsumeTransactionContents(transactions.get(0), null, REQUEST_TOPIC, null);
        verifyConsumeTransactionContents(transactions.get(1), null, REQUEST_TOPIC, null);
        verifyConsumeTransactionContents(transactions.get(2), sendSpan1, REPLY_TOPIC, null);
        verifyConsumeTransactionContents(transactions.get(3), sendSpan2, REPLY_TOPIC, null);
    }

    @Test
    public void testAgentPaused() throws Exception {
        TracerInternalApiUtils.pauseTracer(tracer);
        int transactionCount = objectPoolFactory.getTransactionPool().getRequestedObjectCount();
        int spanCount = objectPoolFactory.getSpanPool().getRequestedObjectCount();

        tracer.currentTransaction().deactivate().end();
        reporter.reset();

        testScenario = TestScenario.AGENT_PAUSED;
        startAndActivateTransaction(ConstantSampler.of(false));
        sendTwoMessageAndConsumeReplies();

        assertThat(reporter.getTransactions()).isEmpty();
        assertThat(reporter.getSpans()).isEmpty();
        assertThat(objectPoolFactory.getTransactionPool().getRequestedObjectCount()).isEqualTo(transactionCount);
        assertThat(objectPoolFactory.getSpanPool().getRequestedObjectCount()).isEqualTo(spanCount);
    }

    @Test
    public void testSendTwoRecords_TransactionNotSampled() throws Exception {
        testScenario = TestScenario.NON_SAMPLED_TRANSACTION;

        tracer.currentTransaction().deactivate().end();
        reporter.reset();
        startAndActivateTransaction(ConstantSampler.of(false));

        iterationMode = RecordIterationMode.ITERABLE_FOR;
        sendTwoMessageAndConsumeReplies();

        List<Span> spans = reporter.getSpans();
        assertThat(spans).isEmpty();
        List<Transaction> transactions = reporter.getTransactions();
        assertThat(transactions).hasSize(2);
        transactions.forEach(transaction -> assertThat(transaction.isSampled()).isFalse());
        transactions.forEach(transaction -> assertThat(
            transaction.getTraceContext().getTraceId()).isEqualTo(tracer.currentTransaction().getTraceContext().getTraceId())
        );
        transactions.forEach(transaction -> assertThat(
            transaction.getTraceContext().getParentId()).isEqualTo(tracer.currentTransaction().getTraceContext().getId())
        );
        transactions.forEach(transaction -> assertThat(transaction.getType()).isEqualTo("messaging"));
        transactions.forEach(transaction -> assertThat(transaction.getNameAsString()).isEqualTo(String.format("RocketMQ Consume Message#%s", REQUEST_TOPIC)));
    }

    private void sendTwoMessageAndConsumeReplies() throws Exception {
        final StringBuilder callback = new StringBuilder();
        String propertyName = (testScenario == TestScenario.SANITIZED_HEADER) ? PASSWORD_HEADER_KEY : TEST_HEADER_KEY;
        Message message1 = new Message(REQUEST_TOPIC, FIRST_MESSAGE_BODY.getBytes());
        message1.putUserProperty(propertyName, TEST_PROPERTY_VALUE);
        Message message2 = new Message(REQUEST_TOPIC, SECOND_MESSAGE_BODY.getBytes());
        message2.putUserProperty(propertyName, TEST_PROPERTY_VALUE);

        producer.send(message1);
        producer.send(message2, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                callback.append("success");
            }

            @Override
            public void onException(Throwable e) {
                callback.append("failure");
            }
        });

        if (testScenario != TestScenario.IGNORE_REQUEST_TOPIC && testScenario != TestScenario.AGENT_PAUSED) {
            await().atMost(10, TimeUnit.SECONDS).until(() -> reporter.getTransactions().size() == 2);
            if (testScenario != TestScenario.NON_SAMPLED_TRANSACTION) {
                int expectedSpans = (testScenario == TestScenario.NO_CONTEXT_PROPAGATION) ? 2 : 4;
                await().atMost(500, TimeUnit.MILLISECONDS).until(() -> reporter.getSpans().size() == expectedSpans);
            }
        }

        List<MessageExt> replies = replyConsumer.poll(2);

        assertThat(callback).isNotEmpty();
        assertThat(replies).hasSize(2);
        Iterator<MessageExt> iterator = replies.iterator();
        assertThat(new String(iterator.next().getBody())).isEqualTo(FIRST_MESSAGE_BODY);
        assertThat(new String(iterator.next().getBody())).isEqualTo(SECOND_MESSAGE_BODY);
        assertThat(iterator.hasNext()).isFalse();
    }



    private void verifyTracing() {
        List<Span> spans = reporter.getSpans();

        assertThat(spans).hasSize(4);
        spans.sort((o1, o2) -> o2.getNameAsString().compareTo(o1.getNameAsString()));
        Span sendRequestSpan0 = spans.get(0);
        verifySendSpanContents(sendRequestSpan0, REQUEST_TOPIC);
        Span sendRequestSpan1 = spans.get(1);
        verifySendSpanContents(sendRequestSpan1, REQUEST_TOPIC);
        Span sendReplySpan0 = spans.get(2);
        verifySendSpanContents(sendReplySpan0, REPLY_TOPIC);
        Span sendReplySpan1 = spans.get(3);
        verifySendSpanContents(sendReplySpan1, REPLY_TOPIC);

        List<Transaction> transactions = reporter.getTransactions();
        assertThat(transactions).hasSize(2);
        verifyConsumeTransactionContents(transactions.get(0), spans.get(0), REQUEST_TOPIC, FIRST_MESSAGE_BODY);
        verifyConsumeTransactionContents(transactions.get(1), spans.get(1), REQUEST_TOPIC, SECOND_MESSAGE_BODY);
    }


    private void verifySendSpanContents(Span span, String topic) {
        assertThat(span.getNameAsString()).isEqualTo("RocketMQ Send Message#" + topic);
        assertThat(span.getType()).isEqualTo("messaging");
        assertThat(span.getSubtype()).isEqualTo("rocketmq");
        assertThat(span.getAction()).isEqualTo("send");
        assertThat(span.getNameAsString()).isEqualTo(String.format("RocketMQ Send Message#%s", topic));

        SpanContext context = span.getContext();

        assertThat(context.getMessage().getQueueName()).startsWith(topic);

        Destination.Service service = context.getDestination().getService();
        assertThat(service.getType()).isEqualTo("messaging");
        assertThat(service.getName().toString()).isEqualTo("rocketmq");
        assertThat(service.getResource().toString()).isEqualTo(String.format("rocketmq/%s", topic));
    }

    void verifyConsumeTransactionContents(Transaction transaction, @Nullable Span parentSpan, String topic, @Nullable String messageValue) {
        assertThat(transaction.getType()).isEqualTo("messaging");
        assertThat(transaction.getNameAsString()).isEqualTo(String.format("RocketMQ Consume Message#%s", topic));

        TraceContext traceContext = transaction.getTraceContext();
        if (parentSpan != null) {
            assertThat(traceContext.getTraceId()).isEqualTo(parentSpan.getTraceContext().getTraceId());
            assertThat(traceContext.getParentId()).isEqualTo(parentSpan.getTraceContext().getId());
        }

        TransactionContext transactionContext = transaction.getContext();
        co.elastic.apm.agent.impl.context.Message message = transactionContext.getMessage();
        if (testScenario == TestScenario.BODY_CAPTURE_ENABLED && messageValue != null) {
            StringBuilder body = message.getBodyForRead();
            assertThat(body).isNotNull();
            String[] bodyItems = body.toString().split(";");
            assertThat(bodyItems).hasSize(2);
            assertThat(bodyItems[0].trim()).startsWith("msgId=");
            assertThat(bodyItems[1].trim()).isEqualTo("body=" + messageValue);
        } else {
            assertThat(message.getBodyForRead()).isNull();
        }

        Headers headers = message.getHeaders();
        Headers.Header testHeader = null;
        for (Headers.Header header : headers) {
            if (header.getKey().equals(TEST_HEADER_KEY)) {
                testHeader = header;
                break;
            }
        }
        if (testScenario == TestScenario.HEADERS_CAPTURE_DISABLED || topic.equals(REPLY_TOPIC)) {
            assertThat(testHeader).isNull();
        } else {
            assertThat(testHeader).isNotNull();
            assertThat(testHeader.getValue()).isEqualTo(TEST_PROPERTY_VALUE);
        }
    }

    enum RecordIterationMode {
        ITERABLE_FOR,
        ITERABLE_FOREACH,
        ITERABLE_SPLITERATOR,
        SUB_LIST,
        PARTIALLY_ITERATE
    }

    enum TestScenario {
        NORMAL,
        BODY_CAPTURE_ENABLED,
        HEADERS_CAPTURE_DISABLED,
        SANITIZED_HEADER,
        IGNORE_REQUEST_TOPIC,
        AGENT_PAUSED,
        NO_CONTEXT_PROPAGATION,
        TOPIC_ADDRESS_COLLECTION_DISABLED,
        NON_SAMPLED_TRANSACTION
    }

    static class ReplyConsumer {

        private DefaultMQPullConsumer consumer;

        private MessageQueue messageQueue;

        private long offset = 0;

        private boolean running = false;

        public synchronized void start() {
            try {
                consumer = new DefaultMQPullConsumer("Reply-Consumer");
                consumer.setNamesrvAddr(rocketmq.getNameServer());
                consumer.start();
                messageQueue = consumer.fetchSubscribeMessageQueues(REPLY_TOPIC).iterator().next();
            } catch (MQClientException e) {
                System.err.println("Error in consumer start:" + e.getMessage());
            }
        }

        public List<MessageExt> poll(int size) {
            try {
                PullResult pullResult = consumer.pull(consumer.fetchSubscribeMessageQueues(REPLY_TOPIC).iterator().next(), null, offset, size, 2000);
                offset = pullResult.getNextBeginOffset();
                if (pullResult.getPullStatus() == PullStatus.FOUND) {
                    List<MessageExt> messageExts =  pullResult.getMsgFoundList();
                    if (messageExts.size() < size) {
                        messageExts.addAll(this.poll(size - messageExts.size()));
                    }
                    return messageExts;
                } else {
                    return this.poll(size);
                }
            } catch (MQClientException | RemotingException | MQBrokerException | InterruptedException e) {
                System.err.println("Error in poll message:" + e.getMessage());
            }
            return this.poll(size);
        }

        public void shutdown() {
            consumer.shutdown();
        }

    }

    static class RequestMessageConsumer implements Consumer<MessageExt> {

        @Override
        public void accept(MessageExt messageExt) {
            try {
                producer.send(new Message(REPLY_TOPIC, messageExt.getBody()));
            } catch (MQClientException | RemotingException | InterruptedException | MQBrokerException e) {
                e.printStackTrace();
            }
        }
    }

}
