/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.jms;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import co.elastic.apm.agent.objectpool.Allocator;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.Recyclable;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import org.jctools.queues.atomic.AtomicQueueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.Topic;

import static org.jctools.queues.spec.ConcurrentQueueSpec.createBoundedMpmc;

@SuppressWarnings("unused")
public class JmsInstrumentationHelperImpl implements JmsInstrumentationHelper<Destination, Message, MessageListener> {

    private static final int MAX_POOLED_ELEMENTS = 256;

    private static final Logger logger = LoggerFactory.getLogger(JmsInstrumentationHelperImpl.class);
    private final ElasticApmTracer tracer;
    private final ObjectPool<MessageListenerWrapper> messageListenerWrapperObjectPool;

    public JmsInstrumentationHelperImpl(ElasticApmTracer tracer) {
        this.tracer = tracer;
        //noinspection RedundantTypeArguments
        messageListenerWrapperObjectPool = QueueBasedObjectPool.ofRecyclable(
            AtomicQueueFactory.<MessageListenerWrapper>newQueue(createBoundedMpmc(MAX_POOLED_ELEMENTS)),
            false,
            new MessageListenerWrapperAllocator());
    }

    private class MessageListenerWrapperAllocator implements Allocator<MessageListenerWrapper> {
        @Override
        public MessageListenerWrapper createInstance() {
            return new MessageListenerWrapper();
        }
    }

    @SuppressWarnings("Duplicates")
    @Override
    @VisibleForAdvice
    @Nullable
    public Span startJmsSendSpan(Destination destination, Message message) {

        final TraceContextHolder<?> activeSpan = tracer.getActive();
        if (activeSpan == null || !activeSpan.isSampled()) {
            return null;
        }

        Span span = activeSpan.createExitSpan();

        if (span == null) {
            return null;
        }

        span.withType("messaging")
            .withSubtype("jms")
            .withAction("send")
            .activate();

        try {
            if (span.isSampled()) {
                span.withName("JMS SEND");
                if (destination instanceof Queue) {
                    span.appendToName(" to queue ").appendToName(((Queue) destination).getQueueName());
                } else if (destination instanceof Topic) {
                    span.appendToName(" to topic ").appendToName(((Topic) destination).getTopicName());
                }
            }

            message.setStringProperty(JMS_TRACE_PARENT_HEADER, span.getTraceContext().getOutgoingTraceParentHeader().toString());
        } catch (JMSException e) {
            logger.error("Failed to capture JMS span", e);
        }
        return span;
    }

    @VisibleForAdvice
    @Override
    @Nullable
    public MessageListener wrapLambda(@Nullable MessageListener listener) {
        // the name check also prevents from wrapping twice
        if (listener != null && listener.getClass().getName().contains("/")) {
            return messageListenerWrapperObjectPool.createInstance().wrap(listener);
        }
        return listener;
    }

    private void recycle(MessageListenerWrapper messageListenerWrapper) {
        messageListenerWrapperObjectPool.recycle(messageListenerWrapper);
    }

    public class MessageListenerWrapper implements MessageListener, Recyclable {

        @Nullable
        private MessageListener delegate;

        MessageListenerWrapper() {
        }

        MessageListenerWrapper wrap(MessageListener delegate) {
            this.delegate = delegate;
            return this;
        }

        @Override
        public void onMessage(Message message) {
            try {
                if (delegate != null) {
                    delegate.onMessage(message);
                }
            } finally {
                JmsInstrumentationHelperImpl.this.recycle(this);
            }
        }

        @Override
        public void resetState() {
            delegate = null;
        }
    }
}
