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
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.rabbitmq.mock.MockConsumer;
import com.rabbitmq.client.AMQP;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

public class RabbitMQConsumerInstrumentationTest extends AbstractInstrumentationTest {

    @Test
    public void testHandleDelivery() throws IOException {
        MockConsumer mockConsumer = new MockConsumer();
        AMQP.BasicProperties.Builder builder = new AMQP.BasicProperties.Builder();
        HashMap<String, Object> headers = new HashMap<>();
        builder.headers(headers);

        mockConsumer.handleDelivery(null, null, builder.build(), "Testing APM!".getBytes());

        assertThat(getReporter().getTransactions()).hasSize(1);

        Transaction transaction = getReporter().getFirstTransaction();
        assertThat(transaction.getType()).isEqualTo("messaging");
        assertThat(transaction.getNameAsString()).isEqualTo("RabbitMQ message received");
    }

}
