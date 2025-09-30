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
package co.elastic.apm.agent.lettuce;

import co.elastic.apm.agent.redis.AbstractRedisInstrumentationTest;
import io.lettuce.core.LettuceFutures;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class Lettuce5InstrumentationIT extends AbstractRedisInstrumentationTest {

    private StatefulRedisConnection<String, String> connection;

    @Before
    public void setUpLettuce() {
        RedisClient client = RedisClient.create(RedisURI.create("localhost", redisPort));
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

    @Test
    public void testBatchedLettuce() throws Exception {
        RedisAsyncCommands<String, String> async = connection.async();
        async.set("foo", "bar").get();
        connection.setAutoFlushCommands(false);
        List<RedisFuture<String>> futures = List.of(async.get("foo"), async.get("foo"));
        connection.flushCommands();
        LettuceFutures.awaitAll(Duration.ofSeconds(5), futures.toArray(new RedisFuture[0]));
        assertTransactionWithRedisSpans("SET", "GET", "GET");
    }

    @Test
    public void testReactiveLettuce() {
        RedisReactiveCommands<String, String> async = connection.reactive();
        async.set("foo", "bar").block();
        assertThat(async.get("foo").block()).isEqualTo("bar");
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
