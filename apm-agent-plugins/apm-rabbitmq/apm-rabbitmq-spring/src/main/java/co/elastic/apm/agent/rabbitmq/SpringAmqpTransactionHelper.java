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

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.rabbitmq.header.SpringRabbitMQTextHeaderGetter;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class SpringAmqpTransactionHelper {

    private final ElasticApmTracer tracer;

    public SpringAmqpTransactionHelper(ElasticApmTracer tracer) {
        this.tracer = tracer;
    }

    @Nullable
    public Transaction createTransaction(@Nonnull Message message, @Nullable MessageProperties messageProperties, @Nonnull String transactionNamePrefix) {
        String exchange = null;
        if (messageProperties != null) {
            exchange = messageProperties.getReceivedExchange();
        }
        if (exchange != null && AbstractBaseInstrumentation.isIgnored(exchange)) {
            return null;
        }

        Transaction transaction = tracer.currentTransaction();
        if (transaction != null) {
            return null;
        }
        transaction = tracer.startChildTransaction(messageProperties, SpringRabbitMQTextHeaderGetter.INSTANCE, message.getClass().getClassLoader());
        if (transaction == null) {
            return null;
        }

        transaction.withType("messaging")
            .withName(transactionNamePrefix)
            .appendToName(" RECEIVE from ")
            .appendToName(AbstractBaseInstrumentation.normalizeExchangeName(exchange));

        transaction.setFrameworkName("Spring AMQP");

        if (messageProperties != null) {
            long timestamp = AbstractBaseInstrumentation.getTimestamp(messageProperties.getTimestamp());
            String receivedRoutingKey = messageProperties.getReceivedRoutingKey();
            transaction.getContext().getMessage().withAge(timestamp).withRoutingKey(receivedRoutingKey);
        }
        if (exchange != null) {
            transaction.getContext().getMessage().withQueue(exchange);
        }

        // only capture incoming messages headers for now (consistent with other messaging plugins)
        AbstractBaseInstrumentation.captureHeaders(messageProperties != null ? messageProperties.getHeaders() : null, transaction.getContext().getMessage());
        return transaction.activate();
    }
}
