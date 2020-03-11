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
import org.apache.rocketmq.client.consumer.PullResult;
import org.apache.rocketmq.client.consumer.PullStatus;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageQueue;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public class RocketMQSyncPullConsumerTest extends AbstractRocketMQConsumerInstrumentationTest {

    private static DefaultMQPullConsumer consumer;

    private static long offset = -1;

    private static boolean running = false;

    @BeforeClass
    public static void setupConsumer() throws MQClientException {
        consumer = new DefaultMQPullConsumer("Request-Consumer");
        consumer.setNamesrvAddr(rocketmq.getNameServer());
        consumer.start();

        running = true;
        new Thread(() -> {
            try {
                while (running) {
                    MessageQueue messageQueue = consumer.fetchSubscribeMessageQueues(REQUEST_TOPIC).iterator().next();
                    if (offset < 0) {
                        offset = consumer.fetchConsumeOffset(messageQueue, true);
                    }
                    PullResult pullResult = consumer.pull(messageQueue, null, offset, 1, 2000);
                    if (pullResult.getPullStatus() == PullStatus.FOUND) {
                        doConsume(pullResult.getMsgFoundList());
                    }
                    offset = pullResult.getNextBeginOffset();
                }
            } catch (Exception ignore) {

            }
        }).start();
    }

    @AfterClass
    public static void shutdownConsumer() {
        running = false;
        consumer.shutdown();
    }

}
