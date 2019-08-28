/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.redis;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.JedisShardInfo;
import redis.clients.jedis.ShardedJedisPool;
import redis.embedded.RedisCluster;
import redis.embedded.util.JedisUtil;

import java.util.Collections;

public abstract class AbstractRedisInstrumentationTest extends AbstractInstrumentationTest {
    protected JedisSentinelPool pool;
    protected Jedis jedis;
    private RedisCluster cluster;

    @BeforeEach
    void setup() throws Exception {
        //creates a cluster with 3 sentinels, quorum size of 2 and 3 replication groups, each with one master and one slave
        cluster = RedisCluster.builder().ephemeral().sentinelCount(3).quorumSize(2)
            .replicationGroup("master1", 1)
            .replicationGroup("master2", 1)
            .replicationGroup("master3", 1)
            .build();
        cluster.start();

        //retrieve ports on which sentinels have been started, using a simple Jedis utility class
        pool = new JedisSentinelPool("master1", JedisUtil.sentinelHosts(cluster));
        jedis = pool.getResource();
    }


    @AfterEach
    void tearDown() throws Exception {
        jedis.close();
        pool.close();
        cluster.stop();
    }
}
