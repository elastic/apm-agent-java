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

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.MessagingConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Enumeration;

abstract public class JmsInstrumentationHelper<MESSAGE, DESTINATION, JMSEXCEPTION extends Exception, MESSAGELISTENER> {

    /**
     * In some cases, dashes are not allowed in JMS Message property names
     */
    protected static String JMS_TRACE_PARENT_PROPERTY = TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME.replace('-', '_');

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

    protected static final Logger logger = LoggerFactory.getLogger(JmsInstrumentationHelper.class);
    private final ElasticApmTracer tracer;
    private final CoreConfiguration coreConfiguration;
    private final MessagingConfiguration messagingConfiguration;

    public JmsInstrumentationHelper(ElasticApmTracer tracer) {
        this.tracer = tracer;
        coreConfiguration = tracer.getConfig(CoreConfiguration.class);
        messagingConfiguration = tracer.getConfig(MessagingConfiguration.class);
    }

    @SuppressWarnings("Duplicates")
    @Nullable
    public Span startJmsSendSpan(DESTINATION destination, MESSAGE message) {

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

        span.propagateTraceContext(message, propertyAccessorSetter());
        if (span.isSampled()) {
            span.getContext().getDestination().getService()
                .withName("jms")
                .withResource("jms")
                .withType(MESSAGING_TYPE);
            if (destinationName != null) {
                span.getContext().getDestination().getService().getResource().append("/").append(destinationName);
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
    public Transaction startJmsTransaction(MESSAGE parentMessage, Class<?> instrumentedClass) {
        Transaction transaction = tracer.startChildTransaction(parentMessage, propertyAccessorGetter(), instrumentedClass.getClassLoader());
        if (transaction != null) {
            transaction.setFrameworkName(FRAMEWORK_NAME);
        }
        return transaction;
    }

    public void makeChildOf(Transaction childTransaction, MESSAGE parentMessage) {
        TraceContext.<MESSAGE>getFromTraceContextTextHeaders().asChildOf(childTransaction.getTraceContext(), parentMessage, propertyAccessorGetter());
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
        long messageTimestamp = getJMSTimestamp(message);
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
            co.elastic.apm.agent.impl.context.Message messageContext = span.getContext().getMessage();

            // Currently only capturing body of TextMessages. The javax.jms.Message#getBody() API is since 2.0, so,
            // if we are supporting JMS 1.1, it makes no sense to rely on isAssignableFrom.
            if (coreConfiguration.getCaptureBody() != CoreConfiguration.EventType.OFF && isTextMessage(message)) {
                messageContext.withBody(getText(message));
            }

            // Addition of non-String headers/properties will cause String instance allocations
            if (coreConfiguration.isCaptureHeaders()) {
                messageContext.addHeader(JMS_MESSAGE_ID_HEADER, getJMSMessageID(message));
                messageContext.addHeader(JMS_EXPIRATION_HEADER, String.valueOf(getJMSExpiration(message)));
                messageContext.addHeader(JMS_TIMESTAMP_HEADER, String.valueOf(getJMSTimestamp(message)));

                Enumeration properties = getPropertyNames(message);
                if (properties != null) {
                    while (properties.hasMoreElements()) {
                        String propertyName = String.valueOf(properties.nextElement());
                        if (!propertyName.equals(JMS_DESTINATION_NAME_PROPERTY) && !propertyName.equals(JMS_TRACE_PARENT_PROPERTY)
                            && WildcardMatcher.anyMatch(coreConfiguration.getSanitizeFieldNames(), propertyName) == null) {
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
        final AbstractSpan<?> parent = GlobalTracer.get().getActive();
        if (parent == null) {
            createPollingTransaction = true;
        } else {
            if (parent instanceof Transaction) {
                Transaction transaction = (Transaction) parent;
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
            } else if (parent instanceof Span) {
                Span parentSpan = (Span) parent;
                if (MESSAGING_TYPE.equals(parentSpan.getType()) && "receive".equals(parentSpan.getAction())) {
                    // Avoid duplication for nested calls
                    return null;
                }
                createPollingSpan = true;
            }
        }

        createPollingTransaction &= messagingConfiguration.getMessagePollingTransactionStrategy() != MessagingConfiguration.Strategy.HANDLING;
        createPollingTransaction |= "receiveNoWait".equals(methodName);

        if (createPollingSpan) {
            createdSpan = parent.createSpan()
                .withType(MESSAGING_TYPE)
                .withSubtype("jms")
                .withAction("receive");
        } else if (createPollingTransaction) {
            createdSpan = GlobalTracer.get().startRootTransaction(clazz.getClassLoader());
            if (createdSpan != null) {
                ((Transaction) createdSpan).withType(MESSAGE_POLLING);
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
                destination = getDestination(message);
                destinationName = extractDestinationName(message, destination);
                discard = ignoreDestination(destinationName);
            } catch (Exception e) {
                logger.error("Failed to retrieve meta info from Message", e);
            }

            if (abstractSpan instanceof Transaction) {
                Transaction transaction = (Transaction) abstractSpan;
                if (discard) {
                    transaction.ignoreTransaction();
                } else {
                    makeChildOf(transaction, message);
                    transaction.withType(MESSAGING_TYPE);
                    addMessageDetails(message, abstractSpan);
                }
            }
        } else if (abstractSpan instanceof Transaction) {
            // Do not report polling transactions if not yielding messages
            ((Transaction) abstractSpan).ignoreTransaction();
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

        if (!discard && GlobalTracer.get().currentTransaction() == null
            && message != null
            && messagingConfiguration.getMessagePollingTransactionStrategy() != MessagingConfiguration.Strategy.POLLING
            && !"receiveNoWait".equals(methodName)) {

            Transaction messageHandlingTransaction = startJmsTransaction(message, clazz);
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

        if (message == null || GlobalTracer.get().currentTransaction() != null) {
            return null;
        }

        DESTINATION destination = null;
        String destinationName = null;
        long timestamp = 0;
        try {
            destination = getDestination(message);
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
        Transaction transaction = startJmsTransaction(message, clazz);
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
        if (transactionObj instanceof Transaction) {
            Transaction transaction = (Transaction) transactionObj;
            transaction.captureException(throwable);
            transaction.deactivate().end();
        }
    }

    public void deactivateSpan(@Nullable final Object spanObj, final Throwable throwable) {
        if (spanObj instanceof Span) {
            Span span = (Span) spanObj;
            span.captureException(throwable);
            span.deactivate().end();
        }
    }

    @Nullable
    abstract String extractDestinationName(@Nullable MESSAGE message, DESTINATION destination);

    abstract boolean isTempDestination(DESTINATION destination, @Nullable String extractedDestinationName);

    abstract TextHeaderGetter<MESSAGE> propertyAccessorGetter();

    abstract TextHeaderSetter<MESSAGE> propertyAccessorSetter();

    abstract void addDestinationDetails(DESTINATION destination, String destinationName, AbstractSpan<?> span);

    abstract MESSAGELISTENER newMessageListener(MESSAGELISTENER listener);

    abstract long getJMSTimestamp(MESSAGE message);

    abstract boolean isTextMessage(MESSAGE message);

    abstract String getText(MESSAGE message) throws JMSEXCEPTION;

    abstract String getJMSMessageID(MESSAGE message) throws JMSEXCEPTION;

    abstract long getJMSExpiration(MESSAGE message) throws JMSEXCEPTION;

    abstract Enumeration getPropertyNames(MESSAGE message) throws JMSEXCEPTION;

    abstract Object getObjectProperty(MESSAGE message, String propertyName) throws JMSEXCEPTION;

    abstract DESTINATION getDestination(MESSAGE message) throws JMSEXCEPTION;
}
