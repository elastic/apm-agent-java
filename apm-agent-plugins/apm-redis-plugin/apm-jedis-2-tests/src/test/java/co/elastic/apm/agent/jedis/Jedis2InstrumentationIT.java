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
package co.elastic.apm.agent.jedis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.BinaryJedis;
import redis.clients.jedis.JedisShardInfo;
import redis.clients.jedis.ShardedJedis;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Jedis2InstrumentationIT extends Jedis1InstrumentationIT {

    private ShardedJedis shardedJedis;
    private BinaryJedis binaryJedis;

    @BeforeEach
    void setUp() {
        shardedJedis = new ShardedJedis(List.of(new JedisShardInfo("localhost", redisPort)));
        binaryJedis = new BinaryJedis("localhost", redisPort);
    }

    @AfterEach
    void tearDown() {
        shardedJedis.close();
        binaryJedis.close();
    }

    @Test
    void testShardedJedis() {
        shardedJedis.set("foo", "bar");
        assertThat(shardedJedis.get("foo".getBytes())).isEqualTo("bar".getBytes());

        assertTransactionWithRedisSpans("SET", "GET");
    }

    @Test
    void testBinaryJedis() {
        binaryJedis.set("foo".getBytes(), "bar".getBytes());
        assertThat(binaryJedis.get("foo".getBytes())).isEqualTo("bar".getBytes());

        assertTransactionWithRedisSpans("SET", "GET");
    }
}
