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
import co.elastic.apm.agent.impl.transaction.Id;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.rocketmq.container.RocketMQContainer;
import org.apache.rocketmq.client.MQAdmin;
import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.PullResult;
import org.apache.rocketmq.client.consumer.PullStatus;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListener;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.impl.factory.MQClientInstance;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

public class RocketMQInstrumentationTest extends AbstractInstrumentationTest {

    private static final String SUB_ALL_EXP = "*";

    private static final String REQUEST_TOPIC = "RequestTopic";

    private static final String REPLY_TOPIC = "ReplyTopic";

    private static final String MESSAGE_BODY = "MessageBody";

    private static final String NORMAL_PROPERTY_KEY = "NormalProperty";

    private static final String PASSWORD_PROPERTY_KEY = "password";

    private static final String PROPERTY_VALUE = "PropertyValue";

    private static Logger logger = LoggerFactory.getLogger(RocketMQInstrumentationTest.class);

    private final CoreConfiguration coreConfiguration;

    private final MessagingConfiguration messagingConfiguration;

    private static RocketMQContainer rocketMQ;

    private static DefaultMQProducer producer;

    private static MQAdmin mqAdmin;

    private static MQClientInstance mqClientInstance;

    private static DefaultMQPushConsumer concurrentRequestConsumer;

    private static DefaultMQPushConsumer orderRequestConsumer;

    private static ReplyConsumer replyConsumer;

    private static TestScenario testScenario;

    private static CountDownLatch sendCallbackDone = new CountDownLatch(1);

    private static CountDownLatch concurrentConsumeDone = new CountDownLatch(2);

    private static volatile boolean skipFlag = false;

    public RocketMQInstrumentationTest() {
        this.coreConfiguration = config.getConfig(CoreConfiguration.class);
        this.messagingConfiguration = config.getConfig(MessagingConfiguration.class);
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

    @BeforeClass
    public static void setup() throws MQClientException {
        rocketMQ = new RocketMQContainer();
        rocketMQ.start();

        producer = new DefaultMQProducer("RequestProducer");
        producer.setPollNameServerInterval(2 * 1000);
        producer.setNamesrvAddr(rocketMQ.getNameServer());
        producer.start();

        mqAdmin = producer;
        mqClientInstance = producer.getDefaultMQProducerImpl().getmQClientFactory();

        if (!createTopicAndAwaitCompletion(REQUEST_TOPIC) || !createTopicAndAwaitCompletion(REPLY_TOPIC)) {
            skipFlag = true;
            return;
        }

        concurrentRequestConsumer = createRequestConsumer("CRC", (MessageListenerConcurrently) (msgs, context) -> {
            doRequestMessageConsume(msgs);
            concurrentConsumeDone.countDown();
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });

        orderRequestConsumer = createRequestConsumer("ORC", (MessageListenerOrderly) (msgs, context) -> {
            try {
                concurrentConsumeDone.await();
            } catch (InterruptedException ignore) {

            }
            doRequestMessageConsume(msgs);
            return ConsumeOrderlyStatus.SUCCESS;
        });

        replyConsumer = new ReplyConsumer();
        replyConsumer.start();

        mqClientInstance.updateTopicRouteInfoFromNameServer(REQUEST_TOPIC);
        mqClientInstance.updateTopicRouteInfoFromNameServer(REPLY_TOPIC);
    }

    private static DefaultMQPushConsumer createRequestConsumer(String group, MessageListener messageListener) throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(group);
        consumer.setNamesrvAddr(rocketMQ.getNameServer());
        consumer.subscribe(RocketMQInstrumentationTest.REQUEST_TOPIC, SUB_ALL_EXP);
        if (messageListener instanceof MessageListenerConcurrently) {
            consumer.registerMessageListener((MessageListenerConcurrently)messageListener);
        } else if (messageListener instanceof MessageListenerOrderly) {
            consumer.registerMessageListener((MessageListenerOrderly) messageListener);
        }
        consumer.start();
        return consumer;
    }

