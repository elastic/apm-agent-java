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
package co.elastic.apm.agent.kafka;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.context.Headers;
import co.elastic.apm.agent.impl.context.Message;
import co.elastic.apm.agent.impl.context.SpanContext;
import co.elastic.apm.agent.impl.context.TransactionContext;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
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

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

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

    static final String REQUEST_TOPIC = UUID.randomUUID().toString();
    static final String REPLY_TOPIC = UUID.randomUUID().toString();
    static final String REQUEST_KEY = "request-key";
    static final String REPLY_KEY = "response-key";
    public static final String MESSAGE_BODY = "test message body";
    public static final String TEST_KEY = "test_key";
    public static final String TEST_VALUE = "test_value";

    private static KafkaContainer kafka;
    private static String bootstrapServers;
    private static Consumer consumerThread;
    private static KafkaConsumer<String, String> replyConsumer;
    private static KafkaProducer<String, String> producer;

    private final CoreConfiguration coreConfiguration;

    public KafkaIT() {
        this.coreConfiguration = config.getConfig(CoreConfiguration.class);
    }

    @BeforeClass
    public static void setup() {
        // confluent versions 5.3.x correspond Kafka versions 2.3.x -
        // https://docs.confluent.io/current/installation/versions-interoperability.html#cp-and-apache-ak-compatibility
        kafka = new KafkaContainer("5.3.0");
        kafka.start();
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
        reporter.reset();
        Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null).activate();
        transaction.withName("Kafka-Test Transaction");
        transaction.withType("request");
        transaction.withResult("success");
        when(coreConfiguration.getCaptureBody()).thenReturn(CoreConfiguration.EventType.ALL);
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

    private void sendTwoRecordsAndConsumeReplies() {
        String value1 = UUID.randomUUID().toString();
        String value2 = UUID.randomUUID().toString();
        final StringBuilder callback = new StringBuilder();
        ProducerRecord<String, String> record1 = new ProducerRecord<>(REQUEST_TOPIC, REQUEST_KEY, value1);
        record1.headers().add(TEST_KEY, TEST_VALUE.getBytes(StandardCharsets.UTF_8));
        ProducerRecord<String, String> record2 = new ProducerRecord<>(REQUEST_TOPIC, REQUEST_KEY, value2);
        record2.headers().add(TEST_KEY, TEST_VALUE.getBytes(StandardCharsets.UTF_8));
        producer.send(record1);
        producer.send(record2, (metadata, exception) -> callback.append("done"));
        await().atMost(2000, MILLISECONDS).until(() -> reporter.getTransactions().size() == 2);
        await().atMost(500, MILLISECONDS).until(() -> reporter.getSpans().size() == 4);
        // todo: change back to 2.4.0 API - Duration.ofSeconds(2)
        ConsumerRecords<String, String> replies = replyConsumer.poll(2000);
        assertThat(callback).isNotEmpty();
        assertThat(replies.count()).isEqualTo(2);
        Iterator<ConsumerRecord<String, String>> iterator = replies.iterator();
        assertThat(iterator.next().value()).isEqualTo(value1);
        assertThat(iterator.next().value()).isEqualTo(value2);
    }

    private void verifyTracing() {
        List<Span> spans = reporter.getSpans();
        // we expect two send spans to request topic, two send spans to reply topic and one poll span from reply topic
        assertThat(spans).hasSize(5);
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
        verifyKafkaTransactionContents(transactions.get(0), sendRequestSpan0);
        verifyKafkaTransactionContents(transactions.get(1), sendRequestSpan1);

        Span pollSpan = spans.get(4);
        verifyPollSpanContents(pollSpan);
    }

    private void verifyPollSpanContents(Span pollSpan) {
        assertThat(pollSpan.getType()).isEqualTo("messaging");
        assertThat(pollSpan.getSubtype()).isEqualTo("kafka");
        assertThat(pollSpan.getAction()).isEqualTo("poll");
        assertThat(pollSpan.getNameAsString()).isEqualTo("KafkaConsumer#poll");
    }

    private void verifySendSpanContents(Span sendSpan, String requestTopic) {
        assertThat(sendSpan.getType()).isEqualTo("messaging");
        assertThat(sendSpan.getSubtype()).isEqualTo("kafka");
        assertThat(sendSpan.getAction()).isEqualTo("send");
        assertThat(sendSpan.getNameAsString()).isEqualTo("KafkaProducer#send to " + requestTopic);
        SpanContext context = sendSpan.getContext();
        Message message = context.getMessage();
        assertThat(message.getTopicName()).isEqualTo(requestTopic);
        // todo - add destination assertions
    }

    private void verifyKafkaTransactionContents(Transaction transaction, Span parentSpan) {
        assertThat(transaction.getType()).isEqualTo("messaging");
        assertThat(transaction.getNameAsString()).isEqualTo("Kafka record from " + REQUEST_TOPIC);
        TraceContext traceContext = transaction.getTraceContext();
        assertThat(traceContext.getTraceId()).isEqualTo(parentSpan.getTraceContext().getTraceId());
        assertThat(traceContext.getParentId()).isEqualTo(parentSpan.getTraceContext().getId());
        TransactionContext transactionContext = transaction.getContext();
        Message message = transactionContext.getMessage();
        assertThat(message.getAge()).isGreaterThanOrEqualTo(0);
        assertThat(message.getTopicName()).isEqualTo(REQUEST_TOPIC);
        // todo - message body
//        assertThat(MESSAGE_BODY).isEqualTo(message.getBody());
        Headers headers = message.getHeaders();
        assertThat(headers.size()).isEqualTo(1);
        Headers.Header testHeader = headers.iterator().next();
        assertThat(testHeader.getKey()).isEqualTo(TEST_KEY);
        assertThat(testHeader.getValue().toString()).isEqualTo(TEST_VALUE);
        // todo - add destination assertions
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
                    // todo: change back to 2.4.0 API - Duration.ofMillis(100)
                    ConsumerRecords<String, String> records = kafkaConsumer.poll(100);
                    if (records != null) {
                        // Can't use switch because of the test runner in a dedicated class loader
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
        RECORD_LIST,
        RECORDS_ITERABLE
    }

    /**
     * Must implement explicitly in order to use the dependency injection runner
     */
    static class ConsumerRecordConsumer implements java.util.function.Consumer<ConsumerRecord<String, String>> {
        @Override
        public void accept(ConsumerRecord<String, String> record) {
            producer.send(new ProducerRecord<>(REPLY_TOPIC, REPLY_KEY, record.value()));
        }
    }
}
