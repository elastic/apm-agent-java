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
package co.elastic.apm.agent.rabbitmq;

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

public class RabbitMQConsumerInstrumentationTest extends RabbitMQTest {

    @Test
    public void createRootTransactionOnConsumerHandle() throws IOException {

        Connection connection = createConnection();
        Channel channel = connection.createChannel();
        String exchange = createExchange(channel);
        String queue = createQueue(channel, exchange);

        Consumer consumer = dummyConsumer(channel);

        channel.basicConsume(queue, consumer);

        AMQP.BasicProperties properties = emptyProperties();
        channel.basicPublish(exchange, ROUTING_KEY, properties, MSG);

        getReporter().awaitTransactionCount(1);

        Transaction transaction = getReporter().getFirstTransaction();
        checkTransaction(transaction);
    }

    @Test
    public void testHandleDeliveryWithTraceHeaders() throws IOException {
        Connection connection = createConnection();
        Channel channel = connection.createChannel();
        String exchange = createExchange(channel);
        String queue = createQueue(channel, exchange);

        // publish within first transaction

        getTracer().startRootTransaction(getClass().getClassLoader())
            .withName("Rabbit-Test Transaction")
            .withType("request")
            .withResult("success")
            .activate();

        // publish will create a span
        channel.basicPublish(exchange, ROUTING_KEY, emptyProperties(), MSG);

        getTracer().currentTransaction().deactivate().end();

        getReporter().awaitTransactionCount(1);

        // consume outside of transaction: should create another transaction with parent

        channel.basicConsume(queue, dummyConsumer(channel));

        getReporter().awaitTransactionCount(2);

        Transaction parentTransaction = getReporter().getTransactions().get(0);
        Transaction childTransaction = getReporter().getTransactions().get(1);

        checkTransaction(childTransaction);

        Span publishSpan = getReporter().getFirstSpan();
        checkParentChild(parentTransaction, publishSpan);

        checkParentChild(publishSpan, childTransaction);

    }

    private Consumer dummyConsumer(Channel channel){
        return new DefaultConsumer(channel) {
            // using an anonymous class to ensure class matching is properly applied
        };
    }

    protected AMQP.BasicProperties emptyProperties() {
        return new AMQP.BasicProperties.Builder().headers(new HashMap<>()).build();
    }

    protected void checkParentChild(AbstractSpan<?> parent, AbstractSpan<?> child) {
        assertThat(parent.getTraceContext().getParentId())
            .describedAs("child (%s) should be a child of (%s)", child, parent)
            .isEqualTo(parent.getTraceContext().getId());
    }

    protected void checkTransaction(Transaction transaction) {
        assertThat(transaction.getType()).isEqualTo("messaging");
        assertThat(transaction.getNameAsString()).isEqualTo("RabbitMQ message received");
    }

}
