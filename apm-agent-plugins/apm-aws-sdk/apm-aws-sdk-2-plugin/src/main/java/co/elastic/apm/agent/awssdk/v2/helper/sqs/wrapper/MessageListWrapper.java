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
package co.elastic.apm.agent.awssdk.v2.helper.sqs.wrapper;

import co.elastic.apm.agent.awssdk.common.AbstractMessageListWrapper;
import co.elastic.apm.agent.awssdk.common.IAwsSdkDataSource;
import co.elastic.apm.agent.awssdk.v2.helper.SQSHelper;
import co.elastic.apm.agent.awssdk.v2.helper.SdkV2DataSource;
import co.elastic.apm.agent.configuration.MessagingConfiguration;
import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.sdk.state.CallDepth;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import co.elastic.apm.agent.tracer.Tracer;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;

public class MessageListWrapper extends AbstractMessageListWrapper<Message> {

    public static final CallDepth jmsReceiveMessageCallDepth = CallDepth.get(MessageListWrapper.class);
    private static final WeakMap<ReceiveMessageResponse, List<Message>> sqsResponseToWrappedMessageListMap = WeakConcurrent.buildMap();

    public static void registerWrapperListForResponse(@Nullable SdkRequest sdkRequest, @Nullable SdkResponse sdkResponse, Tracer tracer) {
        if (sdkResponse instanceof ReceiveMessageResponse && sdkRequest instanceof ReceiveMessageRequest) {
            String queueName = SdkV2DataSource.getInstance().getFieldValue(IAwsSdkDataSource.QUEUE_NAME_FIELD, sdkRequest);
            ReceiveMessageResponse receiveMessageResponse = (ReceiveMessageResponse) sdkResponse;
            // Wrap result only if the messages are NOT received as part of JMS.
            if (!jmsReceiveMessageCallDepth.isNestedCallAndIncrement()) {
                if (tracer.isRunning() && queueName != null
                    && !WildcardMatcher.isAnyMatch(tracer.getConfig(MessagingConfiguration.class).getIgnoreMessageQueues(), queueName)) {
                    sqsResponseToWrappedMessageListMap.put(receiveMessageResponse, new MessageListWrapper(receiveMessageResponse.messages(), tracer, queueName));
                }
            }
            jmsReceiveMessageCallDepth.decrement();
        }
    }

    @Nullable
    public static List<Message> getMessagesList(ReceiveMessageResponse response) {
        if (!sqsResponseToWrappedMessageListMap.containsKey(response)) {
            return null;
        }
        return sqsResponseToWrappedMessageListMap.get(response);
    }

    public MessageListWrapper(List<Message> delegate, Tracer tracer, String queueName) {
        super(delegate, tracer, queueName, SQSHelper.getInstance(), SQSHelper.getInstance());
    }

    @Override
    public Iterator<Message> iterator() {
        return new MessageIteratorWrapper(delegate.iterator(), tracer, queueName);
    }

    @Override
    public List<Message> subList(int fromIndex, int toIndex) {
        return new MessageListWrapper(delegate.subList(fromIndex, toIndex), tracer, queueName);
    }
}
