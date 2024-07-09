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
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import co.elastic.apm.agent.tracer.configuration.MessagingConfiguration;
import org.junit.Test;

import java.util.List;

import static co.elastic.apm.agent.rabbitmq.RabbitMQIT.checkParentChild;
import static co.elastic.apm.agent.rabbitmq.RabbitMQIT.checkSendSpan;
import static co.elastic.apm.agent.rabbitmq.RabbitMQIT.checkTransaction;
import static co.elastic.apm.agent.rabbitmq.RabbitMQIT.getNonRootTransaction;
import static co.elastic.apm.agent.rabbitmq.TestConstants.QUEUE_NAME;
import static co.elastic.apm.agent.rabbitmq.TestConstants.TOPIC_EXCHANGE_NAME;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.doReturn;

public abstract class AbstractRabbitMqTest extends RabbitMqTestBase {

    private static final String MESSAGE = "foo-bar";

    @Test
    public void verifyThatTransactionWithSpanCreated() {
        rabbitTemplate.convertAndSend(TOPIC_EXCHANGE_NAME, TestConstants.ROUTING_KEY, MESSAGE);
        TransactionImpl receiveTransaction = reporter.getFirstTransaction(1000);
        checkTransaction(receiveTransaction, TOPIC_EXCHANGE_NAME, "Spring AMQP");
        SpanImpl testSpan = reporter.getFirstSpan(1000);
        assertThat(testSpan.getNameAsString()).isEqualTo("testSpan");
        assertThat(testSpan.getType()).isEqualTo("custom");
        checkParentChild(receiveTransaction, testSpan);
    }

    @Test
    public void verifyThatTransactionWithSpanCreated_NamingModeQueue() {
        MessagingConfiguration messagingConfiguration = config.getConfig(MessagingConfiguration.class);
        doReturn(MessagingConfiguration.RabbitMQNamingMode.QUEUE).when(messagingConfiguration).getRabbitMQNamingMode();

        rabbitTemplate.convertAndSend(TOPIC_EXCHANGE_NAME, TestConstants.ROUTING_KEY, MESSAGE);
        TransactionImpl receiveTransaction = reporter.getFirstTransaction(1000);
        checkTransaction(receiveTransaction, QUEUE_NAME, "Spring AMQP");
        SpanImpl testSpan = reporter.getFirstSpan(1000);
        assertThat(testSpan.getNameAsString()).isEqualTo("testSpan");
        assertThat(testSpan.getType()).isEqualTo("custom");
        checkParentChild(receiveTransaction, testSpan);
    }

    @Test
    public void verifyThatTransactionWithSpanCreated_DistributedTracing() {
        TransactionImpl rootTransaction = startTestRootTransaction("Rabbit-Test Root Transaction");
        rabbitTemplate.convertAndSend(TOPIC_EXCHANGE_NAME, TestConstants.ROUTING_KEY, MESSAGE);
        rootTransaction.deactivate().end();

        getReporter().awaitTransactionCount(2);
        getReporter().awaitSpanCount(2);

        TransactionImpl receiveTransaction = getNonRootTransaction(rootTransaction, getReporter().getTransactions());
        checkTransaction(receiveTransaction, TOPIC_EXCHANGE_NAME, "Spring AMQP");
        assertThat(receiveTransaction.getSpanCount().getTotal().get()).isEqualTo(1);

        List<SpanImpl> spans = getReporter().getSpans();
        SpanImpl sendSpan = spans.stream().filter(span -> span.getType().equals("messaging")).findFirst().get();
        checkSendSpan(sendSpan, TOPIC_EXCHANGE_NAME, LOCALHOST_ADDRESS, container.getAmqpPort());
        checkParentChild(sendSpan, receiveTransaction);

        SpanImpl testSpan = spans.stream().filter(span -> span.getType().equals("custom")).findFirst().get();
        assertThat(testSpan.getNameAsString()).isEqualTo("testSpan");
        checkParentChild(receiveTransaction, testSpan);
    }

    @Test
    public void verifyTransactionWithDefaultExchangeName() {
        rabbitTemplate.convertAndSend(TestConstants.QUEUE_NAME, MESSAGE);
        TransactionImpl receiveTransaction = reporter.getFirstTransaction(1000);
        checkTransaction(receiveTransaction, "", "Spring AMQP");
        assertThat(receiveTransaction.getSpanCount().getTotal().get()).isEqualTo(1);
        SpanImpl testSpan = reporter.getFirstSpan(1000);
        assertThat(testSpan.getNameAsString()).isEqualTo("testSpan");
    }

    @Test
    public void verifyTransactionWithDefaultExchangeName_DistributedTracing() {
        TransactionImpl rootTransaction = startTestRootTransaction("Rabbit-Test Root Transaction");
        rabbitTemplate.convertAndSend(TestConstants.QUEUE_NAME, MESSAGE);
        rootTransaction.deactivate().end();

        getReporter().awaitTransactionCount(2);
        getReporter().awaitSpanCount(2);

        TransactionImpl receiveTransaction = getNonRootTransaction(rootTransaction, getReporter().getTransactions());
        checkTransaction(receiveTransaction, "", "Spring AMQP");
        assertThat(receiveTransaction.getSpanCount().getTotal().get()).isEqualTo(1);

        SpanImpl sendSpan = getReporter().getSpans().stream().filter(span -> span.getType().equals("messaging")).findFirst().get();
        checkSendSpan(sendSpan, "", LOCALHOST_ADDRESS, container.getAmqpPort());
        checkParentChild(sendSpan, receiveTransaction);
    }
}
