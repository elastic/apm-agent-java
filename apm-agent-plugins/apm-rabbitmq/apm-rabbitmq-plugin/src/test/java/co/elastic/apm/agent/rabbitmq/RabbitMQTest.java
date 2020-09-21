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
package co.elastic.apm.agent.rabbitmq;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.jupiter.api.AfterAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class RabbitMQTest extends AbstractInstrumentationTest {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMQTest.class);

    private static final String IMAGE = "rabbitmq:3.7-management-alpine";
    private static final RabbitMQContainer container = new RabbitMQContainer(IMAGE);

    @Nullable
    private static ConnectionFactory factory;

    @Nullable
    private Connection connection;

    @BeforeClass
    public static void before() {
        container.withLogConsumer(new Slf4jLogConsumer(logger))
            .start();

        factory = new ConnectionFactory();

        factory.setHost(container.getHost());
        factory.setPort(container.getAmqpPort());
        factory.setUsername(container.getAdminUsername());
        factory.setPassword(container.getAdminPassword());
    }

    @After
    public void cleanup() throws IOException {
        if (connection != null) {
            if (connection.isOpen()) {
                logger.info("silently closing open connection id = {}", connection);
                connection.close();
            }
        }
    }

    @AfterClass
    public static void after() {
        container.close();
    }

    protected Connection createConnection() {
        if (connection != null && connection.isOpen()) {
            throw new IllegalStateException("unclosed connection");
        }
        try {
            connection = factory.newConnection();
            logger.info("created connection id = {}", connection);
            return connection;
        } catch (IOException | TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }

}
