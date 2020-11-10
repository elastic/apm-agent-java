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

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.MessagingConfiguration;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.context.Headers;
import co.elastic.apm.agent.impl.context.Message;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests the whole RabbitMQ instrumentation as a whole, both for transactions and spans
 */
public class RabbitMQTest extends AbstractInstrumentationTest {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMQTest.class);

    private static final String IMAGE = "rabbitmq:3.7-management-alpine";
    private static final RabbitMQContainer container = new RabbitMQContainer(IMAGE);

    private static final String ROUTING_KEY = "test.key";

    private static final byte[] MSG = "Testing APM!".getBytes();

    @Nullable
    private static ConnectionFactory factory;

    @Nullable
    private Connection connection;

    @BeforeAll
    static void before() {
        container.withLogConsumer(new Slf4jLogConsumer(logger))
            .start();

        factory = new ConnectionFactory();

        factory.setHost(container.getHost());
        factory.setPort(container.getAmqpPort());
        factory.setUsername(container.getAdminUsername());
        factory.setPassword(container.getAdminPassword());
    }

    @AfterEach
    void cleanup() throws IOException {
        if (connection != null) {
            if (connection.isOpen()) {
                logger.info("silently closing open connection id = {}", connection);
                connection.close();
            }
        }
    }

    @AfterAll
    static void after() {
        container.close();
    }


    @Test
    void contextPropagationWithProperties() throws IOException, InterruptedException {
        performTest(null);
    }

    @Test
    void contextPropagationWithoutProperties() throws IOException, InterruptedException {
        performTest(emptyProperties());
    }

    @Test
    void bodyCaptureNotSupported() throws IOException, InterruptedException {
        // body capture is not supported because at the RabbitMQ driver level
        // the message is provided as a byte array.
        CoreConfiguration config = AbstractInstrumentationTest.config.getConfig(CoreConfiguration.class);
        when(config.getCaptureBody()).thenReturn(CoreConfiguration.EventType.ALL);

        performTest(
            emptyProperties(),
            false,
            randString("exchange"),
            message -> {
                checkMessageBodyNotCaptured(message);
                checkMessageHeaders(message, Collections.emptyMap(), true);
            });
    }

    @Test
    void headersCaptureEnabledByDefault() throws IOException, InterruptedException {
        Map<String, String> headers = Map.of("message-header", "header value");
        testHeadersCapture(headers, headers, true);
    }

    @Test
    void headersCaptureDisabled() throws IOException, InterruptedException {
        CoreConfiguration coreConfiguration = config.getConfig(CoreConfiguration.class);
        when(coreConfiguration.isCaptureHeaders()).thenReturn(false);

        testHeadersCapture(Map.of("message-header", "header value"), Map.of(), false);
    }

    @Test
    void headersCaptureSanitize() throws IOException, InterruptedException {
        CoreConfiguration coreConfiguration = config.getConfig(CoreConfiguration.class);
        when(coreConfiguration.getSanitizeFieldNames()).thenReturn(List.of(WildcardMatcher.valueOf("secret*")));

        testHeadersCapture(
            Map.of(
                "other-header", "other-value",
                "secret-token", "secret-value"),
            Map.of(
                "other-header", "other-value"
            ), true);
    }

    private void testHeadersCapture(Map<String, String> headersMap, Map<String, String> expectedHeaders, boolean expectTracingHeaders) throws IOException, InterruptedException {
        performTest(
            propertiesMap(headersMap),
            false,
            randString("exchange"),
            message -> {
                checkMessageBodyNotCaptured(message);
                checkMessageHeaders(message, expectedHeaders, expectTracingHeaders);
            });
    }

    @Test
    void ignoreExchangeName() throws IOException, InterruptedException {
        MessagingConfiguration messagingConfiguration = config.getConfig(MessagingConfiguration.class);
        when(messagingConfiguration.getIgnoreMessageQueues()).thenReturn(List.of(WildcardMatcher.valueOf("ignored-*")));

        performTest(emptyProperties(), true, randString("ignored"), message -> {
        });
    }

    private void performTest(@Nullable AMQP.BasicProperties properties) throws IOException, InterruptedException {
        performTest(properties, false, randString("exchange"), message -> {
        });
    }

    private void performTest(@Nullable AMQP.BasicProperties properties,
                             boolean shouldIgnore,
                             String channelName,
                             Consumer<Message> messageCheck) throws IOException, InterruptedException {

        Connection connection = createConnection();
        Channel channel = connection.createChannel();
        String exchange = createExchange(channel, channelName);
        String queue = createQueue(channel, exchange);

        CountDownLatch messageReceived = new CountDownLatch(1);

        channel.basicConsume(queue, new DefaultConsumer(channel) {
            // using an anonymous class to ensure class matching is properly applied

            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                assertThat(properties).isNotNull();
                Map<String, Object> headers = properties.getHeaders();

                if (shouldIgnore) {
                    assertThat(headers).doesNotContainKeys(DISTRIBUTED_TRACING_HEADERS);
                } else {
                    assertThat(headers).containsKeys(DISTRIBUTED_TRACING_HEADERS);
                }

                messageReceived.countDown();
            }
        });

        Transaction rootTransaction = getTracer().startRootTransaction(getClass().getClassLoader())
            .withName("Rabbit-Test Root Transaction")
            .withType("request")
            .withResult("success")
            .activate();


        channel.basicPublish(exchange, ROUTING_KEY, properties, MSG);

        rootTransaction.deactivate().end();

        messageReceived.await(1, TimeUnit.SECONDS);

        if (shouldIgnore) {
            getReporter().awaitTransactionCount(1);
            assertThat(getReporter().getFirstTransaction())
                .describedAs("only the test root transaction is expected")
                .isSameAs(rootTransaction);

            getReporter()
                .awaitUntilAsserted(1_000, () -> assertThat(getReporter().getNumReportedTransactions())
                    .describedAs("no other transaction should be reported")
                    .isEqualTo(1));

            getReporter().assertNoSpan(1_000);
            return;
        }


        // 2 transactions, 1 span expected
        getReporter().awaitTransactionCount(2);
        getReporter().awaitSpanCount(1);

        Transaction childTransaction = null;
        for (Transaction t : getReporter().getTransactions()) {
            if (t != rootTransaction) {
                assertThat(childTransaction).isNull();
                childTransaction = t;
            }
        }
        assertThat(childTransaction).isNotNull();
        checkTransaction(childTransaction, exchange);

        Span span = getReporter().getSpans().get(0);
        checkSpan(span, exchange);

        // span should be child of the first transaction
        checkParentChild(rootTransaction, span);
        // second transaction should be the child of span
        checkParentChild(span, childTransaction);

        // perform extra test-specific message check
        messageCheck.accept(span.getContext().getMessage());
        messageCheck.accept(childTransaction.getContext().getMessage());

    }

    protected Connection createConnection() {
        if (connection != null && connection.isOpen()) {
            throw new IllegalStateException("unclosed connection");
        }
        try {
            connection = factory.newConnection();
            logger.info("created connection id = {}", connection);
            return connection;
        } catch (IOException | TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }

    private String createQueue(Channel channel, String exchange) throws IOException {
        String queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, exchange, ROUTING_KEY);
        return queueName;
    }

    private String createExchange(Channel channel, String exchangeName) throws IOException {
        channel.exchangeDeclare(exchangeName, "direct", false);
        return exchangeName;
    }

    private static String randString(String prefix) {
        return String.format("%s-%08x", prefix, System.currentTimeMillis());
    }

    private AMQP.BasicProperties propertiesMap(Map<String, String> map) {
        // doing a dumb copy to convert Map<String,String> to Map<String,Object>
        Map<String, Object> objectMap = map.entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return new AMQP.BasicProperties.Builder()
            .headers(objectMap)
            .build();
    }

    private AMQP.BasicProperties emptyProperties() {
        return new AMQP.BasicProperties.Builder().headers(new HashMap<>()).build();
    }

    private static void checkParentChild(AbstractSpan<?> parent, AbstractSpan<?> child) {
        assertThat(child.getTraceContext().getParentId())
            .describedAs("child (%s) should be a child of (%s)", child, parent)
            .isEqualTo(parent.getTraceContext().getId());

        assertThat(child.getTraceContext().getTraceId())
            .describedAs("child (%s) should have same trace ID as parent (%s)", child, parent)
            .isEqualTo(parent.getTraceContext().getTraceId());
    }

    private static void checkTransaction(Transaction transaction, String exchange) {
        assertThat(transaction.getType()).isEqualTo("messaging");
        assertThat(transaction.getNameAsString())
            .isEqualTo("Consumer#handleDelivery from %s", exchange);
        assertThat(transaction.getFrameworkName()).isEqualTo("RabbitMQ");

        checkMessage(transaction.getContext().getMessage(), exchange);
    }

    private static void checkMessage(Message message, String exchange) {
        assertThat(message.getQueueName()).isEqualTo(exchange);

        // RabbitMQ does not provide timestamp by default
        assertThat(message.getAge()).isLessThan(0);
    }


    private static void checkMessageBodyNotCaptured(Message message) {
        assertThat(message.getBodyForRead()).isNull();
    }

    private static String[] DISTRIBUTED_TRACING_HEADERS = {
        "elastic-apm-traceparent",
        "tracestate",
        "traceparent"
    };

    private static void checkMessageHeaders(Message message, Map<String, String> expectedHeaders, boolean expectTracingHeaders) {
        Headers headers = message.getHeaders();

        Map<String, String> headersMap = new HashMap<>();
        headers.forEach(h -> headersMap.put(h.getKey(), h.getValue().toString()));

        if (!expectedHeaders.isEmpty()) {
            assertThat(headersMap).containsAllEntriesOf(expectedHeaders);
        }

        if (expectTracingHeaders) {
            assertThat(headersMap)
                .describedAs("distributed tracing headers should be captured")
                .containsKeys(DISTRIBUTED_TRACING_HEADERS);
        } else {
            assertThat(headersMap)
                .describedAs("distributed tracing headers aren't expected")
                .doesNotContainKeys(DISTRIBUTED_TRACING_HEADERS);
        }
    }

    private static void checkSpan(Span span, String exchange) {
        assertThat(span.getType()).isEqualTo("messaging");
        assertThat(span.getSubtype()).isEqualTo("rabbitmq");
        assertThat(span.getAction()).isEqualTo("send");

        assertThat(span.getNameAsString())
            .isEqualTo("Channel#basicPublish to %s", exchange);

        checkMessage(span.getContext().getMessage(), exchange);

        Destination destination = span.getContext().getDestination();

        assertThat(destination.getPort()).isEqualTo(container.getAmqpPort());
        assertThat(destination.getAddress().toString()).isEqualTo(container.getHost());

        Destination.Service service = destination.getService();

        assertThat(service.getType()).isEqualTo("messaging");
        assertThat(service.getName().toString()).isEqualTo("rabbitmq");
        assertThat(service.getResource().toString()).isEqualTo("rabbitmq/%s", exchange);

    }
}
