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

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.AfterClass;
import org.junit.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.RabbitMQContainer;

import java.io.IOException;
import java.util.List;

import static co.elastic.apm.agent.rabbitmq.TestConstants.DOCKER_TESTCONTAINER_RABBITMQ_IMAGE;
import static co.elastic.apm.agent.rabbitmq.TestConstants.ROUTING_KEY;
import static co.elastic.apm.agent.rabbitmq.TestConstants.TOPIC_EXCHANGE_NAME;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public abstract class AbstractRabbitMqTest extends AbstractInstrumentationTest {

    private static RabbitMQContainer container;

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            container = new RabbitMQContainer(DOCKER_TESTCONTAINER_RABBITMQ_IMAGE);
            container.start();

            TestPropertyValues.of(
                "spring.rabbitmq.host=" + container.getHost(),
                "spring.rabbitmq.port=" + container.getAmqpPort(),
                "spring.rabbitmq.username=" + container.getAdminUsername(),
                "spring.rabbitmq.password=" + container.getAdminPassword())
                .applyTo(configurableApplicationContext.getEnvironment());
        }
    }

    @AfterClass
    public static void after() throws IOException {
        container.close();
        ElasticApmAgent.reset();
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    public void verifyThatOneTransactionWithOneSpanCreated() {
        disableRecyclingValidation();

        String message = "foo-bar";
        rabbitTemplate.convertAndSend(TOPIC_EXCHANGE_NAME, ROUTING_KEY, message);

        getReporter().awaitTransactionCount(1);
        getReporter().awaitSpanCount(1);

        List<Transaction> transactionList = getReporter().getTransactions();

        assertThat(transactionList.size()).isEqualTo(1);
        Transaction transaction = transactionList.get(0);
        assertThat(transaction.getNameAsString()).isEqualTo("RabbitMQ RECEIVE from spring-boot-exchange");

        assertThat(transaction.getSpanCount().getTotal().get()).isEqualTo(1);
        assertThat(getReporter().getFirstSpan().getNameAsString()).isEqualTo("testSpan");
    }
}
