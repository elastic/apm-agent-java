/*
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
 */
package co.elastic.apm.agent.jms.javax;

import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderSetter;

import javax.annotation.Nullable;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.util.Enumeration;

public class JmsInstrumentationHelper extends co.elastic.apm.agent.jms.JmsInstrumentationHelper<Destination, Message, MessageListener, JMSException> {

    private static final Logger logger = LoggerFactory.getLogger(JmsInstrumentationHelper.class);

    private static final JmsInstrumentationHelper INSTANCE = new JmsInstrumentationHelper(GlobalTracer.get());

    public static JmsInstrumentationHelper get() {
        return INSTANCE;
    }

    private JmsInstrumentationHelper(Tracer tracer) {
        super(tracer);
    }

    public static class MessageListenerWrapper implements MessageListener {

        private final MessageListener delegate;

        MessageListenerWrapper(MessageListener delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onMessage(Message message) {
            delegate.onMessage(message);
        }
    }

    @Override
    public MessageListener newMessageListener(MessageListener listener) {
        return new MessageListenerWrapper(listener);
    }

    @Nullable
    public String extractDestinationName(@Nullable Message message, Destination destination) {
        String destinationName = null;
        if (message != null) {
            destinationName = JmsMessagePropertyAccessor.instance().getFirstHeader(JMS_DESTINATION_NAME_PROPERTY, message);
        }
        if (destinationName == null) {
            try {
                if (destination instanceof Queue) {
                    destinationName = ((Queue) destination).getQueueName();
                } else if (destination instanceof Topic) {
                    destinationName = ((Topic) destination).getTopicName();
                }
            } catch (JMSException e) {
                logger.error("Failed to obtain destination name", e);
            }
        }
        return destinationName;
    }

    @Override
    public boolean isTempDestination(Destination destination, @Nullable String extractedDestinationName) {
        return destination instanceof TemporaryQueue
            || destination instanceof TemporaryTopic
            || (extractedDestinationName != null && extractedDestinationName.startsWith(TIBCO_TMP_QUEUE_PREFIX));
    }

    @Override
    public TextHeaderGetter<Message> propertyAccessorGetter() {
        return JmsMessagePropertyAccessor.instance();
    }

    @Override
    public TextHeaderSetter<Message> propertyAccessorSetter() {
        return JmsMessagePropertyAccessor.instance();
    }

    @Override
    public void addDestinationDetails(Destination destination,
                                      String destinationName,
                                      AbstractSpan<?> span) {

        String prefix = null;
        if (destination instanceof Queue) {
            prefix = "queue ";
        } else if (destination instanceof Topic) {
            prefix = "topic ";
        }

        if (prefix != null) {
            span.appendToName(prefix).appendToName(destinationName)
                .getContext().getMessage().withQueue(destinationName);
        }
    }

    @Override
    public long getJMSTimestamp(Message message) throws JMSException {
        return message.getJMSTimestamp();
    }

    @Override
    public boolean isTextMessage(Message message) {
        return message instanceof TextMessage;
    }

    @Override
    public String getText(Message message) throws JMSException {
        return ((TextMessage) message).getText();
    }

    @Override
    public String getJMSMessageID(Message message) throws JMSException {
        return message.getJMSMessageID();
    }

    @Override
    public long getJMSExpiration(Message message) throws JMSException {
        return message.getJMSExpiration();
    }

    @Override
    public Enumeration getPropertyNames(Message message) throws JMSException {
        return message.getPropertyNames();
    }

    @Override
    public Object getObjectProperty(Message message, String propertyName) throws JMSException {
        return message.getObjectProperty(propertyName);
    }

    @Override
    public void setObjectProperty(Message message, String propertyName, Object value) throws JMSException {
        message.setObjectProperty(propertyName, value);
    }

    @Override
    protected Destination getJMSDestination(Message message) throws JMSException {
        return message.getJMSDestination();
    }

}
