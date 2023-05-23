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
package co.elastic.apm.agent.awssdk.v1.helper;

import co.elastic.apm.agent.awssdk.common.AbstractSQSInstrumentationHelper;
import co.elastic.apm.agent.awssdk.v1.helper.sqs.wrapper.ReceiveMessageResultWrapper;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderSetter;
import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.Request;
import com.amazonaws.http.ExecutionContext;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageBatchRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.SendMessageRequest;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class SQSHelper extends AbstractSQSInstrumentationHelper<Request<?>, ExecutionContext, Message> implements TextHeaderSetter<Map<String, MessageAttributeValue>> {

    private static final SQSHelper INSTANCE = new SQSHelper(GlobalTracer.get());

    public static SQSHelper getInstance() {
        return INSTANCE;
    }

    protected SQSHelper(Tracer tracer) {
        super(tracer, SdkV1DataSource.getInstance());
    }


    public void propagateContext(Span<?> span, AmazonWebServiceRequest request) {
        if (request instanceof SendMessageRequest) {
            SendMessageRequest sendMessageRequest = (SendMessageRequest) request;
            span.propagateTraceContext(sendMessageRequest.getMessageAttributes(), this);
        } else if (request instanceof SendMessageBatchRequest) {
            SendMessageBatchRequest sendMessageBatchRequest = (SendMessageBatchRequest) request;
            for (SendMessageBatchRequestEntry entry : sendMessageBatchRequest.getEntries()) {
                span.propagateTraceContext(entry.getMessageAttributes(), this);
            }
        }
    }

    public void setMessageAttributeNames(ReceiveMessageRequest receiveMessageRequest) {
        List<String> messageAttributeNames = receiveMessageRequest.getMessageAttributeNames();
        for (String header : tracer.getTraceHeaderNames()) {
            if (!messageAttributeNames.contains(ATTRIBUTE_NAME_ALL) && !messageAttributeNames.contains(header)) {
                messageAttributeNames.add(header);
            }
        }

        List<String> attributeNames = receiveMessageRequest.getAttributeNames();
        if (!attributeNames.contains(ATTRIBUTE_NAME_SENT_TIMESTAMP)) {
            attributeNames.add(ATTRIBUTE_NAME_SENT_TIMESTAMP);
        }
    }

    @Override
    protected String getMessageBody(Message sqsMessage) {
        return sqsMessage.getBody();
    }

    protected long getMessageAge(Message message) {
        String value = message.getAttributes().get(ATTRIBUTE_NAME_SENT_TIMESTAMP);
        if (value != null) {
            try {
                long sentTimestampMs = Long.parseLong(value);
                return System.currentTimeMillis() - sentTimestampMs;
            } catch (Throwable t) {
                return Long.MIN_VALUE;
            }
        }

        return Long.MIN_VALUE;
    }

    @Override
    protected Collection<String> getMessageAttributeKeys(Message sqsMessage) {
        return sqsMessage.getMessageAttributes().keySet();
    }

    @Nullable
    @Override
    protected String getMessageAttribute(Message sqsMessage, String key) {
        if (sqsMessage.getMessageAttributes() != null && sqsMessage.getMessageAttributes().containsKey(key)) {
            MessageAttributeValue value = sqsMessage.getMessageAttributes().get(key);
            if (value.getDataType().equals(ATTRIBUTE_DATA_TYPE_STRING)) {
                return value.getStringValue();
            }
        }
        return null;
    }

    @Override
    protected boolean isReceiveMessageRequest(Request<?> request) {
        return request instanceof ReceiveMessageRequest;
    }

    @Override
    protected void setMessageContext(@Nullable Message sqsMessage, @Nullable String queueName, co.elastic.apm.agent.tracer.metadata.Message message) {
        if (queueName != null) {
            message.withQueue(queueName);
        }

        if (sqsMessage != null) {
            long messageAge = getMessageAge(sqsMessage);
            if (messageAge >= 0) {
                message.withAge(messageAge);
            }

            if (coreConfiguration.isCaptureHeaders()) {
                for (Map.Entry<String, MessageAttributeValue> entry : sqsMessage.getMessageAttributes().entrySet()) {
                    String key = entry.getKey();
                    if (!tracer.getTraceHeaderNames().contains(key) &&
                        entry.getValue().getDataType().equals(ATTRIBUTE_DATA_TYPE_STRING) &&
                        WildcardMatcher.anyMatch(coreConfiguration.getSanitizeFieldNames(), key) == null) {
                        message.addHeader(key, entry.getValue().getStringValue());
                    }
                }
            }

            if (coreConfiguration.getCaptureBody() != CoreConfiguration.EventType.OFF) {
                message.appendToBody(sqsMessage.getBody());
            }
        }
    }

    /**
     * Enrich the span if there is already an active SQS span that has been started through dedicated instrumentation.
     * Otherwise, create and start a new span.
     *
     * @return Returns the span object if a new span has been created. Returns null if an active span has been enriched or span could not be created.
     */
    @Nullable
    @Override
    public Span<?> startSpan(Request<?> request, URI httpURI, ExecutionContext context) {
        if (isAlreadyActive(request)) {
            AbstractSpan<?> active = tracer.getActive();
            if (active instanceof Span<?>) {
                Span<?> activeSpan = (Span<?>) active;
                if (activeSpan.isExit() && SQS_TYPE.equals(activeSpan.getSubtype())) {
                    enrichSpan(activeSpan, request, request.getEndpoint(), context);
                    activeSpan.withSync(isRequestSync(request.getOriginalRequest()));
                }
            }
        } else {
            Span<?> span = super.startSpan(request, request.getEndpoint(), context);
            if (span != null) {
                span.withSync(isRequestSync(request.getOriginalRequest()));
                return span;
            }
        }

        return null;
    }

    /**
     * Spans for sending and receiving messages are created and ended in a dedicated instrumentation.
     * Thus, they must not be activated and must not be ended in this instrumentation.
     */
    private static boolean isAlreadyActive(Request<?> request) {
        AmazonWebServiceRequest amazonRequest = request.getOriginalRequest();
        return (amazonRequest instanceof SendMessageRequest ||
            amazonRequest instanceof SendMessageBatchRequest ||
            amazonRequest instanceof ReceiveMessageRequest);
    }

    private boolean isRequestSync(AmazonWebServiceRequest amazonRequest) {
        Boolean isAsync = amazonRequest.getHandlerContext(Constants.ASYNC_HANDLER_CONTEXT);
        return isAsync == null || !isAsync;
    }

    public ReceiveMessageResult wrapResult(ReceiveMessageRequest receiveMessageRequest, ReceiveMessageResult result) {
        ReceiveMessageResult returnedResult = result;
        List<Message> messages = result.getMessages();

        if (messages.size() > 0) {
            String queueName = SdkV1DataSource.getInstance().getQueueNameFromQueueUrl(receiveMessageRequest.getQueueUrl());
            if (queueName != null) {
                returnedResult = new ReceiveMessageResultWrapper(result, SQSHelper.getInstance().getTracer(), queueName);
            }
        }
        return returnedResult;
    }

    @Override
    public void setHeader(String headerName, String headerValue, Map<String, MessageAttributeValue> map) {
        map.put(headerName, new MessageAttributeValue().withDataType(ATTRIBUTE_DATA_TYPE_STRING).withStringValue(headerValue));
    }

    @Nullable
    @Override
    public String getFirstHeader(String headerName, Message message) {
        MessageAttributeValue value = message.getMessageAttributes().get(headerName);
        if (value != null && value.getDataType().equals(ATTRIBUTE_DATA_TYPE_STRING)) {
            return value.getStringValue();
        }
        return null;
    }

    @Override
    public <S> void forEach(String headerName, Message message, S state, HeaderConsumer<String, S> consumer) {
        for (MessageAttributeValue value : message.getMessageAttributes().values()) {
            if (value != null && value.getDataType().equals(ATTRIBUTE_DATA_TYPE_STRING)) {
                consumer.accept(value.getStringValue(), state);
            }
        }
    }
}