    private static boolean createTopicAndAwaitCompletion(String topic) {
        boolean topicCreated = false;
        if (mqAdmin != null) {
            try {
                mqAdmin.createTopic(MixAll.SELF_TEST_TOPIC, topic, 1);
                await().atMost(5, TimeUnit.SECONDS)
                    .until(() -> mqClientInstance.updateTopicRouteInfoFromNameServer(topic));
                topicCreated = true;
            } catch (Exception e) {
                if (mqClientInstance.getTopicRouteTable().containsKey(topic)) {
                    topicCreated = true;
                }
            }
        }
        return topicCreated;
    }

    private static void doRequestMessageConsume(List<MessageExt> msgs) {
        try {
            sendCallbackDone.await();
        } catch (InterruptedException ignore) {
        }
        msgs.forEach(msg -> {
            try {
                producer.send(new Message(REPLY_TOPIC, msg.getBody()));
            } catch (MQClientException | RemotingException | MQBrokerException | InterruptedException e) {
                logger.error("Error in consume request message");
            }
        });
    }

    static class ReplyConsumer {

        private DefaultMQPullConsumer consumer;

        private long offset = -1;

        private Consumer<MessageExt> replyMsgConsumer = this::consumeReplyMsg;

        private void consumeReplyMsg(MessageExt messageExt) {
            assertThat(new String(messageExt.getBody()).equals(MESSAGE_BODY));
        }

        public synchronized void start() {
            try {
                consumer = new DefaultMQPullConsumer("ReplyConsumer");
                consumer.setNamesrvAddr(rocketMQ.getNameServer());
                consumer.start();
            } catch (MQClientException e) {
                logger.error("Error in reply message consumer start", e);
            }
        }

        public void consumeMessage(int msgNum) {
            Set<MessageQueue> queues;
            try {
                queues = consumer.fetchSubscribeMessageQueues(REPLY_TOPIC);
            } catch (MQClientException e) {
                logger.error("Error in fetch reply message queue", e);
                throw new IllegalStateException("fetch queue failure");
            }
            if (queues == null || queues.isEmpty()) {
                throw new IllegalStateException(String.format("Can't found queue in topic [%s]", REPLY_TOPIC));
            }

            List<MessageExt> messageExts = null;
            long startTs = System.currentTimeMillis();
            out:
            for (int needMsgNum = msgNum; needMsgNum > 0; ) {
                for (MessageQueue mq : queues) {
                    try {
                        if (offset < 0) {
                            offset = consumer.fetchConsumeOffset(mq, true);
                        }
                        PullResult pullResult = consumer.pullBlockIfNotFound(mq, null, offset, needMsgNum);
                        offset = pullResult.getNextBeginOffset();
                        if (pullResult.getPullStatus() == PullStatus.FOUND) {
                            List<MessageExt> foundMsgList = pullResult.getMsgFoundList();
                            // PullResult#getMsgFoundList return a ConsumeMessageListWrapper, don't replace with other List
                            if (messageExts == null) {
                                messageExts = foundMsgList;
                            } else {
                                messageExts.addAll(foundMsgList);
                            }
                            if (messageExts.size() == msgNum) {
                                break out;
                            } else {
                                needMsgNum = msgNum - foundMsgList.size();
                            }
                        } else {
                            logger.warn("Pull msg failure: mq={}, res={}", mq, pullResult);
                        }
                    } catch (Exception ignore) {

                    }
                }
                if ((messageExts == null || messageExts.size() < msgNum) && System.currentTimeMillis() - startTs >= 3000) {
                    throw new IllegalStateException("Pull msg timeout");
                }
            }

            assertThat(messageExts).isNotNull();
            messageExts.forEach(replyMsgConsumer);
        }

    }

