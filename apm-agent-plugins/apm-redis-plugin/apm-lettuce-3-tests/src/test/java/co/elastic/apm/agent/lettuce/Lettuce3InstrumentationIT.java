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
import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.RedisConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

public class Lettuce3InstrumentationIT extends AbstractRedisInstrumentationTest {

    private RedisClient client;
    private RedisConnection<String, String> connection;

    @Before
    public void setUpLettuce() {
        client = new RedisClient("localhost", redisPort);
        connection = client.connect();
        reporter.disableCheckDestinationAddress();
    }

    @Test
    public void testSyncLettuce() {
        connection.set("foo", "bar");
        assertThat(connection.get("foo")).isEqualTo("bar");
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
