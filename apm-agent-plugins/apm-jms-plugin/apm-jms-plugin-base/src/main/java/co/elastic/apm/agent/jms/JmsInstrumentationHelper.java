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

import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.ElasticContext;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.tracer.configuration.CoreConfiguration;
import co.elastic.apm.agent.tracer.configuration.MessagingConfiguration;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderSetter;
import co.elastic.apm.agent.sdk.internal.util.PrivilegedActionUtils;

import javax.annotation.Nullable;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class JmsInstrumentationHelper<DESTINATION, MESSAGE, MESSAGELISTENER, JMSEXCEPTION extends Exception> {

    /**
     * When the agent computes a destination name instead of using the default queue name- it should be passed as a
     * message property, in case the receiver side cannot apply the same computation. For example, temporary queues are
     * identified based on the queue type and all receive the same generic name. In Artemis Active MQ, the queue
     * generated at the receiver side is not of the temporary type, so this name computation cannot be made.
     */
    public static final String JMS_DESTINATION_NAME_PROPERTY = "elastic_apm_dest_name";

    /**
     * Indicates a transaction is created for the message handling flow, but should not be used as the actual type of
     * reported transactions.
     */
    public static final String MESSAGE_HANDLING = "message-handling";

    /**
     * Indicates a transaction is created for a message polling method, but should not be used as the actual type of
     * reported transactions.
     */
    public static final String MESSAGE_POLLING = "message-polling";

    public static final String MESSAGING_TYPE = "messaging";

    public static final String RECEIVE_NAME_PREFIX = "JMS RECEIVE";

    // JMS known headers
    //----------------------
    public static final String JMS_MESSAGE_ID_HEADER = "JMSMessageID";
    public static final String JMS_EXPIRATION_HEADER = "JMSExpiration";
    public static final String JMS_TIMESTAMP_HEADER = "JMSTimestamp";

    public static final String TIBCO_TMP_QUEUE_PREFIX = "$TMP$";
    public static final String TEMP = "<temporary>";
    static final String FRAMEWORK_NAME = "JMS";

    private static final Logger logger = LoggerFactory.getLogger(JmsInstrumentationHelper.class);

    private final Tracer tracer;
    private final CoreConfiguration coreConfiguration;
    private final MessagingConfiguration messagingConfiguration;
    private final Set<String> jmsTraceHeaders = new HashSet<>();
    private final Map<String, String> translatedTraceHeaders = new HashMap<>();

    protected JmsInstrumentationHelper(Tracer tracer) {
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
    public Span<?> startJmsSendSpan(DESTINATION destination, MESSAGE message) {
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

        tracer.currentContext().propagateContext(message, propertyAccessorSetter(), null);

        if (span != null && span.isSampled()) {

            span.getContext().getServiceTarget()
                .withType("jms")
                .withName(destinationName);

            if (destinationName != null) {
                span.withName("JMS SEND to ");
                addDestinationDetails(destination, destinationName, span);
                if (isDestinationNameComputed) {
                    propertyAccessorSetter().setHeader(JMS_DESTINATION_NAME_PROPERTY, destinationName, message);
                }
            }
        }
        return span;
    }

    @Nullable
    public Transaction<?> startJmsTransaction(MESSAGE parentMessage, Class<?> instrumentedClass) {
        Transaction<?> transaction = tracer.startChildTransaction(parentMessage, propertyAccessorGetter(), PrivilegedActionUtils.getClassLoader(instrumentedClass));
        if (transaction != null) {
            transaction.setFrameworkName(FRAMEWORK_NAME);
        }
        return transaction;
    }

    @Nullable
    public MESSAGELISTENER wrapLambda(@Nullable MESSAGELISTENER listener) {
        // the name check also prevents from wrapping twice
        if (listener != null && listener.getClass().getName().contains("/")) {
            return newMessageListener(listener);
        }
        return listener;
    }

    public boolean ignoreDestination(@Nullable String destinationName) {
        return WildcardMatcher.isAnyMatch(messagingConfiguration.getIgnoreMessageQueues(), destinationName);
    }

    public void setMessageAge(MESSAGE message, AbstractSpan<?> span) {
        long messageTimestamp = -1L;
        try {
            messageTimestamp = getJMSTimestamp(message);
        } catch (Exception e) {
            logger.warn("Failed to get message timestamp", e);
        }
        if (messageTimestamp > 0) {
            long now = System.currentTimeMillis();
            long age = now > messageTimestamp ? now - messageTimestamp : 0;
            span.getContext().getMessage().withAge(age);
        }
    }

    public void addMessageDetails(@Nullable MESSAGE message, AbstractSpan<?> span) {
        if (message == null) {
            return;
        }
        try {
            co.elastic.apm.agent.tracer.metadata.Message messageContext = span.getContext().getMessage();

            // Currently only capturing body of TextMessages. The jakarta.jms.Message#getBody() API is since 2.0, so,
            // if we are supporting JMS 1.1, it makes no sense to rely on isAssignableFrom.
            if (coreConfiguration.getCaptureBody() != CoreConfiguration.EventType.OFF && isTextMessage(message)) {
                messageContext.withBody(getText(message));
            }

            // Addition of non-String headers/properties will cause String instance allocations
            if (coreConfiguration.isCaptureHeaders()) {
                messageContext.addHeader(JMS_MESSAGE_ID_HEADER, getJMSMessageID(message));
                messageContext.addHeader(JMS_EXPIRATION_HEADER, String.valueOf(getJMSExpiration(message)));
                messageContext.addHeader(JMS_TIMESTAMP_HEADER, String.valueOf(getJMSTimestamp(message)));

                Enumeration<?> properties = getPropertyNames(message);
                if (properties != null) {
                    while (properties.hasMoreElements()) {
                        String propertyName = String.valueOf(properties.nextElement());
                        if (!propertyName.equals(JMS_DESTINATION_NAME_PROPERTY) &&
                            !jmsTraceHeaders.contains(propertyName) &&
                            WildcardMatcher.anyMatch(coreConfiguration.getSanitizeFieldNames(), propertyName) == null) {
                            messageContext.addHeader(propertyName, String.valueOf(getObjectProperty(message, propertyName)));
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to retrieve message details", e);
        }
    }

    @Nullable
    public Object baseBeforeReceive(Class<?> clazz, String methodName) {
        AbstractSpan<?> createdSpan = null;
        boolean createPollingTransaction = false;
        boolean createPollingSpan = false;
        final ElasticContext<?> activeContext = tracer.currentContext();
        final AbstractSpan<?> parentSpan = activeContext.getSpan();
        if (parentSpan == null) {
            createPollingTransaction = true;
        } else {
            if (parentSpan instanceof Transaction<?>) {
                Transaction<?> transaction = (Transaction<?>) parentSpan;
                if (MESSAGE_POLLING.equals(transaction.getType())) {
                    // Avoid duplications for nested calls
                    return null;
                } else if (MESSAGE_HANDLING.equals(transaction.getType())) {
                    // A transaction created in the OnMethodExit of the poll- end it here
                    // Type must be changed to "messaging"
                    transaction.withType(MESSAGING_TYPE);
                    transaction.deactivate().end();
                    createPollingTransaction = true;
                } else {
                    createPollingSpan = true;
                }
            } else if (parentSpan instanceof Span<?>) {
                Span<?> parSpan = (Span<?>) parentSpan;
                if (MESSAGING_TYPE.equals(parSpan.getType()) && "receive".equals(parSpan.getAction())) {
                    // Avoid duplication for nested calls
                    return null;
                }
                createPollingSpan = true;
            }
        }

        createPollingTransaction &= messagingConfiguration.getMessagePollingTransactionStrategy() != MessagingConfiguration.JmsStrategy.HANDLING;
        createPollingTransaction |= "receiveNoWait".equals(methodName);

        if (createPollingSpan) {
            createdSpan = activeContext.createSpan()
                .withType(MESSAGING_TYPE)
                .withSubtype("jms")
                .withAction("receive");
        } else if (createPollingTransaction) {
            createdSpan = tracer.startRootTransaction(PrivilegedActionUtils.getClassLoader(clazz));
            if (createdSpan != null) {
                ((Transaction<?>) createdSpan).withType(MESSAGE_POLLING);
            }
        }

        if (createdSpan != null) {
            createdSpan.withName(RECEIVE_NAME_PREFIX);
            createdSpan.activate();
        }
        return createdSpan;
    }

    public void baseAfterReceive(Class<?> clazz, String methodName,
                                 @Nullable final Object abstractSpanObj,
                                 @Nullable final MESSAGE message,
                                 @Nullable final Throwable throwable) {
        AbstractSpan<?> abstractSpan = null;
        if (abstractSpanObj instanceof AbstractSpan<?>) {
            abstractSpan = (AbstractSpan<?>) abstractSpanObj;
        }
        DESTINATION destination = null;
        String destinationName = null;
        boolean discard = false;
        boolean addDetails = true;
        if (message != null) {
            try {
                destination = getJMSDestination(message);
                destinationName = extractDestinationName(message, destination);
                discard = ignoreDestination(destinationName);
            } catch (Exception e) {
                logger.error("Failed to retrieve meta info from Message", e);
            }

            if (abstractSpan instanceof Transaction<?>) {
                Transaction<?> transaction = (Transaction<?>) abstractSpan;
                if (discard) {
                    transaction.ignoreTransaction();
                } else {
                    transaction
                        .withType(MESSAGING_TYPE)
                        .addLink(propertyAccessorGetter(), message);
                    addMessageDetails(message, abstractSpan);
                }
            } else if (abstractSpan != null) {
                abstractSpan.addLink(propertyAccessorGetter(), message);
            }
        } else if (abstractSpan instanceof Transaction) {
            // Do not report polling transactions if not yielding messages
            ((Transaction<?>) abstractSpan).ignoreTransaction();
            addDetails = false;
        }

        if (abstractSpan != null) {
            try {
                if (discard) {
                    abstractSpan.requestDiscarding();
                } else if (addDetails) {
                    if (message != null && destinationName != null) {
                        abstractSpan.appendToName(" from ");
                        addDestinationDetails(destination, destinationName, abstractSpan);
                        setMessageAge(message, abstractSpan);
                    }
                    abstractSpan.captureException(throwable);
                }
            } finally {
                abstractSpan.deactivate().end();
            }
        }

        if (!discard && tracer.currentTransaction() == null
            && message != null
            && messagingConfiguration.getMessagePollingTransactionStrategy() != MessagingConfiguration.JmsStrategy.POLLING
            && !"receiveNoWait".equals(methodName)) {

            Transaction<?> messageHandlingTransaction = startJmsTransaction(message, clazz);
            if (messageHandlingTransaction != null) {
                messageHandlingTransaction.withType(MESSAGE_HANDLING)
                    .withName(RECEIVE_NAME_PREFIX);

                if (destinationName != null) {
                    messageHandlingTransaction.appendToName(" from ");
                    addDestinationDetails(destination, destinationName, messageHandlingTransaction);
                    addMessageDetails(message, messageHandlingTransaction);
                    setMessageAge(message, messageHandlingTransaction);
                }

                messageHandlingTransaction.activate();
            }
        }
    }

    @Nullable
    public Object baseBeforeOnMessage(@Nullable final MESSAGE message, Class<?> clazz) {
        if (message == null || tracer.currentTransaction() != null) {
            return null;
        }

        DESTINATION destination = null;
        String destinationName = null;
        long timestamp = 0;
        try {
            destination = getJMSDestination(message);
            timestamp = getJMSTimestamp(message);
        } catch (Exception e) {
            logger.warn("Failed to retrieve message's destination", e);
        }

        if (destination != null) {
            destinationName = extractDestinationName(message, destination);
            if (ignoreDestination(destinationName)) {
                return null;
            }
        }

        // Create a transaction - even if running on same JVM as the sender
        Transaction<?> transaction = startJmsTransaction(message, clazz);
        if (transaction != null) {
            transaction.withType(MESSAGING_TYPE)
                .withName(RECEIVE_NAME_PREFIX);

            if (destinationName != null) {
                addDestinationDetails(destination, destinationName, transaction.appendToName(" from "));
            }
            addMessageDetails(message, transaction);
            setMessageAge(message, transaction);
            transaction.activate();
        }

        return transaction;
    }

    public void deactivateTransaction(@Nullable final Object transactionObj, final Throwable throwable) {
        if (transactionObj instanceof Transaction<?>) {
            Transaction<?> transaction = (Transaction<?>) transactionObj;
            transaction.captureException(throwable);
            transaction.deactivate().end();
        }
    }

    protected abstract String extractDestinationName(@Nullable MESSAGE message, DESTINATION destination);

    protected abstract boolean isTempDestination(DESTINATION destination, @Nullable String extractedDestinationName);

    protected abstract TextHeaderGetter<MESSAGE> propertyAccessorGetter();

    protected abstract TextHeaderSetter<MESSAGE> propertyAccessorSetter();

    protected abstract void addDestinationDetails(DESTINATION destination, String destinationName, AbstractSpan<?> span);

    protected abstract MESSAGELISTENER newMessageListener(MESSAGELISTENER listener);

    protected abstract long getJMSTimestamp(MESSAGE message) throws JMSEXCEPTION;

    protected abstract boolean isTextMessage(MESSAGE message);

    protected abstract String getText(MESSAGE message) throws JMSEXCEPTION;

    protected abstract String getJMSMessageID(MESSAGE message) throws JMSEXCEPTION;

    protected abstract long getJMSExpiration(MESSAGE message) throws JMSEXCEPTION;

    protected abstract Enumeration getPropertyNames(MESSAGE message) throws JMSEXCEPTION;

    protected abstract Object getObjectProperty(MESSAGE message, String propertyName) throws JMSEXCEPTION;

    public abstract void setStringProperty(MESSAGE message, String propertyName, String value) throws JMSEXCEPTION;

    protected abstract DESTINATION getJMSDestination(MESSAGE message) throws JMSEXCEPTION;

}
