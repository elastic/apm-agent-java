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
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.context.SpanContext;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SuppressWarnings("NotNullFieldNotInitialized")
public class RocketMQInstrumentationTest extends AbstractInstrumentationTest {

    private static final String NAME_SRV_HOST = "my-rocketmq";

    private static final String NAME_SRV_PORT = "9876";

    private static final String NAME_SRV = NAME_SRV_HOST + ":" + NAME_SRV_PORT;

    private static final String PRODUCER_GROUP =  UUID.randomUUID().toString();

    private static final String CONSUMER_GROUP = UUID.randomUUID().toString();

    private static final String TOPIC = "ApmRocketMQPluginDev";

    private static final byte[] FIRST_MESSAGE_BODY = "First message body".getBytes(StandardCharsets.UTF_8);

    private static final byte[] SECOND_MESSAGE_BODY = "Second message body".getBytes(StandardCharsets.UTF_8);

    private static final byte[] THIRD_MESSAGE_BODY = "Third message body".getBytes(StandardCharsets.UTF_8);

    private static final String RE_CONSUME_TAG = "consume_later";

    private static final String CONSUME_WITH_EXP_TAG = "exception";

    private static DefaultMQProducer producer;

    private static DefaultMQPushConsumer consumer;

    @BeforeClass
    public static void setup() throws MQClientException {
        initProducer();
        initConsumer();
    }

    @AfterClass
    public static void tearDown() {
        producer.shutdown();
        consumer.shutdown();
    }

    private static void initProducer() throws MQClientException {
        producer = new DefaultMQProducer(PRODUCER_GROUP);
        producer.setNamesrvAddr(NAME_SRV);
        producer.start();
    }

    private static void initConsumer() throws MQClientException {
        consumer = new DefaultMQPushConsumer(CONSUMER_GROUP);
        consumer.setNamesrvAddr(NAME_SRV);
        consumer.registerMessageListener(new ConsumeMessageListener());
        consumer.subscribe(TOPIC, "*");
        consumer.start();
    }

    @Before
    public void startTransaction() {
        reporter.reset();
        Transaction transaction = tracer.startRootTransaction(null).activate();
        transaction.withName("RocketMQ-Test Transaction");
        transaction.withType("test");
    }

    @After
    public void endTransaction() {
        Transaction currentTransaction = tracer.currentTransaction();
        if (currentTransaction != null) {
            currentTransaction.deactivate().end();
        }
    }

    @Test
    public void testSendAndConsumeMessage() {
        sendMessage();
        verifyTracing();
    }

    private void sendMessage() {
        Message firstMessage = new Message(TOPIC, FIRST_MESSAGE_BODY);
        Message secondMessage = new Message(TOPIC, SECOND_MESSAGE_BODY);
        secondMessage.setTags(RE_CONSUME_TAG);
        Message thirdMessage = new Message(TOPIC, THIRD_MESSAGE_BODY);
        thirdMessage.setTags(CONSUME_WITH_EXP_TAG);
        try {
            producer.send(firstMessage);
            producer.sendOneway(secondMessage);
            producer.send(thirdMessage, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                }

                @Override
                public void onException(Throwable e) {
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }

        await().atMost(5000, TimeUnit.MILLISECONDS).until(() -> reporter.getTransactions().size() >= 3);
    }

    private void verifyTracing() {
        verifySendSpan();
        verifyConsumeTransaction();
    }

    private void verifySendSpan() {
        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(3);
        spans.forEach(this::verifySendSpanContents);
    }

    private void verifySendSpanContents(Span span) {
        assertThat(span.getType()).isEqualTo("messaging");
        assertThat(span.getSubtype()).isEqualTo("rocketmq");
        assertThat(span.getAction()).isEqualTo("send");
        assertThat(span.getNameAsString()).isEqualTo("RocketMQ Send Message#" + TOPIC);

        SpanContext context = span.getContext();

        assertThat(context.getMessage().getQueueName()).startsWith(TOPIC);

        Destination.Service service = context.getDestination().getService();
        assertThat(service.getType()).isEqualTo("messaging");
        assertThat(service.getName().toString()).isEqualTo("rocketmq");
        assertThat(service.getResource().toString()).isEqualTo("rocketmq/" + TOPIC);
    }

    private void verifyConsumeTransaction() {
        reporter.getTransactions().stream()
            .filter(this::isMessagingTransaction)
            .forEach(this::verifyConsumeTransactionContents);
    }

    private boolean isMessagingTransaction(Transaction transaction) {
        return "messaging".equals(transaction.getType());
    }

    private void verifyConsumeTransactionContents(Transaction transaction) {
        assertThat(transaction.getType()).isEqualTo("messaging");
        assertThat(transaction.getNameAsString()).isEqualTo("RocketMQ Consume Message#" + TOPIC);
        if (transaction.getResult() != null) {
            assertThat(transaction.getResult()).isIn(ConsumeConcurrentlyStatus.CONSUME_SUCCESS.name(),
                ConsumeConcurrentlyStatus.RECONSUME_LATER.name());
        }
    }

    static class ConsumeMessageListener implements MessageListenerConcurrently {

        @Override
        public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
            MessageExt msg = msgs.get(0);
            String tag = msg.getTags();
            if (RE_CONSUME_TAG.equals(tag)) {
                msg.setTags(null);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            } else if (CONSUME_WITH_EXP_TAG.equals(tag)) {
                msg.setTags(null);
                throw new RuntimeException("consume exception");
            } else {
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        }
    }

}
