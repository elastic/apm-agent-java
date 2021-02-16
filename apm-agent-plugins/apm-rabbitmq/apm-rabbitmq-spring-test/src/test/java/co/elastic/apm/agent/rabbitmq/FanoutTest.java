/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.agent.rabbitmq;

import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.rabbitmq.config.FanoutConfiguration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest
@ContextConfiguration(classes = {FanoutConfiguration.class}, initializers = {AbstractRabbitMqTest.Initializer.class})
public class FanoutTest extends AbstractRabbitMqTest {


    @Autowired
    @Qualifier("fanoutRabbitTemplate")
    private RabbitTemplate fanoutRabbitTemplate;

    @Test
    @Override
    public void verifyThatOneTransactionWithOneSpanCreated() {
        disableRecyclingValidation();

        String message = "hello from foobar";
        rabbitTemplate.convertAndSend(message);

        getReporter().awaitTransactionCount(2);
//        getReporter().awaitSpanCount(1);

        List<Transaction> transactionList = getReporter().getTransactions();

        assertThat(transactionList.size()).isEqualTo(2);
        Transaction transaction = transactionList.get(0);
        assertThat(transaction.getNameAsString()).isEqualTo("RabbitMQ RECEIVE from spring-boot-exchange");

        assertThat(transaction.getSpanCount().getTotal().get()).isEqualTo(1);
        assertThat(getReporter().getFirstSpan().getNameAsString()).isEqualTo("testSpan");
    }
}
