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

import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.transaction.Span;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class RabbitMQChannelInstrumentationTest extends RabbitMQTest {

    @Test
    public void contextPropagationWithProperties() throws IOException, InterruptedException {
        testContextPropagation(emptyProperties());
    }

    @Test
    public void contextPropagationWithoutProperties() throws IOException, InterruptedException {
        testContextPropagation(null);
    }

    private void testContextPropagation(AMQP.BasicProperties properties) throws IOException, InterruptedException {

        Connection connection = createConnection();
        Channel channel = connection.createChannel();
        String exchange = createExchange(channel);
        String queue = createQueue(channel, exchange);

        CountDownLatch messageReceived = new CountDownLatch(1);

        channel.basicConsume(queue, new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                Map<String, Object> headers = properties.getHeaders();
                assertThat(headers).containsKey("elastic-apm-traceparent");
                assertThat(headers).containsKey("traceparent");
                messageReceived.countDown();
            }
        });

        getTracer().startRootTransaction(getClass().getClassLoader())
            .withName("Rabbit-Test Transaction")
            .withType("request")
            .withResult("success")
            .activate();

        channel.basicPublish(exchange, ROUTING_KEY, properties, MSG);

        getTracer().currentTransaction().deactivate().end();

        messageReceived.await(1, TimeUnit.SECONDS);

        // 2 transactions, 1 span expected
        getReporter().awaitTransactionCount(2);
        getReporter().awaitSpanCount(1);

        Span span = getReporter().getSpans().get(0);
        checkSpan(span, exchange);
    }

    protected static void checkSpan(Span span, String exchange) {
        assertThat(span.getType()).isEqualTo("messaging");
        assertThat(span.getSubtype()).isEqualTo("rabbitmq");
        assertThat(span.getAction()).isEqualTo("send");
        assertThat(span.getNameAsString()).isEqualTo("RabbitMQ message sent to " + exchange);

        assertThat(span.getContext().getMessage().getQueueName()).isEqualTo(exchange);
        Destination.Service service = span.getContext().getDestination().getService();
        assertThat(service.getType()).isEqualTo("messaging");
        assertThat(service.getName().toString()).isEqualTo("rabbitmq");
        assertThat(service.getResource().toString()).isEqualTo("rabbitmq/" + exchange);


    }

}
