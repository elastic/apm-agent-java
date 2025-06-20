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

import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.rabbitmq.header.SpringRabbitMQTextHeaderGetter;
import co.elastic.apm.agent.sdk.internal.util.PrivilegedActionUtils;
import co.elastic.apm.agent.tracer.configuration.MessagingConfiguration;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import javax.annotation.Nullable;

public class SpringAmqpTransactionHelper {

    private final Tracer tracer;

    public SpringAmqpTransactionHelper(Tracer tracer) {
        this.tracer = tracer;
    }

    @Nullable
    public Transaction<?> createTransaction(Message message, String transactionNamePrefix) {
        String exchangeOrQueue = null;
        MessageProperties messageProperties = message.getMessageProperties();
        if (messageProperties != null) {
            exchangeOrQueue = getExchangeOrQueue(messageProperties);
        }
        if (exchangeOrQueue != null && AbstractBaseInstrumentation.isIgnored(exchangeOrQueue)) {
            return null;
        }

        Transaction<?> transaction = tracer.currentTransaction();
        if (transaction != null) {
            return null;
        }
        transaction = tracer.startChildTransaction(messageProperties, SpringRabbitMQTextHeaderGetter.INSTANCE, PrivilegedActionUtils.getClassLoader(message.getClass()));
        if (transaction == null) {
            return null;
        }

        transaction.withType("messaging")
            .withName(transactionNamePrefix)
            .appendToName(" RECEIVE from ")
            .appendToName(AbstractBaseInstrumentation.normalizeExchangeName(exchangeOrQueue));

        transaction.setFrameworkName("Spring AMQP");

        if (messageProperties != null) {
            long timestamp = AbstractBaseInstrumentation.getTimestamp(messageProperties.getTimestamp());
            String receivedRoutingKey = messageProperties.getReceivedRoutingKey();
            transaction.getContext().getMessage().withAge(timestamp).withRoutingKey(receivedRoutingKey);
        }
        if (exchangeOrQueue != null) {
            transaction.getContext().getMessage().withQueue(exchangeOrQueue);
        }

        // only capture incoming messages headers for now (consistent with other messaging plugins)
        AbstractBaseInstrumentation.captureHeaders(messageProperties != null ? messageProperties.getHeaders() : null, transaction.getContext().getMessage());
        return transaction.activate();
    }

    private static String getExchangeOrQueue(MessageProperties messageProperties) {
        if (MessagingConfiguration.RabbitMQNamingMode.QUEUE == AbstractBaseInstrumentation.getRabbitMQNamingMode()) {
            return messageProperties.getConsumerQueue();
        } else {
            return messageProperties.getReceivedExchange();
        }
    }
}
