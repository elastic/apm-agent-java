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
package co.elastic.apm.agent.redis.lettuce;

import co.elastic.apm.agent.redis.AbstractRedisInstrumentationTest;
import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.api.StatefulRedisConnection;
import com.lambdaworks.redis.api.async.RedisAsyncCommands;
import com.lambdaworks.redis.api.sync.RedisCommands;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Lettuce4InstrumentationTest extends AbstractRedisInstrumentationTest {

    private RedisClient client;
    private StatefulRedisConnection<String, String> connection;

    @Before
    public void setUpLettuce() {
        client = RedisClient.create("redis://localhost:" + redisPort);
        connection = client.connect();
        reporter.disableCheckDestinationAddress();
    }

    @Test
    public void testClusterCommand() {
        RedisCommands<String, String> sync = connection.sync();
        sync.set("foo", "bar");
        assertThat(sync.get("foo")).isEqualTo("bar");
        assertTransactionWithRedisSpans("SET", "GET");
    }

    @Test
    public void testSyncLettuce() {
        RedisCommands<String, String> sync = connection.sync();
        sync.set("foo", "bar");
        assertThat(sync.get("foo")).isEqualTo("bar");
        assertTransactionWithRedisSpans("SET", "GET");
    }

    @Test
    public void testAsyncLettuce() throws Exception {
        RedisAsyncCommands<String, String> async = connection.async();
        async.set("foo", "bar").get();
        assertThat(async.get("foo").get()).isEqualTo("bar");
        assertTransactionWithRedisSpans("SET", "GET");
    }

    @After
    public void tearDownLettuce() {
        connection.close();
    }

    @Override
    protected boolean destinationAddressSupported() {
        return false;
    }
}
