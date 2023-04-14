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
package co.elastic.apm.agent.awssdk.v2.helper;

import co.elastic.apm.agent.awssdk.common.AbstractSQSInstrumentationHelper;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderSetter;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.client.handler.ClientExecutionParams;
import software.amazon.awssdk.core.http.ExecutionContext;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"rawtypes", "unchecked"})
public class SQSHelper extends AbstractSQSInstrumentationHelper<SdkRequest, ExecutionContext, Message> implements TextHeaderSetter<Map<String, MessageAttributeValue>> {

    private static final SQSHelper INSTANCE = new SQSHelper(GlobalTracer.get());

    public static SQSHelper getInstance() {
        return INSTANCE;
    }

    public SQSHelper(Tracer tracer) {
        super(tracer, SdkV2DataSource.getInstance());
    }

    private SdkRequest propagateContext(Span<?> span, SdkRequest sdkRequest) {
        if (sdkRequest instanceof SendMessageRequest) {
            SendMessageRequest sendMessageRequest = (SendMessageRequest) sdkRequest;
            Map<String, MessageAttributeValue> attributesMap = new HashMap<>(sendMessageRequest.messageAttributes());
            span.propagateTraceContext(attributesMap, this);
            return sendMessageRequest.toBuilder().messageAttributes(attributesMap).build();
        } else if (sdkRequest instanceof SendMessageBatchRequest) {
            SendMessageBatchRequest sendMessageBatchRequest = (SendMessageBatchRequest) sdkRequest;
            SendMessageBatchRequestEntry[] newEntries = new SendMessageBatchRequestEntry[sendMessageBatchRequest.entries().size()];
            int idx = 0;
            for (SendMessageBatchRequestEntry entry : sendMessageBatchRequest.entries()) {
                Map<String, MessageAttributeValue> attributesMap = new HashMap<>(entry.messageAttributes());
                span.propagateTraceContext(attributesMap, this);
                newEntries[idx] = entry.toBuilder().messageAttributes(attributesMap).build();
                idx++;
            }
            sendMessageBatchRequest.toBuilder().entries(newEntries).build();
        }
        return sdkRequest;
    }

    @Override
    protected String getMessageBody(Message sqsMessage) {
        return sqsMessage.body();
    }

    protected long getMessageAge(Message message) {
        String value = message.attributesAsStrings().get(ATTRIBUTE_NAME_SENT_TIMESTAMP);
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
        return sqsMessage.messageAttributes().keySet();
    }

    @Nullable
    @Override
    protected String getMessageAttribute(Message sqsMessage, String key) {
        if (sqsMessage.hasMessageAttributes() && sqsMessage.messageAttributes().containsKey(key)) {
            MessageAttributeValue value = sqsMessage.messageAttributes().get(key);
            if (value.dataType().equals(ATTRIBUTE_DATA_TYPE_STRING)) {
                return value.stringValue();
            }
        }
        return null;
    }

    @Override
    protected boolean isReceiveMessageRequest(SdkRequest request) {
        return request instanceof ReceiveMessageRequest;
    }

    public void modifyRequestObject(@Nullable Span<?> span, ClientExecutionParams clientExecutionParams, ExecutionContext executionContext) {
        SdkRequest sdkRequest = clientExecutionParams.getInput();
        SdkRequest newRequestObj = null;
        if (span != null && clientExecutionParams.getOperationName().startsWith("SendMessage")) {
            newRequestObj = SQSHelper.getInstance().propagateContext(span, clientExecutionParams.getInput());
        } else if (sdkRequest instanceof ReceiveMessageRequest) {
            ReceiveMessageRequest receiveMessageRequest = (ReceiveMessageRequest) sdkRequest;
            if (!receiveMessageRequest.messageAttributeNames().contains(ATTRIBUTE_NAME_ALL) &&
                Collections.disjoint(receiveMessageRequest.messageAttributeNames(), tracer.getTraceHeaderNames())) {

                List<String> newMessageAttributeNames = new ArrayList<>(receiveMessageRequest.messageAttributeNames().size() + 2);
                newMessageAttributeNames.addAll(receiveMessageRequest.messageAttributeNames());
                newMessageAttributeNames.addAll(tracer.getTraceHeaderNames());

                List<String> attributeNames;
                if (receiveMessageRequest.attributeNamesAsStrings().isEmpty()) {
                    attributeNames = Collections.singletonList(ATTRIBUTE_NAME_SENT_TIMESTAMP);
                } else {
                    attributeNames = new ArrayList<>(receiveMessageRequest.attributeNamesAsStrings().size() + 1);
                    attributeNames.add(ATTRIBUTE_NAME_SENT_TIMESTAMP);
                }

                newRequestObj = receiveMessageRequest.toBuilder()
                    .messageAttributeNames(newMessageAttributeNames)
                    .attributeNamesWithStrings(attributeNames).build();
            }
        }

        if (newRequestObj != null) {
            clientExecutionParams.withInput(newRequestObj);
            executionContext.interceptorContext(executionContext.interceptorContext().toBuilder().request(newRequestObj).build());
        }
    }

    @Override
    public void setHeader(String headerName, String headerValue, Map<String, MessageAttributeValue> map) {
        map.put(headerName, MessageAttributeValue.builder().dataType(ATTRIBUTE_DATA_TYPE_STRING).stringValue(headerValue).build());
    }

    @Nullable
    @Override
    public String getFirstHeader(String headerName, Message message) {
        MessageAttributeValue value = message.messageAttributes().get(headerName);
        if (value != null && value.dataType().equals(ATTRIBUTE_DATA_TYPE_STRING)) {
            return value.stringValue();
        }
        return null;
    }

    @Override
    public <S> void forEach(String headerName, Message message, S state, HeaderConsumer<String, S> consumer) {
        for (MessageAttributeValue value : message.messageAttributes().values()) {
            if (value != null && value.dataType().equals(ATTRIBUTE_DATA_TYPE_STRING)) {
                consumer.accept(value.stringValue(), state);
            }
        }
    }

    public void handleReceivedMessages(Span<?> span, @Nullable SdkRequest sdkRequest, @Nullable SdkResponse sdkResponse) {
        if (sdkResponse instanceof ReceiveMessageResponse && sdkRequest instanceof ReceiveMessageRequest) {
            SQSHelper.getInstance().handleReceivedMessages(span, ((ReceiveMessageRequest) sdkRequest).queueUrl(), ((ReceiveMessageResponse) sdkResponse).messages());
        }
    }
}
