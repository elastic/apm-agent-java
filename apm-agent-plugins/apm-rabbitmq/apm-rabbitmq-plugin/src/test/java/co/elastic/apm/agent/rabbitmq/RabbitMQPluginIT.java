package co.elastic.apm.agent.rabbitmq;
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

import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.awaitility.core.ConditionFactory;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public abstract class RabbitMQPluginIT extends RabbitMQTest {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQPluginIT.class);

    @Test
    public void testRabbitPlugin() throws IOException {
        Connection connection = createConnection();
        Channel channel = connection.createChannel();
        String exchange = createExchange(channel);
        String queue = createQueue(channel, exchange);

        MyConsumer consumer = new MyConsumer(channel);

        channel.basicConsume(queue, false, "testTag", consumer);

        Transaction transaction = getTracer().startRootTransaction(getClass().getClassLoader())
            .withName("RabbitIT Transaction")
            .withType("request")
            .withResult("success")
            .activate();

        AMQP.BasicProperties basicProperties = new AMQP.BasicProperties();
        channel.basicPublish(exchange, ROUTING_KEY, basicProperties, MSG);

        transaction.deactivate().end();

        getReporter().awaitTransactionCount(2);
        getReporter().awaitSpanCount(1);

        Span span = getReporter().getFirstSpan();
        Transaction transaction1 = getReporter().getTransactions().get(0);
        checkTransaction(transaction1);
        Transaction transaction2 = getReporter().getTransactions().get(1);
        checkTransaction(transaction2);

        assertThat(consumer.getMessages()).contains(new String(MSG));

        // check context propagation
        checkParentChild(transaction1, span);
        checkParentChild(span, transaction2);
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
            String msg = new String(body);
            messages.add(msg);
            log.info("handle message : {}", msg);
            long deliveryTag = envelope.getDeliveryTag();
            getChannel().basicAck(deliveryTag, false);
        }

        public List<String> getMessages() {
            return messages;
        }
    }



}
