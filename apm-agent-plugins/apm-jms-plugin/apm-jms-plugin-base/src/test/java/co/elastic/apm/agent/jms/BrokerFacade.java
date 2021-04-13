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

import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.util.concurrent.CompletableFuture;

interface BrokerFacade {

    void prepareResources() throws Exception;

    void closeResources() throws Exception;

    void beforeTest() throws Exception;

    void afterTest() throws Exception;

    Queue createQueue(String queueName) throws Exception;

    TemporaryQueue createTempQueue() throws Exception;

    Topic createTopic(String topicName) throws Exception;

    TemporaryTopic createTempTopic() throws Exception;

    TextMessage createTextMessage(String messageText) throws Exception;

    /**
     * Send the given message to the given destination.
     *
     * @param destination      destination to send the message to
     * @param message          message to be sent
     * @param disableTimestamp indicates whether the underlying {@link javax.jms.MessageProducer} should disable message timestamp
     * @throws Exception an internal client error
     */
    void send(Destination destination, Message message, boolean disableTimestamp) throws Exception;

    CompletableFuture<Message> registerConcreteListenerImplementation(Destination destination);

    CompletableFuture<Message> registerListenerLambda(Destination destination);

    CompletableFuture<Message> registerListenerMethodReference(Destination destination);

    Message receive(Destination destination) throws Exception;

    Message receive(Destination destination, long timeout) throws Exception;

    boolean shouldTestReceiveNoWait();

    Message receiveNoWait(Destination destination) throws Exception;
}
