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


import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.impl.transaction.TraceContextImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import co.elastic.apm.agent.rabbitmq.components.batch.BatchListenerComponent;
import co.elastic.apm.agent.rabbitmq.config.BatchConfiguration;
import co.elastic.apm.agent.tracer.configuration.MessagingConfiguration;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.rabbit.core.BatchingRabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.doReturn;

@RunWith(SpringRunner.class)
@SpringBootTest
@ContextConfiguration(classes = {BatchConfiguration.class, BatchListenerComponent.class}, initializers = {RabbitMqTestBase.Initializer.class})
@Ignore("Test causes CI flakyness, presumably due to unclean shutdown of RabbitMqTestBase: Can pre reproduced by running the test in repeated mode locally.")
public class SpringAmqpBatchIT extends RabbitMqTestBase {

    @Autowired
    private BatchingRabbitTemplate batchingRabbitTemplate;

    @Test
    public void testTransactionPerMessage_noDistributedTracing() {
        doReturn(MessagingConfiguration.BatchStrategy.SINGLE_HANDLING).when(config.getConfig(MessagingConfiguration.class)).getMessageBatchStrategy();

        batchingRabbitTemplate.convertAndSend(TestConstants.QUEUE_NAME, "hello");
        batchingRabbitTemplate.convertAndSend(TestConstants.QUEUE_NAME, "hello");
        batchingRabbitTemplate.convertAndSend(TestConstants.QUEUE_NAME, "hello");
        batchingRabbitTemplate.convertAndSend(TestConstants.QUEUE_NAME, "hello");

        getReporter().awaitTransactionCount(4);

        List<TransactionImpl> transactionList = getReporter().getTransactions();

        assertThat(transactionList.size()).isEqualTo(4);
        for (TransactionImpl transaction : transactionList) {
            assertThat(transaction.getNameAsString()).isEqualTo("Spring AMQP RECEIVE from <default>");
            assertThat(transaction.getSpanCount().getTotal().get()).isEqualTo(1);
        }

        List<SpanImpl> spans = getReporter().getSpans();
        assertThat(spans.size()).isEqualTo(4);
        for (SpanImpl span : spans) {
            assertThat(span.getNameAsString()).isEqualTo("testSpan");
        }
    }

    @Test
    public void testTransactionPerMessage_withDistributedTracing() {
        doReturn(MessagingConfiguration.BatchStrategy.SINGLE_HANDLING).when(config.getConfig(MessagingConfiguration.class)).getMessageBatchStrategy();

        TransactionImpl rootTraceTransaction = getTracer().startRootTransaction(null);
        Objects.requireNonNull(rootTraceTransaction).activate();

        batchingRabbitTemplate.convertAndSend(TestConstants.QUEUE_NAME, "hello");
        batchingRabbitTemplate.convertAndSend(TestConstants.QUEUE_NAME, "hello");
        batchingRabbitTemplate.convertAndSend(TestConstants.QUEUE_NAME, "hello");
        batchingRabbitTemplate.convertAndSend(TestConstants.QUEUE_NAME, "hello");

        String traceId = rootTraceTransaction.getTraceContext().getTraceId().toString();

        // 4 custom spans and 2 send spans because batch size is configured to 2
        getReporter().awaitSpanCount(6);

        List<SpanImpl> customSpans = getReporter().getSpans().stream()
            .filter(span -> Objects.equals(span.getType(), "custom"))
            .sorted(Comparator.comparing(span -> span.getTraceContext().getId().toString()))
            .collect(Collectors.toList());
        assertThat(customSpans.size()).isEqualTo(4);
        for (SpanImpl span : customSpans) {
            assertThat(span.getNameAsString()).isEqualTo("testSpan");
            assertThat(span.getTraceContext().getTraceId().toString()).isEqualTo(traceId);
        }

        List<SpanImpl> sendSpans = getReporter().getSpans().stream()
            .filter(span -> Objects.equals(span.getType(), "messaging"))
            .sorted(Comparator.comparing(span -> span.getTraceContext().getId().toString()))
            .collect(Collectors.toList());
        assertThat(sendSpans.size()).isEqualTo(2);
        for (SpanImpl span : sendSpans) {
            assertThat(span.getNameAsString()).isEqualTo("RabbitMQ SEND to <default>");
            assertThat(span.getTraceContext().getTraceId().toString()).isEqualTo(traceId);
        }

        getReporter().awaitTransactionCount(4);
        List<TransactionImpl> transactionList = new ArrayList<>(getReporter().getTransactions());
        assertThat(transactionList.size()).isEqualTo(4);
        for (TransactionImpl transaction : transactionList) {
            assertThat(transaction.getNameAsString()).isEqualTo("Spring AMQP RECEIVE from <default>");
            assertThat(transaction.getSpanCount().getTotal().get()).isEqualTo(1);
            assertThat(transaction.getTraceContext().getTraceId().toString()).isEqualTo(traceId);
        }
        transactionList.sort(Comparator.comparing(transaction -> transaction.getTraceContext().getParentId().toString()));

        String firstSendSpan = sendSpans.get(0).getTraceContext().getId().toString();
        assertThat(transactionList.get(0).getTraceContext().getParentId().toString()).isEqualTo(firstSendSpan);
        assertThat(transactionList.get(1).getTraceContext().getParentId().toString()).isEqualTo(firstSendSpan);

        String secondSendSpan = sendSpans.get(1).getTraceContext().getId().toString();
        assertThat(transactionList.get(2).getTraceContext().getParentId().toString()).isEqualTo(secondSendSpan);
        assertThat(transactionList.get(3).getTraceContext().getParentId().toString()).isEqualTo(secondSendSpan);

        rootTraceTransaction.deactivate().end();
    }

