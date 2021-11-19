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
package co.elastic.apm.agent.jms;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;

import javax.annotation.Nullable;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.Queue;
import jakarta.jms.TemporaryQueue;
import jakarta.jms.TemporaryTopic;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;
import java.util.Enumeration;

public class JakartaJmsInstrumentationHelper extends JmsInstrumentationHelper<Message, Destination, JMSException, MessageListener>{
    public JakartaJmsInstrumentationHelper(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Override
    TextHeaderGetter<Message> propertyAccessorGetter() {
        return JakartaJmsMessagePropertyAccessor.instance();
    }

    @Override
    TextHeaderSetter<Message> propertyAccessorSetter() {
        return JakartaJmsMessagePropertyAccessor.instance();
    }

    @Override
    MessageListener newMessageListener(final MessageListener listener) {
        return new MessageListener() {
            @Override
            public void onMessage(Message message) {
                listener.onMessage(message);
            }
        };
    }

    @Override
    boolean isTempDestination(Destination destination, @Nullable String extractedDestinationName) {
        return destination instanceof TemporaryQueue
            || destination instanceof TemporaryTopic
            || (extractedDestinationName != null && extractedDestinationName.startsWith(TIBCO_TMP_QUEUE_PREFIX));
    }

    @Override
    void addDestinationDetails(Destination destination, String destinationName, AbstractSpan<?> span) {

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
    boolean isTextMessage(Message message) {
        return message instanceof TextMessage;
    }

    @Override
    String getText(Message message) throws JMSException {
        return ((TextMessage) message).getText();
    }

    @Override
    long getJMSTimestamp(Message message)  {
        try {
            return message.getJMSTimestamp();
        } catch (JMSException e) {
            logger.warn("Failed to get message timestamp", e);
            return -1L;
        }

    }

    @Override
    long getJMSExpiration(Message message) throws JMSException {
        return message.getJMSExpiration();
    }

    @Override
    String getJMSMessageID(Message message) throws JMSException {
        return message.getJMSMessageID();
    }

    @Override
    String getObjectProperty(Message message, String propertyName) throws JMSException {
        return null;
    }

    @Override
    Enumeration getPropertyNames(Message message) throws JMSException {
        return message.getPropertyNames();
    }

    @Nullable
    public String extractDestinationName(@Nullable Message message, Destination destination) {
        String destinationName = null;
        if (message != null) {
            destinationName = JakartaJmsMessagePropertyAccessor.instance().getFirstHeader(JMS_DESTINATION_NAME_PROPERTY, message);
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
    Destination getDestination(Message message) throws JMSException {
        return message.getJMSDestination();
    }


}
