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
package co.elastic.apm.agent.rabbitmq.config;

import co.elastic.apm.agent.rabbitmq.TestConstants;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DirectMessageListenerContainerConfiguration extends MessageListenerConfiguration {

    @Bean
    DirectMessageListenerContainer container(ConnectionFactory connectionFactory) {
        DirectMessageListenerContainer directMessageListenerContainer = new DirectMessageListenerContainer(connectionFactory);
        directMessageListenerContainer.setQueueNames(TestConstants.QUEUE_NAME);
        directMessageListenerContainer.setMessageListener(messageListener());
        return directMessageListenerContainer;
    }
}