    @Before
    public void before() {
        reporter.reset();
        startAndActivateTransaction(null);
        sendCallbackDone = new CountDownLatch(1);
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
    public void after() {
        Transaction currentTransaction = tracer.currentTransaction();
        if (currentTransaction != null) {
            currentTransaction.deactivate().end();
        }
    }

    @Test
    public void testSndTwoMessage_Normal() throws Exception {
        testScenario = TestScenario.NORMAL;
        sendTwoMessageAndConsumeReplies();
        verifyTracing();
    }

    @Test
    public void testBodyCaptureEnabled() throws Exception {
        when(coreConfiguration.getCaptureBody()).thenReturn(CoreConfiguration.EventType.ALL);
        testScenario = TestScenario.BODY_CAPTURE_ENABLED;
        sendTwoMessageAndConsumeReplies();
        verifyTracing();
    }

    @Test
    public void testHeaderCaptureDisabled() throws Exception {
        when(coreConfiguration.isCaptureHeaders()).thenReturn(false);
        testScenario = TestScenario.HEADERS_CAPTURE_DISABLED;
        sendTwoMessageAndConsumeReplies();
        verifyTracing();
    }

    @Test
    public void testHeaderSanitation() throws Exception {
        testScenario = TestScenario.SANITIZED_HEADER;
        sendTwoMessageAndConsumeReplies();
        verifyTracing();
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
    public void testIgnoreTopic() throws Exception {
        when(messagingConfiguration.getIgnoreMessageQueues()).thenReturn(List.of(WildcardMatcher.valueOf(REQUEST_TOPIC)));
        testScenario = TestScenario.IGNORE_REQUEST_TOPIC;
        sendTwoMessageAndConsumeReplies();

        List<Span> spans = reporter.getSpans();
        assertThat(spans).isEmpty();

        List<Transaction> transactions = reporter.getTransactions();
        assertThat(transactions).isEmpty();
    }

    @Test
    public void testTransactionCreationWithoutContext() throws Exception {
        testScenario = TestScenario.NO_CONTEXT_PROPAGATION;
        tracer.currentTransaction().deactivate().end();
        reporter.reset();

        sendTwoMessageAndConsumeReplies();

        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(4);
        spans.forEach(span -> verifySendSpanContents(span, REPLY_TOPIC));

        List<Transaction> transactions = reporter.getTransactions();
        assertThat(transactions).hasSize(8);
        verifyConsumeTransactionContents(transactions.get(0), null, REQUEST_TOPIC, null);
        verifyConsumeTransactionContents(transactions.get(1), null, REQUEST_TOPIC, null);
        verifyConsumeTransactionContents(transactions.get(2), null, REQUEST_TOPIC, null);
        verifyConsumeTransactionContents(transactions.get(3), null, REQUEST_TOPIC, null);
        verifyConsumeTransactionContents(transactions.get(4), spans.get(0), REPLY_TOPIC , null);
        verifyConsumeTransactionContents(transactions.get(5), spans.get(1), REPLY_TOPIC , null);
    }

    @Test
    public void testSendTwoRecords_TransactionNotSampled() throws Exception {
        testScenario = TestScenario.NON_SAMPLED_TRANSACTION;

        tracer.currentTransaction().deactivate().end();
        reporter.reset();
        startAndActivateTransaction(ConstantSampler.of(false));

        sendTwoMessageAndConsumeReplies();

        List<Span> spans = reporter.getSpans();
        assertThat(spans).isEmpty();
        List<Transaction> transactions = reporter.getTransactions();
        assertThat(transactions).hasSize(4);
        transactions.forEach(transaction -> {
            assertThat(transaction.isSampled()).isFalse();
            assertThat(transaction.getTraceContext().getTraceId()).isEqualTo(tracer.currentTransaction().getTraceContext().getTraceId());
            assertThat(transaction.getTraceContext().getParentId()).isEqualTo(tracer.currentTransaction().getTraceContext().getId());
            assertThat(transaction.getType()).isEqualTo("messaging");
            assertThat(transaction.getNameAsString()).isEqualTo(String.format("RocketMQ Consume Message#%s", REQUEST_TOPIC));
        });
    }

    private void sendTwoMessageAndConsumeReplies() throws Exception {
        Message requestMsg1 = new Message(REQUEST_TOPIC, MESSAGE_BODY.getBytes());
        Message requestMsg2 = new Message(REQUEST_TOPIC, MESSAGE_BODY.getBytes());

        String propertyName = (testScenario == TestScenario.SANITIZED_HEADER) ? PASSWORD_PROPERTY_KEY : NORMAL_PROPERTY_KEY;
        requestMsg1.putUserProperty(propertyName, PROPERTY_VALUE);
        requestMsg2.putUserProperty(propertyName, PROPERTY_VALUE);

        SendResult result = producer.send(requestMsg1);
        producer.send(requestMsg2, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                sendCallbackDone.countDown();
            }

            @Override
            public void onException(Throwable e) {
                sendCallbackDone.countDown();
            }
        });

        if (testScenario != TestScenario.IGNORE_REQUEST_TOPIC && testScenario != TestScenario.AGENT_PAUSED) {
            await().atMost(10, TimeUnit.SECONDS).until(() -> reporter.getTransactions().size() == 4);
        }

        replyConsumer.consumeMessage(4);
    }

