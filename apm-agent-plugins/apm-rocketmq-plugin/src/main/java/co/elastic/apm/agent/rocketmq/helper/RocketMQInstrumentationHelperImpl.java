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

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.configuration.MessagingConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import org.apache.rocketmq.client.consumer.PullCallback;
import org.apache.rocketmq.client.consumer.PullResult;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.impl.CommunicationMode;
import org.apache.rocketmq.client.impl.consumer.PullResultExt;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RocketMQInstrumentationHelperImpl implements RocketMQInstrumentationHelper {

    private static final Logger logger = LoggerFactory.getLogger(RocketMQInstrumentationHelperImpl.class);

    private final MessagingConfiguration messagingConfiguration;

    public RocketMQInstrumentationHelperImpl(ElasticApmTracer tracer) {
        this.messagingConfiguration = tracer.getConfig(MessagingConfiguration.class);
    }

    private ElasticApmTracer getTracer() {
        return ElasticApmInstrumentation.tracer;
    }

    @Override
    public Span onSendStart(Message msg, MessageQueue mq, CommunicationMode communicationMode) {
        String topic = msg.getTopic();
        if (ignoreTopic(topic)) {
            return null;
        }

        final TraceContextHolder<?> parent = getTracer().getActive();

        if (null == parent) {
            return null;
        }

        Span span = parent.createExitSpan();
        if (null == span) {
            return null;
        }

        span.withType("messaging")
            .withSubtype("rocketmq")
            .withAction("send")
            .withName("RocketMQ Send Message#" + topic);
        span.getContext().getMessage().withQueue(topic + "/" + mq.getBrokerName() + "/" + mq.getQueueId());
        span.getContext().getDestination().getService().withType("messaging").withName("rocketmq")
            .getResource().append("rocketmq/").append(topic);

        try {
            span.getTraceContext().setOutgoingTraceContextHeaders(msg, RocketMQMessageHeaderAccessor.getInstance());
        } catch (Exception exp) {
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to add user property to rocketmq message {} because {}", msg, exp.getMessage());
            }
        }

        span.activate();

        return span;
    }

    private boolean ignoreTopic(String topic) {
        return WildcardMatcher.isAnyMatch(messagingConfiguration.getIgnoreMessageQueues(), topic);
    }

    @Override
    public SendCallback wrapSendCallback(SendCallback delegate, Span span) {
        if (delegate == null) {
            return null;
        }
        if (delegate instanceof SendCallbackWrapper) {
            return delegate;
        }
        return new SendCallbackWrapper(delegate, span);
    }


    @Override
    public MessageListenerConcurrently wrapMessageListener(MessageListenerConcurrently listenerConcurrently) {
        if (listenerConcurrently == null) {
            return null;
        }
        if (listenerConcurrently instanceof MessageListenerConcurrentlyWrapper) {
            return listenerConcurrently;
        }
        return new MessageListenerConcurrentlyWrapper(listenerConcurrently, getTracer());
    }

    @Override
    public MessageListenerOrderly wrapMessageListener(MessageListenerOrderly listenerOrderly) {
        if (listenerOrderly == null) {
            return null;
        }
        if (listenerOrderly instanceof MessageListenerOrderlyWrapper) {
            return listenerOrderly;
        }
        return new MessageListenerOrderlyWrapper(listenerOrderly, getTracer());
    }

    @Override
    public PullResult wrapPullResult(PullResult delegate) {
        if (delegate == null || delegate.getMsgFoundList() == null || delegate.getMsgFoundList() instanceof ConsumeMessageListWrapper) {
            return delegate;
        } else if (delegate instanceof PullResultExt) {
            PullResultExt pullResultExt = (PullResultExt) delegate;
            return new PullResultExt(pullResultExt.getPullStatus(),
                pullResultExt.getNextBeginOffset(),
                pullResultExt.getMinOffset(),
                pullResultExt.getMaxOffset(),
                new ConsumeMessageListWrapper(pullResultExt.getMsgFoundList(), getTracer()),
                pullResultExt.getSuggestWhichBrokerId(),
                pullResultExt.getMessageBinary());
        } else {
            return new PullResult(delegate.getPullStatus(),
                delegate.getNextBeginOffset(),
                delegate.getMinOffset(),
                delegate.getMaxOffset(),
                new ConsumeMessageListWrapper(delegate.getMsgFoundList(), getTracer()));
        }
    }

    @Override
    public PullCallback wrapPullCallback(final PullCallback delegate) {
        if (delegate == null) {
            return null;
        }
        return new PullCallbackWrapper(delegate, this);
    }

}
