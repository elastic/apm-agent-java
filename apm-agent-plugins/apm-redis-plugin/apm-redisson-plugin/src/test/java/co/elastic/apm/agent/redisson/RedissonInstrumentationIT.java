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
package co.elastic.apm.agent.redisson;

import co.elastic.apm.agent.redis.AbstractRedisInstrumentationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBatch;
import org.redisson.api.RBucket;
import org.redisson.api.RList;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedissonInstrumentationIT extends AbstractRedisInstrumentationTest {

    protected RedissonClient redisson;

    public RedissonInstrumentationIT() {
        super("127.0.0.1");
    }

    @BeforeEach
    void setUp() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://localhost:" + redisPort);
        redisson = Redisson.create(config);
    }

    @AfterEach
    void shutdown() {
        redisson.shutdown();
    }

    @Test
    void testString() {
        RBucket<String> keyObject = redisson.getBucket("foo");
        keyObject.set("bar");

        assertThat(keyObject.get()).isEqualTo("bar");
        assertTransactionWithRedisSpans("SET", "GET");
    }

    @Test
    void testBatch() {
        RBatch batch = redisson.createBatch();
        batch.getBucket("batch1").setAsync("v1");
        batch.getBucket("batch2").setAsync("v2");
        batch.execute();

        assertThat(redisson.getBucket("batch1").get()).isEqualTo("v1");
        assertThat(redisson.getBucket("batch2").get()).isEqualTo("v2");

        assertTransactionWithRedisSpans("SET... [bulk]", "GET", "GET");
    }

    @Test
    void testList() {
        RList<String> strings = redisson.getList("list1");
        strings.add("a");

        assertThat(strings.size()).isEqualTo(1);
        assertThat(strings.get(0)).isEqualTo("a");

        assertTransactionWithRedisSpans("RPUSH", "LLEN", "LINDEX");
    }

    @Test
    void testHash() {
        RMap<String, String> rMap = redisson.getMap("map1");
        rMap.put("key1", "value1");

        assertThat(rMap.get("key1")).isEqualTo("value1");

        assertTransactionWithRedisSpans("EVAL", "HGET");
    }

    @Test
    void testSet() {
        RSet<String> rSet = redisson.getSet("set1");
        rSet.add("s1");

        assertThat(rSet.contains("s1")).isTrue();

        assertTransactionWithRedisSpans("SADD", "SISMEMBER");
    }

    @Test
    void testSortedSet() {
        Map<String, Double> scores = new HashMap<>();
        scores.put("u1", 1.0d);
        scores.put("u2", 3.0d);
        scores.put("u3", 0.0d);
        RScoredSortedSet<String> sortSet = redisson.getScoredSortedSet("sort_set1");
        sortSet.addAll(scores);

        assertThat(sortSet.rank("u1")).isEqualTo(1);
        assertThat(sortSet.rank("u3")).isEqualTo(0);

        assertTransactionWithRedisSpans("ZADD", "ZRANK", "ZRANK");
    }

    @Test
    void testAtomicLong() {
        RAtomicLong atomicLong = redisson.getAtomicLong("AtomicLong");
        atomicLong.incrementAndGet();

        assertThat(atomicLong.get()).isEqualTo(1);

        assertTransactionWithRedisSpans("INCR", "GET");
    }

    /**
     * redisson use EVAL to implement distributed locks
     */
    @Test
    void testLock() {
        RLock lock = redisson.getLock("lock");
        lock.lock();
        lock.unlock();

        assertTransactionWithRedisSpans("EVAL... [bulk]", "EVAL... [bulk]");
    }
}
