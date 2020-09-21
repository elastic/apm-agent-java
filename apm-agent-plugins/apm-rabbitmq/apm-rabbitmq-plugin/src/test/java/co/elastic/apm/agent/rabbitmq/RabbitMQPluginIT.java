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

import co.elastic.apm.agent.impl.transaction.Transaction;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.awaitility.core.ConditionFactory;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public abstract class RabbitMQPluginIT extends RabbitMQTest {

    @Test
    public void testRabbitPlugin() throws IOException {
        Connection connection = createConnection();

        Channel channel = connection.createChannel();

        channel.exchangeDeclare("test-exchange", "direct", true);

        String queueName = channel.queueDeclare().getQueue();

        channel.queueBind(queueName, "test-exchange", "test.key");

        MyConsumer consumer = new MyConsumer(channel);

        channel.basicConsume(queueName, false, "testTag", consumer);

        Transaction transaction = getTracer().startRootTransaction(getClass().getClassLoader())
            .withName("RabbitIT Transaction")
            .withType("request")
            .withResult("success")
            .activate();

        AMQP.BasicProperties basicProperties = new AMQP.BasicProperties();
        channel.basicPublish("test-exchange", "test.key", basicProperties, "Testing APM!".getBytes());

        transaction.deactivate().end();

        doAwait().until(() -> !consumer.getMessages().isEmpty());
        assertThat(consumer.getMessages()).contains("Testing APM!");

        assertThat(getReporter().getTransactions()).hasSize(2);
        assertThat(getReporter().getSpans()).hasSize(1);
    }

    protected ConditionFactory doAwait() {
        return await().atMost(1000, MILLISECONDS);
    }

    private static class MyConsumer extends DefaultConsumer {

        private final List<String> messages;

        public MyConsumer(Channel channel) {
            super(channel);
            this.messages = new ArrayList<>();
        }

        @Override
        public void handleDelivery(String consumerTag,
                                   Envelope envelope,
                                   AMQP.BasicProperties properties,
                                   byte[] body) throws IOException {
            long deliveryTag = envelope.getDeliveryTag();
            messages.add(new String(body));
            getChannel().basicAck(deliveryTag, false);
        }

        public List<String> getMessages() {
            return messages;
        }
    }



}
