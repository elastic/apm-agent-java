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
package co.elastic.apm.agent.jms;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.MessagingConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

@SuppressWarnings("unused")
public class JmsInstrumentationHelperImpl implements JmsInstrumentationHelper<Destination, Message, MessageListener> {

    static final String TIBCO_TMP_QUEUE_PREFIX = "$TMP$";
    static final String TEMP = "<temporary>";
    static final String FRAMEWORK_NAME = "JMS";

    private static final Logger logger = LoggerFactory.getLogger(JmsInstrumentationHelperImpl.class);
    private final ElasticApmTracer tracer;
    private final CoreConfiguration coreConfiguration;
    private final MessagingConfiguration messagingConfiguration;

    public JmsInstrumentationHelperImpl(ElasticApmTracer tracer) {
        this.tracer = tracer;
        coreConfiguration = tracer.getConfig(CoreConfiguration.class);
        messagingConfiguration = tracer.getConfig(MessagingConfiguration.class);
    }

    @SuppressWarnings("Duplicates")
    @Override
    @VisibleForAdvice
    @Nullable
    public Span startJmsSendSpan(Destination destination, Message message) {

        final AbstractSpan<?> activeSpan = tracer.getActive();
        if (activeSpan == null) {
            return null;
        }

        boolean isDestinationNameComputed = false;
        String destinationName = extractDestinationName(null, destination);
        if (isTempDestination(destination, destinationName)) {
            destinationName = TEMP;
            isDestinationNameComputed = true;
        }
        if (ignoreDestination(destinationName)) {
            return null;
        }

        Span span = activeSpan.createExitSpan();

        if (span == null) {
            return null;
        }

        span.withType(MESSAGING_TYPE)
            .withSubtype("jms")
            .withAction("send")
            .activate();

        try {
            span.propagateTraceContext(message, JmsMessagePropertyAccessor.instance());
            if (span.isSampled()) {
                span.getContext().getDestination().getService()
                    .withName("jms")
                    .withResource("jms")
                    .withType(MESSAGING_TYPE);
                if (destinationName != null) {
                    span.getContext().getDestination().getService().getResource().append("/").append(destinationName);
                    span.withName("JMS SEND to ");
                    addDestinationDetails(null, destination, destinationName, span);
                    if (isDestinationNameComputed) {
                        message.setStringProperty(JMS_DESTINATION_NAME_PROPERTY, destinationName);
                    }
                }
            }
        } catch (JMSException e) {
            logger.error("Failed to capture JMS span", e);
        }
        return span;
    }

    @Override
    @Nullable
    public Transaction startJmsTransaction(Message parentMessage, Class<?> instrumentedClass) {
        Transaction transaction = tracer.startChildTransaction(parentMessage, JmsMessagePropertyAccessor.instance(), instrumentedClass.getClassLoader());
        if (transaction != null) {
            transaction.setFrameworkName(FRAMEWORK_NAME);
        }
        return transaction;
    }

    @Override
    public void makeChildOf(Transaction childTransaction, Message parentMessage) {
        TraceContext.<Message>getFromTraceContextTextHeaders().asChildOf(childTransaction.getTraceContext(), parentMessage, JmsMessagePropertyAccessor.instance());
    }

    @VisibleForAdvice
    @Override
    @Nullable
    public MessageListener wrapLambda(@Nullable MessageListener listener) {
        // the name check also prevents from wrapping twice
        if (listener != null && listener.getClass().getName().contains("/")) {
            return new MessageListenerWrapper(listener);
        }
        return listener;
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

    @Nullable
    public String extractDestinationName(@Nullable Message message, Destination destination) {
        String destinationName = null;
        if (message != null) {
            try {
                destinationName = message.getStringProperty(JMS_DESTINATION_NAME_PROPERTY);
            } catch (JMSException e) {
                logger.warn("Failed to get destination name from message property", e);
            }
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

    private boolean isTempDestination(Destination destination, @Nullable String extractedDestinationName) {
        return destination instanceof TemporaryQueue || destination instanceof TemporaryTopic ||
            (extractedDestinationName != null && extractedDestinationName.startsWith(TIBCO_TMP_QUEUE_PREFIX));
    }

    @Override
    public boolean ignoreDestination(@Nullable String destinationName) {
        return WildcardMatcher.isAnyMatch(messagingConfiguration.getIgnoreMessageQueues(), destinationName);
    }

    @Override
    public void addDestinationDetails(@Nullable Message message, Destination destination, String destinationName,
                                      AbstractSpan span) {
        if (destination instanceof Queue) {
            span.appendToName("queue ").appendToName(destinationName)
                .getContext().getMessage().withQueue(destinationName);
        } else if (destination instanceof Topic) {
            span.appendToName("topic ").appendToName(destinationName)
                .getContext().getMessage().withQueue(destinationName);
        }
    }

    @Override
    public void setMessageAge(Message message, AbstractSpan span) {
        try {
            long messageTimestamp = message.getJMSTimestamp();
            if (messageTimestamp > 0) {
                long now = System.currentTimeMillis();
                if (now > messageTimestamp) {
                    span.getContext().getMessage().withAge(now - messageTimestamp);
                } else {
                    span.getContext().getMessage().withAge(0);
                }
            }
        } catch (JMSException e) {
            logger.warn("Failed to get message timestamp", e);
        }
    }

    @Override
    public void addMessageDetails(@Nullable Message message, AbstractSpan span) {
        if (message == null) {
            return;
        }
        try {
            co.elastic.apm.agent.impl.context.Message messageContext = span.getContext().getMessage();

            // Currently only capturing body of TextMessages. The javax.jms.Message#getBody() API is since 2.0, so,
            // if we are supporting JMS 1.1, it makes no sense to rely on isAssignableFrom.
            if (coreConfiguration.getCaptureBody() != CoreConfiguration.EventType.OFF && message instanceof TextMessage) {
                messageContext.withBody(((TextMessage) message).getText());
            }

            // Addition of non-String headers/properties will cause String instance allocations
            if (coreConfiguration.isCaptureHeaders()) {
                messageContext.addHeader(JMS_MESSAGE_ID_HEADER, message.getJMSMessageID());
                messageContext.addHeader(JMS_EXPIRATION_HEADER, String.valueOf(message.getJMSExpiration()));
                messageContext.addHeader(JMS_TIMESTAMP_HEADER, String.valueOf(message.getJMSTimestamp()));

                Enumeration properties = message.getPropertyNames();
                while (properties.hasMoreElements()) {
                    String propertyName = String.valueOf(properties.nextElement());
                    if (!propertyName.equals(JMS_DESTINATION_NAME_PROPERTY) && !propertyName.equals(JMS_TRACE_PARENT_PROPERTY)
                        && WildcardMatcher.anyMatch(coreConfiguration.getSanitizeFieldNames(), propertyName) == null) {
                        messageContext.addHeader(propertyName, String.valueOf(message.getObjectProperty(propertyName)));
                    }
                }
            }
        } catch (JMSException e) {
            logger.warn("Failed to retrieve message details", e);
        }
    }
}
