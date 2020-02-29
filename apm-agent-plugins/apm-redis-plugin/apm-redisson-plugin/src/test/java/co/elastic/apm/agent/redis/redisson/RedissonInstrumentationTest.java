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
package co.elastic.apm.agent.redis.redisson;

import co.elastic.apm.agent.impl.Scope;
import co.elastic.apm.agent.redis.AbstractRedisInstrumentationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import static org.assertj.core.api.Assertions.assertThat;

class RedissonInstrumentationTest extends AbstractRedisInstrumentationTest {

    protected RedissonClient redisson;

    @BeforeEach
    void setUp() {
        Config config = new Config();
        config.useSingleServer().setAddress("localhost:" + redisPort);
        redisson = Redisson.create(config);
    }

    @AfterEach
    void shutdown() {
        redisson.shutdown();
    }

    @Test
    void testRedission() {
        try (Scope scope = tracer.startRootTransaction(getClass().getClassLoader()).withName("transaction").activateInScope()) {
            RBucket<String> keyObject = redisson.getBucket("foo");
            keyObject.set("bar");

            assertThat(keyObject.get()).isEqualTo("bar");
        }

        assertTransactionWithRedisSpans("SET", "GET");
    }

}
