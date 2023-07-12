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

import co.elastic.apm.agent.tracer.configuration.CoreConfiguration;
import co.elastic.apm.agent.tracer.configuration.MessagingConfiguration;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.sdk.internal.util.PrivilegedActionUtils;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class JmsInstrumentationHelper {

    /**
     * When the agent computes a destination name instead of using the default queue name- it should be passed as a
     * message property, in case the receiver side cannot apply the same computation. For example, temporary queues are
     * identified based on the queue type and all receive the same generic name. In Artemis Active MQ, the queue
     * generated at the receiver side is not of the temporary type, so this name computation cannot be made.
     */
    protected static String JMS_DESTINATION_NAME_PROPERTY = "elastic_apm_dest_name";

    /**
     * Indicates a transaction is created for the message handling flow, but should not be used as the actual type of
     * reported transactions.
     */
    protected static String MESSAGE_HANDLING = "message-handling";

    /**
     * Indicates a transaction is created for a message polling method, but should not be used as the actual type of
     * reported transactions.
     */
    protected static String MESSAGE_POLLING = "message-polling";

    protected static String MESSAGING_TYPE = "messaging";

    protected static String RECEIVE_NAME_PREFIX = "JMS RECEIVE";

    // JMS known headers
    //----------------------
    protected static String JMS_MESSAGE_ID_HEADER = "JMSMessageID";
    protected static String JMS_EXPIRATION_HEADER = "JMSExpiration";
    protected static String JMS_TIMESTAMP_HEADER = "JMSTimestamp";

    static final String TIBCO_TMP_QUEUE_PREFIX = "$TMP$";
    static final String TEMP = "<temporary>";
    static final String FRAMEWORK_NAME = "JMS";

    private static final Logger logger = LoggerFactory.getLogger(JmsInstrumentationHelper.class);

    private static final JmsInstrumentationHelper INSTANCE = new JmsInstrumentationHelper(GlobalTracer.get());

    private final Tracer tracer;
    private final CoreConfiguration coreConfiguration;
    private final MessagingConfiguration messagingConfiguration;
    private final Set<String> jmsTraceHeaders = new HashSet<>();
    private final Map<String, String> translatedTraceHeaders = new HashMap<>();

    public static JmsInstrumentationHelper get() {
        return INSTANCE;
    }

    private JmsInstrumentationHelper(Tracer tracer) {
        this.tracer = tracer;
        coreConfiguration = tracer.getConfig(CoreConfiguration.class);
        messagingConfiguration = tracer.getConfig(MessagingConfiguration.class);
        Set<String> traceHeaders = tracer.getTraceHeaderNames();
        for (String traceHeader : traceHeaders) {
            String jmsTraceHeader = traceHeader.replace('-', '_');
            if (!jmsTraceHeaders.add(jmsTraceHeader)) {
                throw new IllegalStateException("Ambiguous translation of trace headers into JMS-compatible format: " + traceHeaders);
            }
            translatedTraceHeaders.put(traceHeader, jmsTraceHeader);
        }
    }

    public String resolvePossibleTraceHeader(String header) {
        String translation = translatedTraceHeaders.get(header);
        return translation == null ? header : translation;
    }

    @SuppressWarnings("Duplicates")
    @Nullable
    public Span<?> startJmsSendSpan(Destination destination, Message message) {
        String destinationName = extractDestinationName(null, destination);
        boolean isDestinationNameComputed = false;
        if (isTempDestination(destination, destinationName)) {
            destinationName = TEMP;
            isDestinationNameComputed = true;
        }
        if (ignoreDestination(destinationName)) {
            return null;
        }

        Span<?> span = tracer.currentContext().createExitSpan();
        if (span != null) {
            span.withType(MESSAGING_TYPE)
                .withSubtype("jms")
                .withAction("send")
                .activate();
        }

        tracer.currentContext().propagateContext(message, JmsMessagePropertyAccessor.instance(), null);

        if (span != null && span.isSampled()) {

            span.getContext().getServiceTarget()
                .withType("jms")
                .withName(destinationName);

            if (destinationName != null) {
                span.withName("JMS SEND to ");
                addDestinationDetails(destination, destinationName, span);
                if (isDestinationNameComputed) {
                    JmsMessagePropertyAccessor.instance().setHeader(JMS_DESTINATION_NAME_PROPERTY, destinationName, message);
                }
            }
        }

        return span;
    }

    @Nullable
    public Transaction<?> startJmsTransaction(Message parentMessage, Class<?> instrumentedClass) {
        Transaction<?> transaction = tracer.startChildTransaction(parentMessage, JmsMessagePropertyAccessor.instance(), PrivilegedActionUtils.getClassLoader(instrumentedClass));
        if (transaction != null) {
            transaction.setFrameworkName(FRAMEWORK_NAME);
        }
        return transaction;
    }

    @Nullable
    public MessageListener wrapLambda(@Nullable MessageListener listener) {
        // the name check also prevents from wrapping twice
        if (listener != null && listener.getClass().getName().contains("/")) {
            return new JmsInstrumentationHelper.MessageListenerWrapper(listener);
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

    private boolean isTempDestination(Destination destination, @Nullable String extractedDestinationName) {
        return destination instanceof TemporaryQueue
            || destination instanceof TemporaryTopic
            || (extractedDestinationName != null && extractedDestinationName.startsWith(TIBCO_TMP_QUEUE_PREFIX));
    }

    public boolean ignoreDestination(@Nullable String destinationName) {
        return WildcardMatcher.isAnyMatch(messagingConfiguration.getIgnoreMessageQueues(), destinationName);
    }

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

    public void setMessageAge(Message message, AbstractSpan<?> span) {
        long messageTimestamp = -1L;
        try {
            messageTimestamp = message.getJMSTimestamp();
        } catch (JMSException e) {
            logger.warn("Failed to get message timestamp", e);
        }
        if (messageTimestamp > 0) {
            long now = System.currentTimeMillis();
            long age = now > messageTimestamp ? now - messageTimestamp : 0;
            span.getContext().getMessage().withAge(age);
        }
    }

    public void addMessageDetails(@Nullable Message message, AbstractSpan<?> span) {
        if (message == null) {
            return;
        }
        try {
            co.elastic.apm.agent.tracer.metadata.Message messageContext = span.getContext().getMessage();

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

                Enumeration<?> properties = message.getPropertyNames();
                if (properties != null) {
                    while (properties.hasMoreElements()) {
                        String propertyName = String.valueOf(properties.nextElement());
                        if (!propertyName.equals(JMS_DESTINATION_NAME_PROPERTY) &&
                            !jmsTraceHeaders.contains(propertyName) &&
                            WildcardMatcher.anyMatch(coreConfiguration.getSanitizeFieldNames(), propertyName) == null) {
                            messageContext.addHeader(propertyName, String.valueOf(message.getObjectProperty(propertyName)));
                        }
                    }
                }
            }
        } catch (JMSException e) {
            logger.warn("Failed to retrieve message details", e);
        }
    }
}
