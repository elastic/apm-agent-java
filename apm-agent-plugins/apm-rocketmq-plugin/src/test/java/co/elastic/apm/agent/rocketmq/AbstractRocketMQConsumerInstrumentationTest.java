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
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractRocketMQConsumerInstrumentationTest extends AbstractInstrumentationTest {

    private static final String NAME_SRV_HOST = "my-rocketmq";

    private static final String NAME_SRV_PORT = "9876";

    static final String NAME_SRV = NAME_SRV_HOST + ":" + NAME_SRV_PORT;

    private static final byte[] MESSAGE_BODY = "message body".getBytes(StandardCharsets.UTF_8);

    private static DefaultMQProducer producer;

    @BeforeClass
    public static void initProducer() throws MQClientException {
        if (producer == null) {
            synchronized (AbstractRocketMQConsumerInstrumentationTest.class) {
                if (producer == null) {
                    producer = new DefaultMQProducer(UUID.randomUUID().toString());
                    producer.setNamesrvAddr(NAME_SRV);
                    producer.start();
                }
            }
        }
    }

    @Before
    public void before() {
        reporter.reset();

        tracer.startRootTransaction(null)
            .withName("test")
            .withType("test")
            .activate();
    }

    @After
    public void after() {
        Transaction currentTransaction = tracer.currentTransaction();
        if (currentTransaction != null) {
            currentTransaction.deactivate().end();
        }
    }

    @Test
    public void testSendAndConsume() throws MQClientException {
        startConsumer();
        sendMessage();
        waitMsgReceived();
        stopConsumer();
        verifyTracing();
    }

    abstract void startConsumer() throws MQClientException;

    private void sendMessage() {
        Message message = new Message(getTopic(), MESSAGE_BODY);
        try {
            producer.send(message);
        } catch (Exception ignored) {

        }
    }

    protected void waitMsgReceived () {
        while (getMsgCnt() < 1) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
        }
    }

    abstract int getMsgCnt();

    private void verifyTracing() {
        verifySendSpan();
        verifyConsumeTransaction();
    }

    private void verifySendSpan() {
        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        for (Span span : spans) {
            verifySendSpanContents(span);
        }
    }

    private void verifySendSpanContents(Span span) {
        assertThat(span.getType()).isEqualTo("messaging");
        assertThat(span.getSubtype()).isEqualTo("rocketmq");
        assertThat(span.getAction()).isEqualTo("send");
        assertThat(span.getNameAsString()).isEqualTo(String.format("RocketMQ Send Message#%s", getTopic()));

        SpanContext context = span.getContext();

        assertThat(context.getMessage().getQueueName()).startsWith(getTopic());

        Destination.Service service = context.getDestination().getService();
        assertThat(service.getType()).isEqualTo("messaging");
        assertThat(service.getName().toString()).isEqualTo("rocketmq");
        assertThat(service.getResource().toString()).isEqualTo(String.format("rocketmq/%s", getTopic()));
    }

    private void verifyConsumeTransaction() {
        assertThat(reporter.getTransactions()).isNotEmpty();
        Transaction transaction = reporter.getFirstTransaction();
        verifyConsumeTransactionContents(transaction);
    }

    protected void verifyConsumeTransactionContents(Transaction transaction) {
        assertThat(transaction.getType()).isEqualTo("messaging");
        assertThat(transaction.getNameAsString()).isEqualTo(String.format("RocketMQ Consume Message#%s", getTopic()));
    }

    abstract String getTopic();

    abstract void stopConsumer();
}
