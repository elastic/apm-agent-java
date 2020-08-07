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

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.github.fridujo.rabbitmq.mock.MockConnection;
import com.github.fridujo.rabbitmq.mock.MockConnectionFactory;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;

public class RabbitMQConsumerInstrumentationTest extends AbstractInstrumentationTest {

    @Test
    public void testHandleDelivery() throws IOException {
        MockConnectionFactory mockConnectionFactory = new MockConnectionFactory();

        MockConnection connection = mockConnectionFactory.newConnection();

        Channel channel = connection.createChannel();

        channel.exchangeDeclare("test-exchange", "direct", true);

        String queueName = channel.queueDeclare().getQueue();

        channel.queueBind(queueName, "test-exchange", "test.key");

        List<String> messages = new ArrayList<>();

        Consumer consumer = new DefaultConsumer(channel) {

            @Override
            public void handleDelivery(String consumerTag,
                                       Envelope envelope,
                                       AMQP.BasicProperties properties,
                                       byte[] body) throws IOException {
                long deliveryTag = envelope.getDeliveryTag();
                messages.add(new String(body));
                channel.basicAck(deliveryTag, false);
            }

        };

        channel.basicConsume(queueName, false, "testTag", consumer);

        byte[] messageBodyBytes = "Testing APM!".getBytes();

        AMQP.BasicProperties basicProperties = new AMQP.BasicProperties();
        channel.basicPublish("test-exchange", "test.key", basicProperties, messageBodyBytes);

        await().atMost(1000, MILLISECONDS).until(() -> !messages.isEmpty());

        assertEquals(Arrays.asList("Testing APM!"), messages);

        assertThat(getReporter().getTransactions()).hasSize(1);

        Transaction transaction = getReporter().getFirstTransaction();
        assertThat(transaction.getType()).isEqualTo("messaging");
        assertThat(transaction.getNameAsString()).isEqualTo("RabbitMQ message received");
    }

}
