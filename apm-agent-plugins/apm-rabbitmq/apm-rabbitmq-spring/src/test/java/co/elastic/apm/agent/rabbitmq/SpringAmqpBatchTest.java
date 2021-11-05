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


import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.rabbitmq.config.BatchConfiguration;
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

@RunWith(SpringRunner.class)
@SpringBootTest
@ContextConfiguration(classes = {BatchConfiguration.class}, initializers = {RabbitMqTestBase.Initializer.class})
public class SpringAmqpBatchTest extends RabbitMqTestBase {

    @Autowired
    private BatchingRabbitTemplate batchingRabbitTemplate;

    @Test
    public void verifyThatTransactionWithSpanCreated_noDistributedTracing() {
        batchingRabbitTemplate.convertAndSend(TestConstants.QUEUE_NAME, "hello");
        batchingRabbitTemplate.convertAndSend(TestConstants.QUEUE_NAME, "hello");
        batchingRabbitTemplate.convertAndSend(TestConstants.QUEUE_NAME, "hello");
        batchingRabbitTemplate.convertAndSend(TestConstants.QUEUE_NAME, "hello");

        getReporter().awaitTransactionCount(4);

        List<Transaction> transactionList = getReporter().getTransactions();

        assertThat(transactionList.size()).isEqualTo(4);
        for (Transaction transaction : transactionList) {
            assertThat(transaction.getNameAsString()).isEqualTo("Spring AMQP RECEIVE from <default>");
            assertThat(transaction.getSpanCount().getTotal().get()).isEqualTo(1);
        }

        List<Span> spans = getReporter().getSpans();
        assertThat(spans.size()).isEqualTo(4);
        for (Span span : spans) {
            assertThat(span.getNameAsString()).isEqualTo("testSpan");
        }
    }

    @Test
    public void verifyThatTransactionWithSpanCreated_withDistributedTracing() {
        Transaction rootTraceTransaction = getTracer().startRootTransaction(null);
        Objects.requireNonNull(rootTraceTransaction).activate();

        batchingRabbitTemplate.convertAndSend(TestConstants.QUEUE_NAME, "hello");
        batchingRabbitTemplate.convertAndSend(TestConstants.QUEUE_NAME, "hello");
        batchingRabbitTemplate.convertAndSend(TestConstants.QUEUE_NAME, "hello");
        batchingRabbitTemplate.convertAndSend(TestConstants.QUEUE_NAME, "hello");

        String traceId = rootTraceTransaction.getTraceContext().getTraceId().toString();

        // 4 custom spans and 2 send spans because batch size is configured to 2
        getReporter().awaitSpanCount(6);

        List<Span> customSpans = getReporter().getSpans().stream()
            .filter(span -> Objects.equals(span.getType(), "custom"))
            .sorted(Comparator.comparing(span -> span.getTraceContext().getId().toString()))
            .collect(Collectors.toList());
        assertThat(customSpans.size()).isEqualTo(4);
        for (Span span : customSpans) {
            assertThat(span.getNameAsString()).isEqualTo("testSpan");
            assertThat(span.getTraceContext().getTraceId().toString()).isEqualTo(traceId);
        }

        List<Span> sendSpans = getReporter().getSpans().stream()
            .filter(span -> Objects.equals(span.getType(), "messaging"))
            .sorted(Comparator.comparing(span -> span.getTraceContext().getId().toString()))
            .collect(Collectors.toList());
        assertThat(sendSpans.size()).isEqualTo(2);
        for (Span span : sendSpans) {
            assertThat(span.getNameAsString()).isEqualTo("RabbitMQ SEND to <default>");
            assertThat(span.getTraceContext().getTraceId().toString()).isEqualTo(traceId);
        }

        getReporter().awaitTransactionCount(4);
        List<Transaction> transactionList = new ArrayList<>(getReporter().getTransactions());
        assertThat(transactionList.size()).isEqualTo(4);
        for (Transaction transaction : transactionList) {
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
}
