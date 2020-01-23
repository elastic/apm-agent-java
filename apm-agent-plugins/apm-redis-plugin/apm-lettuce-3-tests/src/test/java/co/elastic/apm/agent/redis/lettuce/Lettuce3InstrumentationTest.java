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
package co.elastic.apm.agent.redis.lettuce;

import co.elastic.apm.agent.impl.Scope;
import co.elastic.apm.agent.redis.AbstractRedisInstrumentationTest;
import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.RedisConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

public class Lettuce3InstrumentationTest extends AbstractRedisInstrumentationTest {

    private RedisClient client;
    private RedisConnection<String, String> connection;

    @Before
    public void setUpLettuce() {
        client = new RedisClient("localhost", redisPort);
        connection = client.connect();
        reporter.disableDestinationAddressCheck();
    }

    @Test
    public void testSyncLettuce() {
        try (Scope scope = tracer.startRootTransaction(getClass().getClassLoader()).withName("transaction").activateInScope()) {
            connection.set("foo", "bar");
            assertThat(connection.get("foo")).isEqualTo("bar");
        }
        assertTransactionWithRedisSpans("SET", "GET");
    }

    @After
    public void tearDownLettuce() throws ExecutionException, InterruptedException {
        connection.close();
    }

    @Override
    protected boolean destinationAddressSupported() {
        return false;
    }
}
