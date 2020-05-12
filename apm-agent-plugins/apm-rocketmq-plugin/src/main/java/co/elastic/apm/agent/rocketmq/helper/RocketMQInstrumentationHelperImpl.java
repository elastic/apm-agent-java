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

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.MessagingConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import org.apache.rocketmq.client.consumer.MQConsumer;
import org.apache.rocketmq.client.consumer.PullCallback;
import org.apache.rocketmq.client.consumer.PullResult;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.impl.CommunicationMode;
import org.apache.rocketmq.client.impl.consumer.PullResultExt;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class RocketMQInstrumentationHelperImpl implements RocketMQInstrumentationHelper<Message, MessageQueue, CommunicationMode,
    SendCallback, MessageListenerConcurrently, MessageListenerOrderly, PullResult, PullCallback, MessageExt> {

    private static final Logger logger = LoggerFactory.getLogger(RocketMQInstrumentationHelperImpl.class);

    private final ElasticApmTracer tracer;

    public RocketMQInstrumentationHelperImpl(ElasticApmTracer tracer) {
        this.tracer = tracer;
    }

    private CoreConfiguration getCoreConfiguration() {
        return tracer.getConfig(CoreConfiguration.class);
    }

    private MessagingConfiguration getMessagingConfiguration() {
        return tracer.getConfig(MessagingConfiguration.class);
    }

    public ElasticApmTracer getTracer() {
        return tracer;
    }

    @Override
    @Nullable
    public Span onSendStart(Message msg, MessageQueue mq, CommunicationMode communicationMode) {
        String topic = msg.getTopic();
        if (ignoreTopic(topic)) {
            return null;
        }

        final TraceContextHolder<?> parent = tracer.getActive();

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

    @Override
    @Nullable
    public SendCallback wrapSendCallback(@Nullable SendCallback delegate, Span span) {
        if (delegate == null || delegate instanceof SendCallbackWrapper) {
            return delegate;
        }
        return new SendCallbackWrapper(delegate, span);
    }

    @Override
    @Nullable
    public MessageListenerConcurrently wrapMessageListenerConcurrently(@Nullable MessageListenerConcurrently listenerConcurrently) {
        if (listenerConcurrently != null && isLambda(listenerConcurrently)) {
            return new MessageListenerConcurrentlyWrapper(listenerConcurrently);
        }
        return listenerConcurrently;
    }

    @Override
    @Nullable
    public MessageListenerOrderly wrapMessageListenerOrderly(@Nullable MessageListenerOrderly listenerOrderly) {
        if (listenerOrderly != null && isLambda(listenerOrderly)) {
            return new MessageListenerOrderlyWrapper(listenerOrderly);
        }
        return listenerOrderly;
    }

    @Override
    @Nullable
    public PullResult replaceMsgList(@Nullable PullResult delegate) {
        if (delegate == null || delegate.getMsgFoundList() == null || delegate.getMsgFoundList() instanceof ConsumeMessageListWrapper) {
            return delegate;
        } else if (delegate instanceof PullResultExt) {
            PullResultExt pullResultExt = (PullResultExt) delegate;
            return new PullResultExt(pullResultExt.getPullStatus(),
                pullResultExt.getNextBeginOffset(),
                pullResultExt.getMinOffset(),
                pullResultExt.getMaxOffset(),
                new ConsumeMessageListWrapper(pullResultExt.getMsgFoundList(), this),
                pullResultExt.getSuggestWhichBrokerId(),
                pullResultExt.getMessageBinary());
        } else {
            return new PullResult(delegate.getPullStatus(),
                delegate.getNextBeginOffset(),
                delegate.getMinOffset(),
                delegate.getMaxOffset(),
                new ConsumeMessageListWrapper(delegate.getMsgFoundList(), this));
        }
    }

    @Override
    @Nullable
    public PullCallback wrapPullCallback(@Nullable final PullCallback delegate) {
        if (delegate == null || delegate instanceof PullCallbackWrapper) {
            return delegate;
        }
        return new PullCallbackWrapper(delegate, this);
    }

    @Override
    public List<MessageExt> wrapMessageList(List<MessageExt> msgs) {
        if (!(msgs instanceof ConsumeMessageListWrapper)){
            return new ConsumeMessageListWrapper(msgs, this);
        }
        return msgs;
    }

    @Override
    @Nullable
    public Transaction onConsumeStart(MessageExt msg) {
        Transaction transaction = null;
        try {
            String topic = msg.getTopic();
            if (!ignoreTopic(topic)) {
                transaction = tracer.startChildTransaction(msg,
                    RocketMQMessageHeaderAccessor.getInstance(),
                    MQConsumer.class.getClassLoader());
                if (transaction != null) {
                    transaction.withType("messaging")
                        .withName("RocketMQ Consume Message#" + topic)
                        .activate();

                    co.elastic.apm.agent.impl.context.Message messageContext = transaction.getContext().getMessage();
                    messageContext.withQueue(topic).withAge(System.currentTimeMillis() - msg.getBornTimestamp());

                    if (transaction.isSampled() && getCoreConfiguration().isCaptureHeaders()) {
                        for (Map.Entry<String, String> property: msg.getProperties().entrySet()) {
                            if (!TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME.equals(property.getKey()) &&
                                WildcardMatcher.anyMatch(getCoreConfiguration().getSanitizeFieldNames(), property.getKey()) == null) {
                                messageContext.addHeader(property.getKey(), property.getValue());
                            }
                        }
                    }

                    if (transaction.isSampled() && getCoreConfiguration().getCaptureBody() != CoreConfiguration.EventType.OFF) {
                        messageContext.appendToBody("msgId=").appendToBody(msg.getMsgId()).appendToBody("; ")
                            .appendToBody("body=").appendToBody(new String(msg.getBody()));
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error in RocketMQ consume transaction creation", e);
        }
        return transaction;
    }

    @Override
    public void onConsumeEnd(Transaction transaction, Throwable throwable, Object ret) {
        try {
            if (ret instanceof ConsumeConcurrentlyStatus) {
                transaction.withResult(((ConsumeConcurrentlyStatus)ret).name());
            } else if (ret instanceof ConsumeOrderlyStatus){
                transaction.withResult(((ConsumeOrderlyStatus)ret).name());
            }
            transaction.captureException(throwable)
                .deactivate()
                .end();
        } catch (Exception e) {
            logger.error("Error in transaction end", e);
        }

    }

    private boolean ignoreTopic(String topic) {
        return WildcardMatcher.isAnyMatch(getMessagingConfiguration().getIgnoreMessageQueues(), topic);
    }

    private boolean isLambda(Object obj) {
        return obj.getClass().getName().contains("/");
    }
}
