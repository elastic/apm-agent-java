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
package co.elastic.apm.agent.kafka;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.MessagingConfiguration;
import co.elastic.apm.agent.impl.TracerInternalApiUtils;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.context.Headers;
import co.elastic.apm.agent.impl.context.Message;
import co.elastic.apm.agent.impl.context.SpanContext;
import co.elastic.apm.agent.impl.context.TransactionContext;
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.impl.sampling.Sampler;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.testutils.TestContainersUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;
import org.testcontainers.utility.DockerImageName;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doReturn;

/**
 * Each test sends a message to a request topic and waits on a reply message. This serves two purposes:
 * 1.  reduce waits to a minimum within tests
 * 2.  test both consumer instrumentation functionalities:
 * a.  the poll span creation (as part of the test, occurring within a traced transaction)- one per poll action
 * b.  the creation of consumer transaction- one per consumed record
 */
@SuppressWarnings("NotNullFieldNotInitialized")
@Ignore
public class KafkaIT extends AbstractInstrumentationTest {

    static final String REQUEST_TOPIC = "Request-Topic";
    static final String REPLY_TOPIC = "Reply-Topic";
    static final String REQUEST_KEY = "request-key";
    static final String REPLY_KEY = "response-key";
    public static final String FIRST_MESSAGE_VALUE = "First message body";
    public static final String SECOND_MESSAGE_VALUE = "Second message body";
    public static final String TEST_HEADER_KEY = "test_header";
    public static final String PASSWORD_HEADER_KEY = "password";
    public static final String TEST_HEADER_VALUE = "test_header_value";

    private static KafkaContainer kafka;
    private static int kafkaPort;
    private static String bootstrapServers;
    private static Consumer consumerThread;
    private static KafkaConsumer<String, String> replyConsumer;
    private static KafkaProducer<String, String> producer;

    private final CoreConfiguration coreConfiguration;
    private final MessagingConfiguration messagingConfiguration;

    private TestScenario testScenario;

    public KafkaIT() {
        this.coreConfiguration = config.getConfig(CoreConfiguration.class);
        this.messagingConfiguration = config.getConfig(MessagingConfiguration.class);
    }

