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
import co.elastic.apm.agent.rabbitmq.config.FanoutConfiguration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

import static co.elastic.apm.agent.rabbitmq.TestConstants.FANOUT_EXCHANGE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest
@ContextConfiguration(classes = {FanoutConfiguration.class}, initializers = {RabbitMqTestBase.Initializer.class})
public class FanoutIT extends RabbitMqTestBase {

    @Test
    public void verifyThatTransactionWithSpanCreated() {
        String message = "hello from foobar";
        rabbitTemplate.setExchange(FANOUT_EXCHANGE);
        rabbitTemplate.convertAndSend(message);

        getReporter().awaitTransactionCount(2);
        getReporter().awaitSpanCount(2);

        List<TransactionImpl> transactionList = getReporter().getTransactions();
        assertThat(transactionList.size()).isEqualTo(2);
        for (TransactionImpl transaction : transactionList) {
            assertThat(transaction.getNameAsString()).isEqualTo("RabbitMQ RECEIVE from foobar");
            assertThat(transaction.getSpanCount().getTotal().get()).isEqualTo(1);
        }

        List<SpanImpl> testSpans = getReporter().getSpans();
        for (SpanImpl testSpan : testSpans) {
            assertThat(testSpan.getNameAsString()).isEqualTo("testSpan");
        }
    }
}
