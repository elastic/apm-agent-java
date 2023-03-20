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
package co.elastic.apm.agent.rabbitmq.header;

import co.elastic.apm.agent.impl.transaction.AbstractTextHeaderGetterTest;
import co.elastic.apm.agent.tracer.dispatch.HeaderGetter;
import com.rabbitmq.client.AMQP;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class RabbitMQTextHeaderGetterTest extends AbstractTextHeaderGetterTest<RabbitMQTextHeaderGetter,AMQP.BasicProperties> {

    private static RabbitMQTextHeaderGetter rabbitMQTextHeaderGetter;

    @BeforeAll
    static void setUp() {
        rabbitMQTextHeaderGetter = RabbitMQTextHeaderGetter.INSTANCE;
    }

    @Test
    void getFirstHeader() {
        getFirstHeader("value", "value");
        getFirstHeader(null, null);

        // use toString as fallback
        ObjectHeader header = new ObjectHeader();
        getFirstHeader(header, header.toString());
    }

    @Override
    protected RabbitMQTextHeaderGetter createTextHeaderGetter() {
        return RabbitMQTextHeaderGetter.INSTANCE;
    }

    @Override
    protected AMQP.BasicProperties createCarrier(Map<String, List<String>> map) {
        Map<String,Object> headers = new HashMap<>();
        map.forEach((k,values)-> headers.put(k,values.get(0)));
        return new AMQP.BasicProperties.Builder().headers(headers).build();
    }

    @Test
    @Override
    @Disabled
    public void multipleValueHeader() {
        // disabled as multiple-value headers are not supported
    }


    private static class ObjectHeader {
        @Override
        public String toString() {
            return "hello";
        }
    }

    private void getFirstHeader(@Nullable Object value, @Nullable String expected) {
        HashMap<String, Object> headers = new HashMap<>();
        headers.put("header", value);
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().headers(headers).build();
        assertThat(rabbitMQTextHeaderGetter.getFirstHeader("header", properties))
            .isEqualTo(expected);
    }

    @Test
    void forEach() throws InterruptedException {
        forEach("value", "value");
        forEach(null, null);

        // use toString as fallback
        ObjectHeader header = new ObjectHeader();
        forEach(header, header.toString());
    }

    private void forEach(@Nullable Object value, @Nullable String expected) throws InterruptedException {
        HashMap<String, Object> headers = new HashMap<>();
        headers.put("header", value);
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().headers(headers).build();

        Object stateObject = new Object();
        CountDownLatch end = new CountDownLatch(1);

        HeaderGetter.HeaderConsumer<String, Object> headerConsumer = (headerValue, state) -> {
            assertThat(state).isSameAs(stateObject);
            assertThat(headerValue).isEqualTo(expected);
            end.countDown();
        };

        rabbitMQTextHeaderGetter.forEach("header", properties, stateObject, headerConsumer);

        // ensure consumer has been properly called
        end.await(1, TimeUnit.SECONDS);
    }
}
