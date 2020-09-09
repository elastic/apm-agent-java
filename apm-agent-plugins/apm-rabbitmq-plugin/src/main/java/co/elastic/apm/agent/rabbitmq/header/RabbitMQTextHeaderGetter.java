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

import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import com.rabbitmq.client.AMQP;

import javax.annotation.Nullable;

public class RabbitMQTextHeaderGetter implements TextHeaderGetter<AMQP.BasicProperties> {

    private static RabbitMQTextHeaderGetter instance;

    public static RabbitMQTextHeaderGetter getInstance() {
        if (instance == null) {
            instance = new RabbitMQTextHeaderGetter();
        }
        return instance;
    }

    private RabbitMQTextHeaderGetter() {
    }

    @Nullable
    @Override
    public String getFirstHeader(String headerName, AMQP.BasicProperties carrier) {
        if (carrier.getHeaders() != null && !carrier.getHeaders().isEmpty() && carrier.getHeaders().containsKey(headerName)) {
            Object headerValue = carrier.getHeaders().get(headerName);
            if (headerValue instanceof String) {
                return (String) headerValue;
            }
        }
        return null;
    }

    @Override
    public <S> void forEach(String headerName, AMQP.BasicProperties carrier, S state, HeaderConsumer<String, S> consumer) {
        if (carrier.getHeaders() != null && !carrier.getHeaders().isEmpty() && carrier.getHeaders().containsKey(headerName)) {
            Object headerValue = carrier.getHeaders().get(headerName);
            if (headerValue instanceof String) {
                consumer.accept((String) headerValue, state);
            }
        }
    }
}
