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
import co.elastic.apm.agent.impl.transaction.AbstractSpanImpl;
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import co.elastic.apm.agent.testutils.TestContainersUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;


public class KafkaSpringIT extends AbstractInstrumentationTest {

    private static final String SINGLE_TOPIC = "single-topic";
    private static final String BATCH_TOPIC = "batch-topic";

    private static KafkaContainer kafka;

    private static AnnotationConfigApplicationContext springContext;
    private static KafkaTemplate<Integer, String> kafkaTemplate;

    @BeforeClass
    @SuppressWarnings("unchecked")
    public static void setup() throws ClassNotFoundException {
        // confluent versions 7.1.0 correspond Kafka versions 3.1.0 -
        // https://docs.confluent.io/current/installation/versions-interoperability.html#cp-and-apache-ak-compatibility
        kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka").withTag("7.1.0"));
        kafka.withCreateContainerCmdModifier(TestContainersUtils.withMemoryLimit(4096));
        kafka.start();

        springContext = new AnnotationConfigApplicationContext(SpringConfig.class);
        kafkaTemplate = (KafkaTemplate<Integer, String>) springContext.getBean("kafkaTemplate");
    }

    @AfterClass
    public static void tearDown() {
        springContext.close();
        kafka.stop();
    }

    @Test
    public void testBatchReceive() {
        TransactionImpl transaction1 = startTestRootTransaction("Send 1");
        kafkaTemplate.send(BATCH_TOPIC, "data-1");
        transaction1.deactivate().end();

        TransactionImpl transaction2 = startTestRootTransaction("Send 2");
        kafkaTemplate.send(BATCH_TOPIC, "data-2");
        transaction2.deactivate().end();

        reporter.awaitTransactionCount(3, 10000);
        reporter.awaitSpanCount(3); //two kafka exit spans + the batchListener span

        List<SpanImpl> senderSpans = reporter.getSpans().stream()
            .filter(AbstractSpanImpl::isExit)
            .collect(Collectors.toList());
        assertThat(senderSpans).hasSize(2);

        TransactionImpl batchReceive = reporter.getTransactions().stream()
            .filter(transaction -> transaction.getNameAsString().equals("Spring Kafka Message Batch Processing"))
            .findFirst().orElse(null);

        assertThat(batchReceive)
            .isNotNull()
            .hasSpanLinkCount(2)
            .hasSpanLink(senderSpans.get(0))
            .hasSpanLink(senderSpans.get(1));

        SpanImpl listenerSpan = reporter.getSpanByName("batchListener");
        assertThat(listenerSpan)
            .isNotNull()
            .hasParent(batchReceive);
    }

    // This test has nothing to do with the SpringKafkaBatchListenerInstrumentation,
    // it just verifies that non-batch listeners are caught by the standard kafka instrumentation
    @Test
    public void testNonBatchSingleReceive() {
        TransactionImpl transaction1 = startTestRootTransaction("Send 1");
        kafkaTemplate.send(SINGLE_TOPIC, "data-1");
        transaction1.deactivate().end();

        reporter.awaitTransactionCount(2, 10000);
        reporter.awaitSpanCount(2); //one kafka exit spans + the singleListener span

        SpanImpl senderSpan = reporter.getSpans().stream()
            .filter(AbstractSpanImpl::isExit)
            .findFirst().orElse(null);

        assertThat(senderSpan).isNotNull();

        TransactionImpl singleReceive = reporter.getTransactions().stream()
            .filter(transaction -> "messaging".equals(transaction.getType()))
            .findFirst().orElse(null);

        assertThat(singleReceive)
            .isNotNull()
            .hasParent(senderSpan);

        SpanImpl listenerSpan = reporter.getSpanByName("singleListener");
        assertThat(listenerSpan)
            .isNotNull()
            .hasParent(singleReceive);
    }

    @Configuration
    @EnableKafka
    public static class SpringConfig {

        @Bean
        ConcurrentKafkaListenerContainerFactory<Integer, String>
        kafkaListenerContainerFactory(ConsumerFactory<Integer, String> consumerFactory) {
            ConcurrentKafkaListenerContainerFactory<Integer, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
            factory.setConsumerFactory(consumerFactory);
            return factory;
        }

        @Bean
        public ConsumerFactory<Integer, String> consumerFactory() {
            return new DefaultKafkaConsumerFactory<>(consumerProps());
        }

        private Map<String, Object> consumerProps() {
            Map<String, Object> props = new HashMap<>();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "group");
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, IntegerDeserializer.class);
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            return props;
        }

        @Bean
        public ProducerFactory<Integer, String> producerFactory() {
            return new DefaultKafkaProducerFactory<>(senderProps());
        }

        private Map<String, Object> senderProps() {
            Map<String, Object> props = new HashMap<>();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
            props.put(ProducerConfig.LINGER_MS_CONFIG, 1000);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, IntegerSerializer.class);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            return props;
        }

        @Bean
        public KafkaTemplate<Integer, String> kafkaTemplate(ProducerFactory<Integer, String> producerFactory) {
            return new KafkaTemplate<Integer, String>(producerFactory);
        }

        @Bean
        public BatchListener batchListener() {
            return new BatchListener();
        }

        @Bean
        public SingleListener singleListener() {
            return new SingleListener();
        }
    }

    public static class BatchListener {

        @KafkaListener(id = "my-batch", topics = BATCH_TOPIC, batch = "true")
        public void batchListener(List<String> in) {
            tracer.getActive().createSpan().withName("batchListener").end();
        }

    }

    public static class SingleListener {

        @KafkaListener(id = "my-single", topics = SINGLE_TOPIC)
        public void singleListener(List<String> in) {
            tracer.getActive().createSpan().withName("singleListener").end();
        }

    }

}
