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
package co.elastic.apm.agent.rabbitmq;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;

import javax.annotation.Nullable;

public class MessageListenerHelper {

    @Nullable
    public MessageListener registerAndWrapLambda(@Nullable MessageListener listener) {
        if (listener != null) {
            if (listener.getClass().getName().contains("/")) {
                if (listener instanceof ChannelAwareMessageListener) {
                    listener = new ChannelAwareMessageListenerWrapper((ChannelAwareMessageListener) listener);
                } else {
                    listener = new MessageListenerWrapper(listener);
                }
            }
            SpringAmqpTransactionNameUtil.register(listener);
        }
        return listener;
    }

    public static class MessageListenerWrapper implements MessageListener {

        private final MessageListener delegate;

        public MessageListenerWrapper(MessageListener delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onMessage(Message message) {
            delegate.onMessage(message);
        }
    }

    public static class ChannelAwareMessageListenerWrapper implements ChannelAwareMessageListener {

        private final ChannelAwareMessageListener delegate;

        public ChannelAwareMessageListenerWrapper(ChannelAwareMessageListener delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onMessage(Message message, Channel channel) throws Exception {
            delegate.onMessage(message, channel);
        }

        @Override
        public void onMessage(Message message) {
            delegate.onMessage(message);
        }
    }
}