    @BeforeClass
    public static void setup() {
        // confluent versions 7.1.0 correspond Kafka versions 3.1.0 -
        // https://docs.confluent.io/current/installation/versions-interoperability.html#cp-and-apache-ak-compatibility
        kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka").withTag("7.1.0"));
        kafka.withCreateContainerCmdModifier(TestContainersUtils.withMemoryLimit(4096));
        kafka.start();
        kafkaPort = kafka.getMappedPort(KafkaContainer.KAFKA_PORT);
        bootstrapServers = kafka.getBootstrapServers();
        consumerThread = new Consumer();
        consumerThread.start();
        replyConsumer = createKafkaConsumer();
        replyConsumer.subscribe(Collections.singletonList(REPLY_TOPIC));
        producer = new KafkaProducer<>(
            ImmutableMap.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.CLIENT_ID_CONFIG, UUID.randomUUID().toString(),
                // This should guarantee that records are batched, as long as they are sent within the configured duration
                ProducerConfig.LINGER_MS_CONFIG, 50
            ),
            new StringSerializer(),
            new StringSerializer()
        );
    }

    @AfterClass
    public static void tearDown() {
        producer.close();
        replyConsumer.unsubscribe();
        replyConsumer.close();
        consumerThread.terminate();
        kafka.stop();
    }

    @Before
    public void startTransaction() {
        startAndActivateTransaction(null);
        testScenario = TestScenario.NORMAL;
    }

    private void startAndActivateTransaction(@Nullable Sampler sampler) {
        Transaction transaction;
        if (sampler == null) {
            transaction = tracer.startRootTransaction(null);
        } else {
            transaction = tracer.startRootTransaction(sampler, -1, null);
        }
        if (transaction != null) {
            transaction.activate()
                .withName("Kafka-Test Transaction")
                .withType("request")
                .withResult("success")
                .withOutcome(Outcome.SUCCESS);
        }
    }

    @After
    public void endTransaction() {
        Transaction currentTransaction = tracer.currentTransaction();
        if (currentTransaction != null) {
            currentTransaction.deactivate().end();
        }
    }

    @Test
    public void testSendTwoRecords_IterableFor() {
        consumerThread.setIterationMode(RecordIterationMode.ITERABLE_FOR);
        sendTwoRecordsAndConsumeReplies();
        verifyTracing();
    }

    @Test
    public void testSendTwoRecords_IterableForEach() {
        consumerThread.setIterationMode(RecordIterationMode.ITERABLE_FOREACH);
        sendTwoRecordsAndConsumeReplies();
        verifyTracing();
    }

    @Test
    public void testSendTwoRecords_IterableSpliterator() {
        consumerThread.setIterationMode(RecordIterationMode.ITERABLE_SPLITERATOR);
        sendTwoRecordsAndConsumeReplies();
        verifyTracing();
    }

    @Test
    public void testSendTwoRecords_RecordsIterable() {
        consumerThread.setIterationMode(RecordIterationMode.RECORDS_ITERABLE);
        sendTwoRecordsAndConsumeReplies();
        verifyTracing();
    }

    @Test
    public void testSendTwoRecords_RecordListIterableFor() {
        consumerThread.setIterationMode(RecordIterationMode.RECORD_LIST_ITERABLE_FOR);
        sendTwoRecordsAndConsumeReplies();
        verifyTracing();
    }

    @Test
    public void testSendTwoRecords_RecordListIterableForEach() {
        consumerThread.setIterationMode(RecordIterationMode.RECORD_LIST_ITERABLE_FOREACH);
        sendTwoRecordsAndConsumeReplies();
        verifyTracing();
    }

    @Test
    public void testSendTwoRecords_RecordListSubList() {
        consumerThread.setIterationMode(RecordIterationMode.RECORD_LIST_SUB_LIST);
        sendTwoRecordsAndConsumeReplies();
        verifyTracing();
    }

    @Test
    public void testSendTwoRecords_PartiallyIterate() {
        // Here we test that the KafkaConsumer#poll instrumentation will end transactions that are left open
        consumerThread.setIterationMode(RecordIterationMode.PARTIALLY_ITERATE);
        sendTwoRecordsAndConsumeReplies();
        verifyTracing();
    }

    @Test
    public void testBodyCaptureEnabled() {
        doReturn(CoreConfiguration.EventType.ALL).when(coreConfiguration).getCaptureBody();
        testScenario = TestScenario.BODY_CAPTURE_ENABLED;
        consumerThread.setIterationMode(RecordIterationMode.ITERABLE_FOR);
        sendTwoRecordsAndConsumeReplies();
        verifyTracing();
    }

    @Test
    public void testHeaderCaptureDisabled() {
        doReturn(false).when(coreConfiguration).isCaptureHeaders();
        testScenario = TestScenario.HEADERS_CAPTURE_DISABLED;
        consumerThread.setIterationMode(RecordIterationMode.ITERABLE_FOR);
        sendTwoRecordsAndConsumeReplies();
        verifyTracing();
    }

    @Test
    public void testHeaderSanitation() {
        testScenario = TestScenario.SANITIZED_HEADER;
        consumerThread.setIterationMode(RecordIterationMode.ITERABLE_FOR);
        sendTwoRecordsAndConsumeReplies();
        verifyTracing();
    }

    @Test
    public void testBatchProcessing() {
        testScenario = TestScenario.BATCH_PROCESSING;
        consumerThread.setIterationMode(RecordIterationMode.ITERATE_WITHIN_TRANSACTION);
        sendTwoRecordsAndConsumeReplies();
        List<Span> sendSpans =
            reporter.getSpans().stream().filter(span -> span.getNameAsString().contains("send to " + REQUEST_TOPIC)).collect(Collectors.toList());
        assertThat(sendSpans).hasSize(2);
        Transaction batchProcessingTransaction = reporter.getFirstTransaction();
        verifySpanLinks(batchProcessingTransaction.getSpanLinks(), sendSpans.toArray(new Span[2]));
    }

    @Test
    public void testDestinationAddressCollectionDisabled() {
        doReturn(false).when(messagingConfiguration).shouldCollectQueueAddress();
        testScenario = TestScenario.TOPIC_ADDRESS_COLLECTION_DISABLED;
        consumerThread.setIterationMode(RecordIterationMode.ITERABLE_FOR);
        reporter.disableCheckDestinationAddress();
        sendTwoRecordsAndConsumeReplies();
        verifyTracing();
    }

    @Test
    public void testIgnoreTopic() {
        doReturn(List.of(WildcardMatcher.valueOf(REQUEST_TOPIC))).when(messagingConfiguration).getIgnoreMessageQueues();
        testScenario = TestScenario.IGNORE_REQUEST_TOPIC;
        consumerThread.setIterationMode(RecordIterationMode.ITERABLE_FOR);
        sendTwoRecordsAndConsumeReplies();

        // we expect only one span for polling the reply topic
        List<Span> spans = reporter.getSpans();
        assertThat(spans.size()).isGreaterThanOrEqualTo(1);
        verifyPollSpanContents(spans);
        List<Transaction> transactions = reporter.getTransactions();
        assertThat(transactions).isEmpty();
    }

    @Test
    public void testTransactionCreationWithoutContext() {
        testScenario = TestScenario.NO_CONTEXT_PROPAGATION;
        consumerThread.setIterationMode(RecordIterationMode.ITERABLE_FOR);
        //noinspection ConstantConditions
        tracer.currentTransaction().deactivate().end();
        reporter.reset();

        // Send without context
        sendTwoRecordsAndConsumeReplies();

        // We expect two transactions from records read from the request topic, each creating a send span as well.
        // In addition, we expect two transactions from the main test thread, iterating over reply messages.
        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(2);
        Span sendSpan1 = spans.get(0);
        verifySendSpanContents(sendSpan1, REPLY_TOPIC);
        Span sendSpan2 = spans.get(1);
        verifySendSpanContents(sendSpan2, REPLY_TOPIC);
        List<Transaction> transactions = reporter.getTransactions();
        assertThat(transactions).hasSize(4);
        verifyKafkaTransactionContents(transactions.get(0), null, null, REQUEST_TOPIC);
        verifyKafkaTransactionContents(transactions.get(1), null, null, REQUEST_TOPIC);
        verifyKafkaTransactionContents(transactions.get(2), sendSpan1, null, REPLY_TOPIC);
        verifyKafkaTransactionContents(transactions.get(3), sendSpan2, null, REPLY_TOPIC);
    }

    @Test
    public void testAgentPaused() {
        TracerInternalApiUtils.pauseTracer(tracer);
        int transactionCount = objectPoolFactory.getTransactionPool().getRequestedObjectCount();
        int spanCount = objectPoolFactory.getSpanPool().getRequestedObjectCount();

        // End current transaction and start a non-sampled one
        //noinspection ConstantConditions
        tracer.currentTransaction().deactivate().end();
        reporter.reset();

        testScenario = TestScenario.AGENT_PAUSED;
        startAndActivateTransaction(ConstantSampler.of(false));
        sendTwoRecordsAndConsumeReplies();

        assertThat(reporter.getTransactions()).isEmpty();
        assertThat(reporter.getSpans()).isEmpty();
        assertThat(objectPoolFactory.getTransactionPool().getRequestedObjectCount()).isEqualTo(transactionCount);
        assertThat(objectPoolFactory.getSpanPool().getRequestedObjectCount()).isEqualTo(spanCount);
    }

    private void sendTwoRecordsAndConsumeReplies() {
        final StringBuilder callback = new StringBuilder();
        ProducerRecord<String, String> record1 = new ProducerRecord<>(REQUEST_TOPIC, 0, REQUEST_KEY, FIRST_MESSAGE_VALUE);
        String headerKey = (testScenario == TestScenario.SANITIZED_HEADER) ? PASSWORD_HEADER_KEY : TEST_HEADER_KEY;
        record1.headers().add(headerKey, TEST_HEADER_VALUE.getBytes(StandardCharsets.UTF_8));
        ProducerRecord<String, String> record2 = new ProducerRecord<>(REQUEST_TOPIC, REQUEST_KEY, SECOND_MESSAGE_VALUE);
        record2.headers().add(headerKey, TEST_HEADER_VALUE.getBytes(StandardCharsets.UTF_8));
        producer.send(record1);
        producer.send(record2, (metadata, exception) -> callback.append("done"));
        if (testScenario != TestScenario.IGNORE_REQUEST_TOPIC && testScenario != TestScenario.AGENT_PAUSED && testScenario != TestScenario.BATCH_PROCESSING) {
            await().atMost(2000, MILLISECONDS).until(() -> reporter.getTransactions().size() == 2);
            if (testScenario != TestScenario.NON_SAMPLED_TRANSACTION) {
                int expectedSpans = (testScenario == TestScenario.NO_CONTEXT_PROPAGATION) ? 2 : 4;
                await().atMost(500, MILLISECONDS).until(() -> reporter.getSpans().size() == expectedSpans);
            }
        }
        List<ConsumerRecord<String, String>> replies = awaitReplyRecords(5000, 2);
        assertThat(callback).isNotEmpty();
        Iterator<ConsumerRecord<String, String>> iterator = replies.iterator();
        assertThat(iterator.next().value()).isEqualTo(FIRST_MESSAGE_VALUE);
        assertThat(iterator.next().value()).isEqualTo(SECOND_MESSAGE_VALUE);
        // this is required in order to end transactions related to the record iteration
        assertThat(iterator.hasNext()).isFalse();
    }

    @SuppressWarnings("SameParameterValue")
    private List<ConsumerRecord<String, String>> awaitReplyRecords(long timeoutMs, int expectedRecords) {
        List<ConsumerRecord<String, String>> replies = new ArrayList<>();
        long start = System.currentTimeMillis();
        long pollTime = 100;
        while (System.currentTimeMillis() + pollTime - start < timeoutMs) {
            //noinspection deprecation - this poll overload is deprecated in newer clients, but enables testing of old ones
            ConsumerRecords<String, String> records = replyConsumer.poll(pollTime);
            if (!records.isEmpty()) {
                records.forEach(replies::add);
            }
            if (replies.size() == expectedRecords) {
                return replies;
            }
        }
        throw new RuntimeException(String.format("Failed to read %s replies within %s ms", expectedRecords, timeoutMs));
    }

    private void verifyTracing() {
        List<Span> spans = reporter.getSpans();
        // we expect two send spans to request topic, two send spans to reply topic and at least one poll span from reply topic
        assertThat(spans.size()).isGreaterThanOrEqualTo(5);
        Span sendRequestSpan0 = spans.get(0);
        verifySendSpanContents(sendRequestSpan0, REQUEST_TOPIC);
        Span sendRequestSpan1 = spans.get(1);
        verifySendSpanContents(sendRequestSpan1, REQUEST_TOPIC);
        Span sendReplySpan0 = spans.get(2);
        verifySendSpanContents(sendReplySpan0, REPLY_TOPIC);
        Span sendReplySpan1 = spans.get(3);
        verifySendSpanContents(sendReplySpan1, REPLY_TOPIC);

        List<Transaction> transactions = reporter.getTransactions();
        assertThat(transactions).hasSize(2);
        verifyKafkaTransactionContents(transactions.get(0), sendRequestSpan0, FIRST_MESSAGE_VALUE, REQUEST_TOPIC);
        verifyKafkaTransactionContents(transactions.get(1), sendRequestSpan1, SECOND_MESSAGE_VALUE, REQUEST_TOPIC);

        List<Span> pollSpans = spans.stream().filter(span -> !span.getSpanLinks().isEmpty()).collect(Collectors.toList());
        verifyPollSpanContents(pollSpans, sendReplySpan0, sendReplySpan1);
    }

    private void verifyPollSpanContents(List<Span> pollSpans, Span... sendSpans) {
        List<TraceContext> spanLinks = new ArrayList<>();
        // collecting Reply-Topic polling spans that returned with records
        pollSpans.forEach(pollSpan -> {
            assertThat(pollSpan.getType()).isEqualTo("messaging");
            assertThat(pollSpan.getSubtype()).isEqualTo("kafka");
            assertThat(pollSpan.getAction()).isEqualTo("poll");
            assertThat(pollSpan.getNameAsString()).isEqualTo("KafkaConsumer#poll");
            assertThat(pollSpan.getContext().getServiceTarget())
                .hasType("kafka")
                .hasNoName();
            spanLinks.addAll(pollSpan.getSpanLinks());
        });
        verifySpanLinks(spanLinks, sendSpans);
    }

    private void verifySpanLinks(List<TraceContext> spanLinks, Span... sendSpans) {
        assertThat(spanLinks).hasSize(sendSpans.length);
        Arrays.stream(sendSpans).forEach(
            sendSpan -> assertThat(spanLinks.stream()).anyMatch(link -> link.getParentId().equals(sendSpan.getTraceContext().getId()))
        );
    }

    private void verifySendSpanContents(Span sendSpan, String topicName) {
        assertThat(sendSpan.getType()).isEqualTo("messaging");
        assertThat(sendSpan.getSubtype()).isEqualTo("kafka");
        assertThat(sendSpan.getAction()).isEqualTo("send");
        assertThat(sendSpan.getNameAsString()).isEqualTo("KafkaProducer#send to " + topicName);
        SpanContext context = sendSpan.getContext();
        Message message = context.getMessage();
        assertThat(message.getQueueName()).isEqualTo(topicName);
        Destination destination = context.getDestination();
        if (testScenario != TestScenario.TOPIC_ADDRESS_COLLECTION_DISABLED) {
            assertThat(destination.getPort()).isEqualTo(kafkaPort);
            assertThat(destination.getAddress().toString()).isEqualTo(kafka.getContainerIpAddress());
        } else {
            assertThat(destination.getPort()).isEqualTo(0);
            assertThat(destination.getAddress().toString()).isEmpty();
        }

        assertThat(context.getServiceTarget())
            .hasType("kafka")
            .hasName(topicName)
            .hasDestinationResource("kafka/" + topicName);
    }

    private void verifyKafkaTransactionContents(Transaction transaction, @Nullable Span parentSpan,
                                                @Nullable String messageValue, String topic) {
        assertThat(transaction.getType()).isEqualTo("messaging");
        assertThat(transaction.getNameAsString()).isEqualTo("Kafka record from " + topic);
        assertThat(transaction.getFrameworkName()).isEqualTo("Kafka");

        TraceContext traceContext = transaction.getTraceContext();
        if (parentSpan != null) {
            assertThat(traceContext.getTraceId()).isEqualTo(parentSpan.getTraceContext().getTraceId());
            assertThat(traceContext.getParentId()).isEqualTo(parentSpan.getTraceContext().getId());
        }
        TransactionContext transactionContext = transaction.getContext();
        Message message = transactionContext.getMessage();
        assertThat(message.getAge()).isGreaterThanOrEqualTo(0);
        assertThat(message.getQueueName()).isEqualTo(topic);
        if (testScenario == TestScenario.BODY_CAPTURE_ENABLED && messageValue != null) {
            String messageBody = "key=" + REQUEST_KEY + "; value=" + messageValue;
            StringBuilder body = message.getBodyForRead();
            assertThat(body).isNotNull();
            assertThat(messageBody).isEqualTo(body.toString());
        } else {
            assertThat(message.getBodyForRead()).isNull();
        }
        Headers headers = message.getHeaders();
        if (testScenario == TestScenario.HEADERS_CAPTURE_DISABLED || testScenario == TestScenario.SANITIZED_HEADER ||
            topic.equals(REPLY_TOPIC)) {
            assertThat(headers).isEmpty();
        } else {
            assertThat(headers.size()).isEqualTo(1);
            Headers.Header testHeader = headers.iterator().next();
            assertThat(testHeader.getKey()).isEqualTo(TEST_HEADER_KEY);
            //noinspection ConstantConditions
            assertThat(testHeader.getValue().toString()).isEqualTo(TEST_HEADER_VALUE);
        }
    }

    static KafkaConsumer<String, String> createKafkaConsumer() {
        return new KafkaConsumer<>(
            ImmutableMap.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "tc-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
            ),
            new StringDeserializer(),
            new StringDeserializer()
        );
    }

    static class Consumer extends Thread {
        private volatile boolean running;
        private volatile RecordIterationMode iterationMode;

        @Override
        public synchronized void start() {
            running = true;
            super.start();
        }

        void setIterationMode(RecordIterationMode iterationMode) {
            this.iterationMode = iterationMode;
        }

        public synchronized void terminate() {
            running = false;
            this.interrupt();
        }

        @Override
        public void run() {
            KafkaConsumer<String, String> kafkaConsumer = createKafkaConsumer();
            kafkaConsumer.subscribe(Collections.singletonList(REQUEST_TOPIC));
            while (running) {
                try {
                    //noinspection deprecation - this poll overload is deprecated in newer clients, but enables testing of old ones
                    ConsumerRecords<String, String> records = kafkaConsumer.poll(100);
                    if (records != null && !records.isEmpty()) {
                        // Can't use switch because we run this test in a dedicated class loader, where the anonymous
                        // class created by the enum switch cannot be loaded
                        if (iterationMode == RecordIterationMode.ITERABLE_FOR) {
                            for (ConsumerRecord<String, String> record : records) {
                                producer.send(new ProducerRecord<>(REPLY_TOPIC, REPLY_KEY, record.value()));
                            }
                        } else if (iterationMode == RecordIterationMode.ITERABLE_FOREACH) {
                            records.forEach(new ConsumerRecordConsumer());
                        } else if (iterationMode == RecordIterationMode.ITERABLE_SPLITERATOR) {
                            records.spliterator().forEachRemaining(new ConsumerRecordConsumer());
                        } else if (iterationMode == RecordIterationMode.RECORDS_ITERABLE) {
                            for (ConsumerRecord<String, String> record : records.records(REQUEST_TOPIC)) {
                                producer.send(new ProducerRecord<>(REPLY_TOPIC, REPLY_KEY, record.value()));
                            }
                        } else if (iterationMode == RecordIterationMode.RECORD_LIST_ITERABLE_FOR) {
                            List<ConsumerRecord<String, String>> recordList = records.records(records.partitions().iterator().next());
                            for (ConsumerRecord<String, String> record : recordList) {
                                producer.send(new ProducerRecord<>(REPLY_TOPIC, REPLY_KEY, record.value()));
                            }
                        } else if (iterationMode == RecordIterationMode.RECORD_LIST_SUB_LIST) {
                            List<ConsumerRecord<String, String>> recordList = records.records(records.partitions().iterator().next());
                            for (ConsumerRecord<String, String> record : recordList.subList(0, 2)) {
                                producer.send(new ProducerRecord<>(REPLY_TOPIC, REPLY_KEY, record.value()));
                            }
                        } else if (iterationMode == RecordIterationMode.RECORD_LIST_ITERABLE_FOREACH) {
                            List<ConsumerRecord<String, String>> recordList = records.records(records.partitions().iterator().next());
                            recordList.forEach(new ConsumerRecordConsumer());
                        } else if (iterationMode == RecordIterationMode.PARTIALLY_ITERATE) {
                            // we should normally get a batch of two, but may get one in two different polls
                            List<ConsumerRecord<String, String>> recordList = records.records(records.partitions().iterator().next());
                            Iterator<ConsumerRecord<String, String>> iterator = recordList.iterator();
                            ConsumerRecord<String, String> record = iterator.next();
                            producer.send(new ProducerRecord<>(REPLY_TOPIC, REPLY_KEY, record.value()));
                            if (recordList.size() == 2) {
                                record = iterator.next();
                                producer.send(new ProducerRecord<>(REPLY_TOPIC, REPLY_KEY, record.value()));
                            }
                        } else if (iterationMode == RecordIterationMode.ITERATE_WITHIN_TRANSACTION) {
                            Transaction transaction = Objects.requireNonNull(tracer.startRootTransaction(null))
                                .withName("Batch-processing Transaction")
                                .activate();
                            if (records.isEmpty()) {
                                transaction.ignoreTransaction();
                            } else {
                                records.forEach(new ConsumerRecordConsumer());
                            }
                            transaction.deactivate().end();
                        }
                    }
                } catch (Exception e) {
                    if (!(e instanceof InterruptException)) {
                        System.err.println("Kafka consumer failure: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
            kafkaConsumer.unsubscribe();
            kafkaConsumer.close();
        }
    }

    enum RecordIterationMode {
        ITERABLE_FOR,
        ITERABLE_FOREACH,
        ITERABLE_SPLITERATOR,
        RECORD_LIST_ITERABLE_FOR,
        RECORD_LIST_ITERABLE_FOREACH,
        RECORD_LIST_SUB_LIST,
        RECORDS_ITERABLE,
        PARTIALLY_ITERATE,
        ITERATE_WITHIN_TRANSACTION
    }

    enum TestScenario {
        NORMAL,
        BODY_CAPTURE_ENABLED,
        HEADERS_CAPTURE_DISABLED,
        SANITIZED_HEADER,
        IGNORE_REQUEST_TOPIC,
        AGENT_PAUSED,
        NO_CONTEXT_PROPAGATION,
        TOPIC_ADDRESS_COLLECTION_DISABLED,
        NON_SAMPLED_TRANSACTION,
        BATCH_PROCESSING
    }

    /**
     * Must implement explicitly in order to use the dependency injection runner
     */
    static class ConsumerRecordConsumer implements java.util.function.Consumer<ConsumerRecord<String, String>> {
        @Override
        public void accept(ConsumerRecord<String, String> record) {
            Future<RecordMetadata> send = producer.send(new ProducerRecord<>(REPLY_TOPIC, REPLY_KEY, record.value()));
            try {
                RecordMetadata recordMetadata = send.get();
                System.out.println("Record sent: " + recordMetadata);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
