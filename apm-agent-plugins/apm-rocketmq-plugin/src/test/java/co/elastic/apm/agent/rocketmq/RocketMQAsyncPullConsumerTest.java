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

import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.consumer.PullCallback;
import org.apache.rocketmq.client.consumer.PullResult;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.junit.Ignore;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Ignore
public class RocketMQAsyncPullConsumerTest extends AbstractRocketMQConsumerInstrumentationTest {

    private static final Map<MessageQueue, Long> offsetTable = new HashMap<>();
    private static DefaultMQPullConsumer consumer;
    private static String topic = "ApmRocketMQPluginTest-3";
    private static AtomicInteger cnt = new AtomicInteger(0);

    private static void putMessageQueueOffset(MessageQueue mq, long offset) {
        offsetTable.put(mq, offset);
    }

    private static long getMessageQueueOffset(MessageQueue mq) throws MQClientException {
        Long offset = offsetTable.get(mq);
        if (offset != null) {
            return offset;
        } else {
            return consumer.fetchConsumeOffset(mq, true);
        }
    }

    @Override
    void startConsumer() throws MQClientException {
        consumer = new DefaultMQPullConsumer("rocketmq-async-pull-consumer");
        consumer.setNamesrvAddr(NAME_SRV);
        consumer.start();

        final Set<MessageQueue> mqs = consumer.fetchSubscribeMessageQueues(topic);
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        for (final MessageQueue mq : mqs) {
                            long offset = getMessageQueueOffset(mq);
                            consumer.pull(mq, "*", offset, 32, new PullCallback() {
                                @Override
                                public void onSuccess(PullResult pullResult) {
                                    switch (pullResult.getPullStatus()) {
                                        case FOUND:
                                            for (MessageExt msg : pullResult.getMsgFoundList()) {
                                                cnt.incrementAndGet();
                                            }
                                            break;
                                        case NO_MATCHED_MSG:
                                            break;
                                        case NO_NEW_MSG:
                                            break;
                                        case OFFSET_ILLEGAL:
                                            break;
                                        default:
                                            break;
                                    }
                                }

                                @Override
                                public void onException(Throwable e) {

                                }
                            });
                            Thread.sleep(100);
                        }
                    } catch (Exception ignore) {

                    }
                }
            }}).start();
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
    void stopConsumer() {
        consumer.shutdown();
    }


}
