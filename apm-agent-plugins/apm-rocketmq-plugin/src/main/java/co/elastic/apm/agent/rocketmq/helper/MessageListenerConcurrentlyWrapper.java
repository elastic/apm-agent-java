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
package co.elastic.apm.agent.rocketmq.helper;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;

import javax.annotation.Nonnull;
import java.util.List;

public class MessageListenerConcurrentlyWrapper extends MessageListenerWrapper<ConsumeConcurrentlyStatus, ConsumeConcurrentlyContext> implements MessageListenerConcurrently {

    @Nonnull
    private MessageListenerConcurrently delegate;

    MessageListenerConcurrentlyWrapper(@Nonnull MessageListenerConcurrently delegate,
                                       @Nonnull ElasticApmTracer tracer) {
        super(tracer);
        this.delegate = delegate;
    }

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
        return doConsumeMessage(msgs, context);
    }

    @Override
    ConsumeConcurrentlyStatus consumeDelegateImpl(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
        return delegate.consumeMessage(msgs, context);
    }
}
