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

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.testutils.TestContainersUtils;
import org.junit.AfterClass;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.RabbitMQContainer;

public class RabbitMqTestBase extends AbstractInstrumentationTest {

    public static final String LOCALHOST_ADDRESS = "127.0.0.1";
    protected static RabbitMQContainer container = new RabbitMQContainer(TestConstants.DOCKER_TESTCONTAINER_RABBITMQ_IMAGE);

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            container.withExtraHost("localhost", LOCALHOST_ADDRESS);
            container.withCreateContainerCmdModifier(TestContainersUtils.withMemoryLimit(2048));
            container.start();

            TestPropertyValues.of(
                "spring.rabbitmq.host=" + LOCALHOST_ADDRESS,
                "spring.rabbitmq.port=" + container.getAmqpPort(),
                "spring.rabbitmq.username=" + container.getAdminUsername(),
                "spring.rabbitmq.password=" + container.getAdminPassword())
                .applyTo(configurableApplicationContext.getEnvironment());
        }
    }

    @AfterClass
    public static void after() {
        container.close();
        ElasticApmAgent.reset();
    }

    @Autowired
    public RabbitTemplate rabbitTemplate;
}
