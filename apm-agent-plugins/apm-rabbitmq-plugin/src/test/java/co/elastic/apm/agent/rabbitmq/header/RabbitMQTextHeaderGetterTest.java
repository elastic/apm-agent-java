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
package co.elastic.apm.agent.rabbitmq.header;

import co.elastic.apm.agent.impl.transaction.HeaderGetter;
import com.rabbitmq.client.AMQP;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

public class RabbitMQTextHeaderGetterTest {

    private RabbitMQTextHeaderGetter rabbitMQTextHeaderGetter;

    @Before
    public void setUp() {
        rabbitMQTextHeaderGetter = new RabbitMQTextHeaderGetter();
    }

    @Test
    public void getFirstHeader() {
        HashMap<String, Object> headers = new HashMap<>();
        headers.put("header", "value");
        AMQP.BasicProperties basicProperties = new AMQP.BasicProperties.Builder().headers(headers).build();
        assertThat(rabbitMQTextHeaderGetter.getFirstHeader("header", basicProperties)).isEqualTo("value");
    }

    @Test
    public void forEach() {
        HashMap<String, Object> headers = new HashMap<>();
        headers.put("header", "value");
        AMQP.BasicProperties basicProperties = new AMQP.BasicProperties.Builder().headers(headers).build();

        Object stateObject = new Object();

        HeaderGetter.HeaderConsumer<String, Object> headerConsumer = new HeaderGetter.HeaderConsumer<String, Object>() {

            @Override
            public void accept(String headerValue, Object state) {
                assertThat(state).isEqualTo(stateObject);
                assertThat(headerValue).isEqualTo("value");
            }

        };

        rabbitMQTextHeaderGetter.forEach("header", basicProperties, stateObject, headerConsumer);


    }
}
