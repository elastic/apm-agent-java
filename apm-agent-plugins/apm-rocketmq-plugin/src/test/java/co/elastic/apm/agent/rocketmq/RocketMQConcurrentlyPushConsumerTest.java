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

import co.elastic.apm.agent.impl.transaction.Transaction;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.Ignore;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Ignore
public class RocketMQConcurrentlyPushConsumerTest extends AbstractRocketMQConsumerInstrumentationTest {

    private static DefaultMQPushConsumer consumer;

    private static String topic = "ApmRocketMQPluginTest-1";

    private static AtomicInteger cnt = new AtomicInteger(0);

    @Override
    protected void verifyConsumeTransactionContents(Transaction transaction) {
        super.verifyConsumeTransactionContents(transaction);
        if (transaction.getResult() != null) {
            assertThat(transaction.getResult()).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS.name());
        }
    }

    @Override
    String getTopic() {
        return topic;
    }

    @Override
    int getMsgCnt() {
        return cnt.get();
    }

    @Override
    void startConsumer() throws MQClientException {
        consumer = new DefaultMQPushConsumer("rocketmq-concurrently-push-consumer");
        consumer.setNamesrvAddr(NAME_SRV);
        consumer.subscribe(topic, "*");
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
                cnt.incrementAndGet();
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });
        consumer.start();
    }

    @Override
    void stopConsumer() {
        consumer.shutdown();
    }
}