    private void verifyTracing() {
        List<Span> spans = reporter.getSpans();

        assertThat(spans).hasSize(6);
        Span sendRequestSpan0 = spans.get(0);
        verifySendSpanContents(sendRequestSpan0, REQUEST_TOPIC);
        Span sendRequestSpan1 = spans.get(1);
        verifySendSpanContents(sendRequestSpan1, REQUEST_TOPIC);
        Span sendReplySpan1 = spans.get(2);
        verifySendSpanContents(sendReplySpan1, REPLY_TOPIC);
        Span sendReplySpan2 = spans.get(3);
        verifySendSpanContents(sendReplySpan2, REPLY_TOPIC);
        Span sendReplySpan3 = spans.get(4);
        verifySendSpanContents(sendReplySpan3, REPLY_TOPIC);
        Span sendReplySpan4 = spans.get(5);
        verifySendSpanContents(sendReplySpan4, REPLY_TOPIC);

        List<Transaction> transactions = reporter.getTransactions();
        assertThat(transactions).hasSize(4);

        Map<Id, List<Transaction>> transactionMap = new HashMap<>();
        transactions.forEach(transaction -> {
            Id parentId = transaction.getTraceContext().getParentId();
            List<Transaction> transactionList = transactionMap.computeIfAbsent(parentId, k -> new ArrayList<>());
            transactionList.add(transaction);
        });

        transactionMap.get(sendRequestSpan0.getTraceContext().getId()).forEach(transaction -> {
            verifyConsumeTransactionContents(transaction, sendRequestSpan0, REQUEST_TOPIC, MESSAGE_BODY);
        });

        transactionMap.get(sendRequestSpan1.getTraceContext().getId()).forEach(transaction -> {
            verifyConsumeTransactionContents(transaction, sendRequestSpan1, REQUEST_TOPIC, MESSAGE_BODY);
        });
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

    private void verifyConsumeTransactionContents(Transaction transaction, @Nullable Span parentSpan, String topic, @Nullable String messageValue) {
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
        Map<String, String> headerMap = new HashMap<>();
        for (Headers.Header header : headers) {
           headerMap.put(header.getKey(), Objects.requireNonNull(header.getValue()).toString());
        }

        if (testScenario == TestScenario.HEADERS_CAPTURE_DISABLED ||
            testScenario == TestScenario.SANITIZED_HEADER ||
            topic.equals(REPLY_TOPIC)) {
            assertThat(headerMap.get(NORMAL_PROPERTY_KEY)).isNull();
        } else {
            assertThat(headerMap.get(NORMAL_PROPERTY_KEY)).isNotNull();
            assertThat(headerMap.get(NORMAL_PROPERTY_KEY)).isEqualTo(PROPERTY_VALUE);
        }
        assertThat(headerMap.get(PASSWORD_PROPERTY_KEY)).isNull();
    }

}
