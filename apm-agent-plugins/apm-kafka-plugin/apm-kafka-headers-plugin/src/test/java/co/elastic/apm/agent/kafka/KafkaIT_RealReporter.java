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
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.MessagingConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.report.Reporter;
import co.elastic.apm.agent.testutils.TestContainersUtils;
import net.bytebuddy.agent.ByteBuddyAgent;
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
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Future;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

/**
 * A duplicate of {@link KafkaIT}, only uses a real reporter and initializes instrumentations instead of extending
 * {@link AbstractInstrumentationTest}.
 * Useful for end-to-end testing with a real stack for showcasing multiple messaging system scenarios.
 */
@SuppressWarnings("NotNullFieldNotInitialized")
@Ignore
public class KafkaIT_RealReporter {

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

    private static CoreConfiguration coreConfiguration;
    private static MessagingConfiguration messagingConfiguration;

    private static TestScenario testScenario;
    private static Reporter reporter;
    private static ElasticApmTracer tracer;
    private static ConfigurationRegistry config;

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

        config = SpyConfiguration.createSpyConfig();
        StacktraceConfiguration stacktraceConfiguration = config.getConfig(StacktraceConfiguration.class);
        doReturn(30).when(stacktraceConfiguration).getStackTraceLimit();
        tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(config)
            .buildAndStart();
        reporter = tracer.getReporter();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());
        testScenario = TestScenario.NORMAL;

        coreConfiguration = config.getConfig(CoreConfiguration.class);
        messagingConfiguration = config.getConfig(MessagingConfiguration.class);
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
        Transaction transaction;
        transaction = tracer.startRootTransaction(null);
        if (transaction != null) {
            transaction.activate()
                .withName("Kafka-Test Transaction")
                .withType("request")
                .withResult("success")
                .withOutcome(Outcome.SUCCESS);
        }
    }

    @After
    public void endAndReportTransaction() {
        Transaction currentTransaction = tracer.currentTransaction();
        if (currentTransaction != null) {
            currentTransaction.deactivate().end();
        }
        reporter.flush(2000, MILLISECONDS, false);
        SpyConfiguration.reset(config);
    }

    @Test
    public void testSendTwoRecords_IterableFor() {
        consumerThread.setIterationMode(RecordIterationMode.ITERABLE_FOR);
        sendTwoRecordsAndConsumeReplies();
    }

    @Test
    public void testSendTwoRecords_IterableForEach() {
        consumerThread.setIterationMode(RecordIterationMode.ITERABLE_FOREACH);
        sendTwoRecordsAndConsumeReplies();
    }

    @Test
    public void testSendTwoRecords_IterableSpliterator() {
        consumerThread.setIterationMode(RecordIterationMode.ITERABLE_SPLITERATOR);
        sendTwoRecordsAndConsumeReplies();
    }

    @Test
    public void testSendTwoRecords_RecordsIterable() {
        consumerThread.setIterationMode(RecordIterationMode.RECORDS_ITERABLE);
        sendTwoRecordsAndConsumeReplies();
    }

    @Test
    public void testSendTwoRecords_RecordListIterableFor() {
        consumerThread.setIterationMode(RecordIterationMode.RECORD_LIST_ITERABLE_FOR);
        sendTwoRecordsAndConsumeReplies();
    }

    @Test
    public void testSendTwoRecords_RecordListIterableForEach() {
        consumerThread.setIterationMode(RecordIterationMode.RECORD_LIST_ITERABLE_FOREACH);
        sendTwoRecordsAndConsumeReplies();
    }

    @Test
    public void testSendTwoRecords_RecordListSubList() {
        consumerThread.setIterationMode(RecordIterationMode.RECORD_LIST_SUB_LIST);
        sendTwoRecordsAndConsumeReplies();
    }

    @Test
    public void testSendTwoRecords_PartiallyIterate() {
        // Here we test that the KafkaConsumer#poll instrumentation will end transactions that are left open
        consumerThread.setIterationMode(RecordIterationMode.PARTIALLY_ITERATE);
        sendTwoRecordsAndConsumeReplies();
    }

    @Test
    public void testBodyCaptureEnabled() {
        doReturn(CoreConfiguration.EventType.ALL).when(coreConfiguration).getCaptureBody();
        testScenario = TestScenario.BODY_CAPTURE_ENABLED;
        consumerThread.setIterationMode(RecordIterationMode.ITERABLE_FOR);
        sendTwoRecordsAndConsumeReplies();
    }

    @Test
    public void testHeaderCaptureDisabled() {
        doReturn(false).when(coreConfiguration).isCaptureHeaders();
        testScenario = TestScenario.HEADERS_CAPTURE_DISABLED;
        consumerThread.setIterationMode(RecordIterationMode.ITERABLE_FOR);
        sendTwoRecordsAndConsumeReplies();
    }

    @Test
    public void testHeaderSanitation() {
        testScenario = TestScenario.SANITIZED_HEADER;
        consumerThread.setIterationMode(RecordIterationMode.ITERABLE_FOR);
        sendTwoRecordsAndConsumeReplies();
    }

    @Test
    public void testBatchProcessing() {
        testScenario = TestScenario.BATCH_PROCESSING;
        consumerThread.setIterationMode(RecordIterationMode.ITERATE_WITHIN_TRANSACTION);
        sendTwoRecordsAndConsumeReplies();
    }

    @Test
    public void testDestinationAddressCollectionDisabled() {
        doReturn(false).when(messagingConfiguration).shouldCollectQueueAddress();
        testScenario = TestScenario.TOPIC_ADDRESS_COLLECTION_DISABLED;
        consumerThread.setIterationMode(RecordIterationMode.ITERABLE_FOR);
        sendTwoRecordsAndConsumeReplies();
    }

    @Test
    public void testIgnoreTopic() {
        doReturn(List.of(WildcardMatcher.valueOf(REQUEST_TOPIC))).when(messagingConfiguration).getIgnoreMessageQueues();
        testScenario = TestScenario.IGNORE_REQUEST_TOPIC;
        consumerThread.setIterationMode(RecordIterationMode.ITERABLE_FOR);
        sendTwoRecordsAndConsumeReplies();
    }

    @Test
    public void testTransactionCreationWithoutContext() {
        testScenario = TestScenario.NO_CONTEXT_PROPAGATION;
        consumerThread.setIterationMode(RecordIterationMode.ITERABLE_FOR);
        //noinspection ConstantConditions
        tracer.currentTransaction().deactivate().end();

        // Send without context
        sendTwoRecordsAndConsumeReplies();
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