    @Test
    public void testTransactionPerBatch() {
        TransactionImpl rootTraceTransaction = getTracer().startRootTransaction(null);
        Objects.requireNonNull(rootTraceTransaction).activate();

        batchingRabbitTemplate.convertAndSend(TestConstants.QUEUE_NAME, "hello");
        batchingRabbitTemplate.convertAndSend(TestConstants.QUEUE_NAME, "hello");
        batchingRabbitTemplate.convertAndSend(TestConstants.QUEUE_NAME, "hello");
        batchingRabbitTemplate.convertAndSend(TestConstants.QUEUE_NAME, "hello");

        // expecting one transaction per batch, of which configured size is 2
        getReporter().awaitTransactionCount(2);
        List<TransactionImpl> transactionList = getReporter().getTransactions();
        getReporter().awaitUntilTimeout(200, () -> assertThat(transactionList.size()).isEqualTo(2));

        List<SpanImpl> spans = getReporter().getSpans();
        // Expecting 2 send spans (one per two-message batch) and 4 custom test-spans
        assertThat(spans.size()).isEqualTo(6);

        List<SpanImpl> sendSpans = getReporter().getSpans().stream()
            .filter(span -> Objects.equals(span.getNameAsString(), "RabbitMQ SEND to <default>"))
            .collect(Collectors.toList());
        assertThat(sendSpans.size()).isEqualTo(2);
        sendSpans.forEach(span -> {
            assertThat(span.getType()).isEqualTo("messaging");
            assertThat(span.getTraceContext().getParentId()).isEqualTo(rootTraceTransaction.getTraceContext().getId());
        });

        List<SpanImpl> testSpans = getReporter().getSpans().stream()
            .filter(span -> Objects.equals(span.getNameAsString(), "testSpan"))
            .collect(Collectors.toList());
        assertThat(testSpans.size()).isEqualTo(4);
        testSpans.forEach(span -> {
            assertThat(span.getType()).isEqualTo("custom");
            // not a distributed trace - the batch is on a separate trace and the traces are linked through span links
            assertThat(span.getTraceContext().getTraceId()).isNotEqualTo(rootTraceTransaction.getTraceContext().getTraceId());
        });

        for (TransactionImpl transaction : transactionList) {
            assertThat(transaction.getNameAsString()).isEqualTo("Spring AMQP Message Batch Processing");
            assertThat(transaction.getSpanCount().getTotal().get()).isEqualTo(2);
            assertThat(testSpans.stream()
                .filter(span -> span.getTraceContext().getParentId().equals(transaction.getTraceContext().getId()))
                .count()
            ).isEqualTo(2);
            assertThat(transaction.getTraceContext().getTraceId()).isNotEqualTo(rootTraceTransaction.getTraceContext().getTraceId());

            List<TraceContextImpl> spanLinks = transaction.getSpanLinks();
            // we expect one span link because each batch of two messages is related to a single send span
            assertThat(spanLinks.size()).isEqualTo(1);
            TraceContextImpl spanLink = spanLinks.get(0);
            assertThat(sendSpans.stream().anyMatch(sendSpan -> sendSpan.getTraceContext().getId().equals(spanLink.getParentId()))).isTrue();
            assertThat(sendSpans.stream().anyMatch(sendSpan -> sendSpan.getTraceContext().getTraceId().equals(spanLink.getTraceId()))).isTrue();
        }

        rootTraceTransaction.deactivate().end();
    }
}

