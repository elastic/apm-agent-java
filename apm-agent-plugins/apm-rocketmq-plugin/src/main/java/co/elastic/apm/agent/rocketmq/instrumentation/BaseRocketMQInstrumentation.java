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
package co.elastic.apm.agent.rocketmq.instrumentation;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.rocketmq.helper.RocketMQInstrumentationHelper;
import org.apache.rocketmq.client.consumer.PullCallback;
import org.apache.rocketmq.client.consumer.PullResult;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.impl.CommunicationMode;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;

import java.util.Arrays;
import java.util.Collection;

public abstract class BaseRocketMQInstrumentation extends ElasticApmInstrumentation {

    @VisibleForAdvice
    public static HelperClassManager<RocketMQInstrumentationHelper<Message, MessageQueue, CommunicationMode, SendCallback,
        MessageListenerConcurrently, MessageListenerOrderly, PullResult, PullCallback, MessageExt>> helperClassManager;

    public BaseRocketMQInstrumentation(ElasticApmTracer tracer) {
        BaseRocketMQInstrumentation.init(tracer);
    }

    private synchronized static void init(ElasticApmTracer tracer) {
        if (helperClassManager == null) {
            helperClassManager = HelperClassManager.ForAnyClassLoader.of(tracer,
                "co.elastic.apm.agent.rocketmq.helper.RocketMQInstrumentationHelperImpl",
                "co.elastic.apm.agent.rocketmq.helper.SendCallbackWrapper",
                "co.elastic.apm.agent.rocketmq.helper.MessageListenerOrderlyWrapper",
                "co.elastic.apm.agent.rocketmq.helper.MessageListenerConcurrentlyWrapper",
                "co.elastic.apm.agent.rocketmq.helper.ConsumeMessageListWrapper",
                "co.elastic.apm.agent.rocketmq.helper.ConsumeMessageIteratorWrapper",
                "co.elastic.apm.agent.rocketmq.helper.RocketMQMessageHeaderAccessor",
                "co.elastic.apm.agent.rocketmq.helper.PullCallbackWrapper");
        }
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("messaging", "rocketmq");
    }

}
