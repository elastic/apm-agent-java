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
import co.elastic.apm.agent.impl.transaction.Id;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.testutils.TestContainersUtils;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import org.testcontainers.containers.RabbitMQContainer;

import javax.annotation.Nullable;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests the whole RabbitMQ instrumentation as a whole, both for transactions and spans
 */
public class RabbitMQIT extends AbstractInstrumentationTest {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMQIT.class);

    private static final String IMAGE = "rabbitmq:3.7-management-alpine";
    private static final RabbitMQContainer container = new RabbitMQContainer(IMAGE);

    private static final String ROUTING_KEY = "test.key";

    private static final byte[] MSG = "Testing APM!".getBytes();

    private static ConnectionFactory factory;

    private static Connection connection;

    @BeforeAll
    static void before() {
        container.withLogConsumer(TestContainersUtils.createSlf4jLogConsumer(RabbitMQIT.class))
            .withStartupTimeout(Duration.ofSeconds(120))
            .withCreateContainerCmdModifier(TestContainersUtils.withMemoryLimit(2048))
            .start();

        factory = new ConnectionFactory();

        factory.setHost(container.getHost());
        factory.setPort(container.getAmqpPort());
        factory.setUsername(container.getAdminUsername());
        factory.setPassword(container.getAdminPassword());

        try {
            connection = factory.newConnection();
            Objects.requireNonNull(connection);
            logger.info("created connection id = {}", connection);
        } catch (IOException | TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }


    @AfterAll
    static void after() throws IOException {
        container.close();

        if (connection.isOpen()) {
            logger.info("silently closing open connection id = {}", connection);
            connection.close();
        }
    }


    @Test
    void contextPropagationWithoutProperties() throws IOException, InterruptedException {
        performTest(null);
    }

    @Test
    void contextPropagationWithProperties() throws IOException, InterruptedException {
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
            (mt, ms) -> {

                checkMessageBodyNotCaptured(mt);
                checkMessageBodyNotCaptured(ms);
            });
    }

    @Test
    void headersCaptureEnabledByDefault() throws IOException, InterruptedException {
        Map<String, String> headers = Map.of("message-header", "header value");
        Map<String, String> headersWithNullValue = new HashMap<>(headers);
        headersWithNullValue.put("null-header", null);
        testHeadersCapture(headersWithNullValue,
            Map.of(
                "message-header", "header value",
                "null-header", "null"),
            true);
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
            (mt, ms) -> {
                assertThat(ms.getHeaders())
                    .describedAs("spans should not capture outgoing message headers")
                    .isEmpty();

                // only transaction should have headers
                checkHeaders(mt, expectedHeaders);
                checkDistributedTracingHeaders(mt, expectTracingHeaders);
            });
    }

    @Test
    void ignoreExchangeName() throws IOException, InterruptedException {
        MessagingConfiguration messagingConfiguration = config.getConfig(MessagingConfiguration.class);
        when(messagingConfiguration.getIgnoreMessageQueues()).thenReturn(List.of(WildcardMatcher.valueOf("ignored-*")));

        performTest(emptyProperties(), true, randString("ignored"), (mt, ms) -> {
        });
    }

    private void performTest(@Nullable AMQP.BasicProperties properties) throws IOException, InterruptedException {
        performTest(properties, false, randString("exchange"), (mt, ms) -> {
        });
    }

    private void performTest(@Nullable AMQP.BasicProperties properties,
                             boolean shouldIgnore,
                             String channelName,
                             BiConsumer<Message, Message> messageCheck) throws IOException, InterruptedException {

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

        Transaction rootTransaction = startTestRootTransaction("Rabbit-Test Root Transaction");

        channel.basicPublish(exchange, ROUTING_KEY, properties, MSG);

        endRootTransaction(rootTransaction);

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

        Transaction childTransaction = getNonRootTransaction(rootTransaction, getReporter().getTransactions());

        checkTransaction(childTransaction, exchange);

        Span span = getReporter().getSpans().get(0);
        checkSendSpan(span, exchange);

        // span should be child of the first transaction
        checkParentChild(rootTransaction, span);
        // second transaction should be the child of span
        checkParentChild(span, childTransaction);

        // common assertions on span & transaction message
        Message spanMessage = span.getContext().getMessage();
        Message transactionMessage = childTransaction.getContext().getMessage();


        // test-specific assertions on captured message
        messageCheck.accept(transactionMessage, spanMessage);

    }

    @Test
    void testPollingWithinTransactionNoMessage() throws IOException {
        Channel channel = connection.createChannel();
        String exchange = createExchange(channel, "exchange");

        String queueName = randString("queue");

        pollingTest(true, false, () -> declareAndBindQueue(queueName, exchange, channel), exchange);

        reporter.awaitTransactionCount(1);
        reporter.awaitSpanCount(1);

        Span pollingSpan = reporter.getFirstSpan();
        checkPollSpan(pollingSpan, queueName, "<unknown>", false);
    }

    @Test
    void testPollingWithinTransactionGetMessage() throws IOException {
        Channel channel = connection.createChannel();
        String exchange = createExchange(channel, "exchange");

        String queueName = randString("queue");

        pollingTest(true, true, () -> declareAndBindQueue(queueName, exchange, channel), exchange);

        reporter.awaitTransactionCount(1);
        reporter.awaitSpanCount(1);

        Span pollingSpan = reporter.getFirstSpan();
        checkPollSpan(pollingSpan, queueName, exchange, true);
    }


    @Test
    void testPollingOutsideTransaction() throws IOException {
        Channel channel = connection.createChannel();
        String exchange = createExchange(channel, "exchange");

        pollingTest(false, false, () -> declareAndBindQueue("queue", exchange, channel), exchange);

        reporter.assertNoTransaction(100);
        reporter.assertNoSpan(100);
    }

    @Test
    void testPollingIgnoreQueueName() throws IOException {
        Channel channel = connection.createChannel();
        String exchange = createExchange(channel, "exchange");

        MessagingConfiguration messagingConfiguration = config.getConfig(MessagingConfiguration.class);
        when(messagingConfiguration.getIgnoreMessageQueues()).thenReturn(List.of(WildcardMatcher.valueOf("ignored-qu*")));

        pollingTest(true, false, () -> declareAndBindQueue("ignored-queue", exchange, channel), exchange);

        reporter.awaitTransactionCount(1);
        reporter.assertNoSpan(100);
    }

    @Test
    void testPollingIgnoreExchangeName() throws IOException {
        Channel channel = connection.createChannel();
        String exchange = createExchange(channel, "ignored-exchange");

        MessagingConfiguration messagingConfiguration = config.getConfig(MessagingConfiguration.class);
        when(messagingConfiguration.getIgnoreMessageQueues()).thenReturn(List.of(WildcardMatcher.valueOf("ignored-ex*")));

        pollingTest(true, true, () -> declareAndBindQueue("queue", exchange, channel), exchange);

        reporter.awaitTransactionCount(1);
        reporter.assertNoSpan(100);
    }

    private String declareAndBindQueue(String queue, String exchange, Channel channel) {
        try {
            channel.queueDeclare(queue, false, false, false, null);
            channel.queueBind(queue, exchange, ROUTING_KEY);
            return queue;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void pollingTest(boolean withinTransaction, boolean withResult, Supplier<String> createQueue, String exchange) throws IOException {
        Channel channel = connection.createChannel();

        String queue = createQueue.get();

        if (withResult) {
            channel.basicPublish(exchange, ROUTING_KEY, emptyProperties(), MSG);
        }

        Transaction rootTransaction = null;
        if (withinTransaction) {
            rootTransaction = startTestRootTransaction("Rabbit-Test Root Transaction");
        }

        channel.basicGet(queue, true);

        if (withinTransaction) {
            endRootTransaction(rootTransaction);
        }
    }

    @Test
    void testRpcCall() throws IOException, InterruptedException {
        // with an RPC call, the message consumer might be executed within the caller thread
        // as a result, if there is an active transaction we should create a span for the message processing

        Channel channel = connection.createChannel();

        // using an empty name for exchange allows to use the default exchange
        // which has the property to send message to any queue by name using routing key
        final String exchange = "";

        channel.basicQos(1);

        String rpcQueueName = randString("rpc_queue");
        channel.queueDeclare(rpcQueueName, false, false, false, null);
        // because we use a random queue, we don't have to purge it
        // if it was persistent, any previous message should be discarded with a call to 'queuePurge'

        // RPC server implementation
        String serverConsumerTag = channel.basicConsume(rpcQueueName, false, new DefaultConsumer(channel) {

            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {

                AMQP.BasicProperties replyProperties = new AMQP.BasicProperties
                    .Builder()
                    .correlationId(properties.getCorrelationId())
                    .build();

                String reply = "reply from RPC server: " + new String(body);
                channel.basicPublish(exchange, properties.getReplyTo(), replyProperties, reply.getBytes());
                channel.basicAck(envelope.getDeliveryTag(), false);
            }
        });

        final String correlationId = UUID.randomUUID().toString();

        String replyQueueName = channel.queueDeclare().getQueue();
        AMQP.BasicProperties properties = new AMQP.BasicProperties
            .Builder()
            .correlationId(correlationId)
            .replyTo(replyQueueName)
            .build();


        Transaction rootTransaction = startTestRootTransaction("Rabbit-Test Root Transaction");

        channel.basicPublish(exchange, rpcQueueName, properties, MSG);

        ArrayBlockingQueue<String> rpcResult = new ArrayBlockingQueue<>(1);

        // here we could have used the DeliverCallback functional interface added in rabbitmq 5.x driver
        // however, internally it only delegates to a regular Consumer
        String clientConsumerTag = channel.basicConsume(replyQueueName, true, new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                if (correlationId.equals(properties.getCorrelationId())) {
                    rpcResult.offer(new String(body));
                }
            }
        });

        assertThat(rpcResult.take()).isEqualTo("reply from RPC server: Testing APM!");

        endRootTransaction(rootTransaction);

        // we need to cancel consumers after usage
        channel.basicCancel(clientConsumerTag);
        channel.basicCancel(serverConsumerTag);


        // we should have captured the following:
        // 3 transactions:
        // - root transaction
        // - transaction for the server-side of the RPC call (processing request)
        // - transaction for the client-side of the RPC call (processing response)
        // 2 spans:
        // - span for sending the RPC request message in the root transaction
        // - span for sending the RPC response message in server-side processing

        getReporter().awaitTransactionCount(3);
        getReporter().awaitSpanCount(2);

        // start with the spans as we can identify them using the root transaction
        // parent/child relationships are used to find who is who as we don't have other fields like name
        // to distinguish them

        Span clientRequestRpc = null;
        Span serverReplyRpc = null;
        for (Span s : getReporter().getSpans()) {
            Id spanParentId = s.getTraceContext().getParentId();
            if (rootTransaction.getTraceContext().getId().equals(spanParentId)) {
                // client request is child of root transaction
                assertThat(clientRequestRpc).isNull();
                clientRequestRpc = s;
            } else {
                assertThat(serverReplyRpc).isNull();
                serverReplyRpc = s;
            }
        }
        assertThat(clientRequestRpc).isNotNull();
        assertThat(serverReplyRpc).isNotNull();

        Transaction serverSideRpc = null;
        Transaction clientSideRpc = null;
        for (Transaction t : getReporter().getTransactions()) {
            if (t != rootTransaction) {
                Id transactionParentId = t.getTraceContext().getParentId();
                if (clientRequestRpc.getTraceContext().getId().equals(transactionParentId)) {
                    assertThat(serverSideRpc).isNull();
                    serverSideRpc = t;
                } else {
                    assertThat(clientSideRpc).isNull();
                    clientSideRpc = t;
                }
            }
        }
        assertThat(serverSideRpc).isNotNull();
        assertThat(clientSideRpc).isNotNull();

        checkSendSpan(clientRequestRpc, exchange);
        checkParentChild(rootTransaction, clientRequestRpc);

        checkTransaction(serverSideRpc, exchange);
        assertThat(serverSideRpc.getNameAsString()).isEqualTo("RabbitMQ RECEIVE from <default>");
        checkParentChild(clientRequestRpc, serverSideRpc);

        checkSendSpan(serverReplyRpc, exchange);
        checkParentChild(serverSideRpc, serverReplyRpc);

        checkTransaction(clientSideRpc, exchange);
        checkParentChild(serverReplyRpc, clientSideRpc);

    }

    private void endRootTransaction(Transaction rootTransaction) {
        rootTransaction.deactivate().end();
    }

    static Transaction getNonRootTransaction(Transaction rootTransaction, List<Transaction> transactions) {
        Transaction childTransaction = null;
        for (Transaction t : transactions) {
            if (t != rootTransaction) {
                assertThat(childTransaction).isNull();
                childTransaction = t;
            }
        }
        assertThat(childTransaction).isNotNull();
        return childTransaction;
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
        Map<String, Object> objectMap = new HashMap<>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            objectMap.put(entry.getKey(), entry.getValue());
        }
        return new AMQP.BasicProperties.Builder()
            .headers(objectMap)
            .build();
    }

    private AMQP.BasicProperties emptyProperties() {
        return new AMQP.BasicProperties.Builder().headers(new HashMap<>()).build();
    }

    static void checkParentChild(AbstractSpan<?> parent, AbstractSpan<?> child) {
        assertThat(child.getTraceContext().getParentId())
            .describedAs("child (%s) should be a child of (%s)", child, parent)
            .isEqualTo(parent.getTraceContext().getId());

        assertThat(child.getTraceContext().getTraceId())
            .describedAs("child (%s) should have same trace ID as parent (%s)", child, parent)
            .isEqualTo(parent.getTraceContext().getTraceId());
    }

    private static void checkTransaction(Transaction transaction, String exchange) {
        checkTransaction(transaction, exchange, "RabbitMQ");
    }

    static void checkTransaction(Transaction transaction, String exchange, String frameworkName) {
        assertThat(transaction.getType()).isEqualTo("messaging");
        assertThat(transaction.getNameAsString())
            .isEqualTo("RabbitMQ RECEIVE from %s", exchange.isEmpty() ? "<default>" : exchange);
        assertThat(transaction.getFrameworkName()).isEqualTo(frameworkName);

        assertThat(transaction.getOutcome()).isEqualTo(Outcome.SUCCESS);

        checkMessage(transaction.getContext().getMessage(), exchange, true);
    }

    private static void checkMessage(Message message, String queueName, boolean withRoutingKeyCheck) {
        assertThat(message.getQueueName()).isEqualTo(queueName);

        // RabbitMQ does not provide timestamp by default
        assertThat(message.getAge()).isLessThan(0);
        if (withRoutingKeyCheck) {
            assertThat(message.getRoutingKey()).isNotBlank();
        } else {
            assertThat(message.getRoutingKey()).isNull();
        }
    }


    private static void checkMessageBodyNotCaptured(Message message) {
        assertThat(message.getBodyForRead()).describedAs("body capture isn't supported").isNull();
    }

    private static final String[] DISTRIBUTED_TRACING_HEADERS = {
        "elastic-apm-traceparent",
        "tracestate",
        "traceparent"
    };

    private static void checkDistributedTracingHeaders(Message message, boolean expectTracingHeaders) {
        HashMap<String, String> headersMap = getHeadersMap(message);
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

    private static void checkHeaders(Message message, Map<String, String> expectedHeaders) {
        HashMap<String, String> headersMap = getHeadersMap(message);
        for (String key : DISTRIBUTED_TRACING_HEADERS) {
            headersMap.remove(key);
        }
        assertThat(headersMap)
            .describedAs("should contain entries of %s", expectedHeaders)
            .containsAllEntriesOf(expectedHeaders);
    }

    private static HashMap<String, String> getHeadersMap(Message message) {
        Headers headers = message.getHeaders();
        HashMap<String, String> headersMap = new HashMap<>();
        headers.forEach(h -> headersMap.put(h.getKey(), h.getValue().toString()));
        return headersMap;
    }

    private static void checkSendSpan(Span span, String exchange) {
        checkSendSpan(span, exchange, connection.getAddress().getHostAddress(), connection.getPort());
    }

    static void checkSendSpan(Span span, String exchange, String host, int port) {
        String exchangeName = exchange.isEmpty() ? "<default>" : exchange;
        checkSpanCommon(span,
            "send",
            String.format("RabbitMQ SEND to %s", exchangeName),
            exchangeName,
            true
        );

        checkSpanDestination(span, host, port, String.format("rabbitmq/%s", exchangeName));
    }

    private static void checkPollSpan(Span span, String queue, String normalizedExchange, boolean withRoutingKeyCheck) {
        checkSpanCommon(span,
            "poll",
            String.format("RabbitMQ POLL from %s", queue),
            queue,
            withRoutingKeyCheck);

        checkSpanDestination(span,
            connection.getAddress().getHostAddress(),
            connection.getPort(),
            String.format("rabbitmq/%s", normalizedExchange)
        );
    }

    private static void checkSpanCommon(Span span, String expectedAction, String expectedName, String expectedQueueName, boolean withRoutingKeyCheck) {
        assertThat(span.getType()).isEqualTo("messaging");
        assertThat(span.getSubtype()).isEqualTo("rabbitmq");
        assertThat(span.getAction()).isEqualTo(expectedAction);

        assertThat(span.getNameAsString())
            .isEqualTo(expectedName);

        checkMessage(span.getContext().getMessage(), expectedQueueName, withRoutingKeyCheck);

        assertThat(span.getOutcome()).isEqualTo(Outcome.SUCCESS);
    }

    static void checkSpanDestination(Span span, String expectedHostAddress, int expectedPort, String expectedResource) {
        Destination destination = span.getContext().getDestination();
        assertThat(destination.getAddress().toString()).isEqualTo(expectedHostAddress);
        assertThat(destination.getPort()).isEqualTo(expectedPort);

        Destination.Service service = destination.getService();

        assertThat(service.getResource().toString()).isEqualTo(expectedResource);
    }
}
