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
package co.elastic.apm.agent.awssdk.common;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.MessagingConfiguration;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.tracer.metadata.Message;
import co.elastic.apm.agent.util.PrivilegedActionUtils;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractSQSInstrumentationHelper<R, C, MessageT> extends AbstractAwsSdkInstrumentationHelper<R, C> implements TextHeaderGetter<MessageT> {
    private static final Logger logger = LoggerFactory.getLogger(AbstractSQSInstrumentationHelper.class);
    protected static final String SQS_TYPE = "sqs";
    protected static final String MESSAGE_PROCESSING_ACTION = "processing";
    protected static final String MESSAGING_TYPE = "messaging";
    protected static final String ATTRIBUTE_NAME_SENT_TIMESTAMP = "SentTimestamp";
    protected static final String ATTRIBUTE_DATA_TYPE_STRING = "String";
    protected static final String ATTRIBUTE_NAME_ALL = "All";
    protected static final String FRAMEWORK_NAME = "AWS SQS";

    public static final Map<String, SpanNameAction> SPAN_NAME_LOOKUP = new HashMap<>();

    static {
        SPAN_NAME_LOOKUP.put("SendMessage", new SpanNameAction("SEND to", "send"));
        SPAN_NAME_LOOKUP.put("SendMessageBatch", new SpanNameAction("SEND_BATCH to", "send_batch"));
        SPAN_NAME_LOOKUP.put("ReceiveMessage", new SpanNameAction("POLL from", "poll"));
        SPAN_NAME_LOOKUP.put("DeleteMessage", new SpanNameAction("DELETE from", "delete"));
        SPAN_NAME_LOOKUP.put("DeleteMessageBatch", new SpanNameAction("DELETE_BATCH from", "delete_batch"));
    }

    protected final MessagingConfiguration messagingConfiguration;
    protected final CoreConfiguration coreConfiguration;

    protected abstract String getMessageBody(MessageT sqsMessage);

    /**
     * Returns the message age in milliseconds if available.
     * Otherwise returns a negative long value.
     */
    protected abstract long getMessageAge(MessageT sqsMessage);

    protected abstract Collection<String> getMessageAttributeKeys(MessageT sqsMessage);

    @Nullable
    protected abstract String getMessageAttribute(MessageT sqsMessage, String key);

    protected abstract boolean isReceiveMessageRequest(R request);

    protected AbstractSQSInstrumentationHelper(Tracer tracer, IAwsSdkDataSource<R, C> awsSdkDataSource) {
        super(tracer, awsSdkDataSource);
        this.messagingConfiguration = tracer.getConfig(MessagingConfiguration.class);
        this.coreConfiguration = tracer.getConfig(CoreConfiguration.class);
    }

    @Nullable
    public Span<?> createSpan(@Nullable String queueName) {
        if (WildcardMatcher.isAnyMatch(messagingConfiguration.getIgnoreMessageQueues(), queueName)) {
            return null;
        }
        AbstractSpan<?> active = tracer.getActive();
        if (active == null) {
            return null;
        }
        Span<?> span = active.createExitSpan();
        if (span != null) {
            span.withType(MESSAGING_TYPE)
                .withSubtype(SQS_TYPE);
        }
        return span;
    }

    public void enrichSpan(Span<?> span, R request, URI httpURI, C context) {
        String operationName = awsSdkDataSource.getOperationName(request, context);
        String queueName = awsSdkDataSource.getFieldValue(IAwsSdkDataSource.QUEUE_NAME_FIELD, request);

        String action = operationName;
        String spanNameOperation = operationName;

        SpanNameAction nameAndAction = SPAN_NAME_LOOKUP.get(operationName);
        if (nameAndAction != null) {
            action = nameAndAction.getAction();
            spanNameOperation = nameAndAction.getSpanNameOperation();
        }

        span.withType(MESSAGING_TYPE)
            .withSubtype(SQS_TYPE)
            .withAction(action);

        if (span.isSampled()) {
            StringBuilder name = span.getAndOverrideName(AbstractSpan.PRIORITY_DEFAULT);
            if (name != null) {
                name.append("SQS ").append(spanNameOperation);
                if (queueName != null && !queueName.isEmpty()) {
                    name.append(" ").append(queueName);
                }
            }
            span.withName("SQS", AbstractSpan.PRIORITY_DEFAULT - 1);

            if (queueName != null) {
                span.getContext().getMessage()
                    .withQueue(queueName);
            }

            setDestinationContext(span, httpURI, request, context, SQS_TYPE, queueName);
        }
    }

    @Nullable
    public Span<?> startSpan(R request, URI httpURI, C context) {
        AbstractSpan<?> activeSpan = tracer.getActive();

        if (isReceiveMessageRequest(request) && messagingConfiguration.shouldEndMessagingTransactionOnPoll() && activeSpan instanceof Transaction) {
            Transaction<?> transaction = (Transaction<?>) activeSpan;
            if (MESSAGING_TYPE.equals(transaction.getType())) {
                transaction.deactivate().end();
                return null;
            }
        }

        String queueName = awsSdkDataSource.getFieldValue(IAwsSdkDataSource.QUEUE_NAME_FIELD, request);

        Span<?> span = createSpan(queueName);
        if (span != null) {
            enrichSpan(span, request, httpURI, context);
        }

        return span;
    }

    public void startTransactionOnMessage(MessageT sqsMessage, String queueName, TextHeaderGetter<MessageT> headerGetter) {
        try {
            if (!WildcardMatcher.isAnyMatch(messagingConfiguration.getIgnoreMessageQueues(), queueName)) {
                Transaction<?> transaction = tracer.startChildTransaction(sqsMessage, headerGetter, PrivilegedActionUtils.getClassLoader(AbstractSQSInstrumentationHelper.class));
                if (transaction != null) {
                    transaction.withType(MESSAGING_TYPE).withName("SQS RECEIVE from " + queueName).activate();
                    transaction.setFrameworkName(FRAMEWORK_NAME);

                    if (transaction.isSampled()) {
                        setMessageContext(sqsMessage, queueName, transaction.getContext().getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error in transaction creation based on SQS message", e);
        }
    }

    private void addSpanLink(Span<?> span, MessageT sqsMessage, TextHeaderGetter<MessageT> headerGetter) {
        span.addLink(headerGetter, sqsMessage);
    }

    protected void setMessageContext(@Nullable MessageT sqsMessage, @Nullable String queueName, Message message) {
        if (queueName != null) {
            message.withQueue(queueName);
        }

        if (sqsMessage != null) {
            long messageAge = getMessageAge(sqsMessage);
            if (messageAge >= 0) {
                message.withAge(messageAge);
            }

            if (coreConfiguration.isCaptureHeaders()) {
                for (String key : getMessageAttributeKeys(sqsMessage)) {
                    String value = getMessageAttribute(sqsMessage, key);
                    if (!tracer.getTraceHeaderNames().contains(key) &&
                        value != null &&
                        WildcardMatcher.anyMatch(coreConfiguration.getSanitizeFieldNames(), key) == null) {
                        message.addHeader(key, value);
                    }
                }
            }

            if (coreConfiguration.getCaptureBody() != CoreConfiguration.EventType.OFF) {
                message.appendToBody(getMessageBody(sqsMessage));
            }
        }
    }

    public void handleReceivedMessages(Span<?> span, String queueUrl, @Nullable List<MessageT> messages) {
        String queueName = awsSdkDataSource.getQueueNameFromQueueUrl(queueUrl);
        MessageT singleMessage = null;
        if (messages != null) {
            if (messages.size() == 1) {
                singleMessage = messages.get(0);
            }
            for (MessageT msg : messages) {
                addSpanLink(span, msg, this);
            }
        }
        setMessageContext(singleMessage, queueName, span.getContext().getMessage());
    }

    private static class SpanNameAction {
        private final String spanName;
        private final String action;

        public SpanNameAction(String spanName, String action) {
            this.spanName = spanName;
            this.action = action;
        }

        public String getSpanNameOperation() {
            return spanName;
        }

        public String getAction() {
            return action;
        }
    }
}
