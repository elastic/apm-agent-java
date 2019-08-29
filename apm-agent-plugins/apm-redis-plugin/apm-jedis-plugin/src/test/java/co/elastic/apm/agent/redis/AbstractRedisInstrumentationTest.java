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
import co.elastic.apm.agent.impl.transaction.Span;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import redis.clients.jedis.Jedis;
import redis.embedded.RedisServer;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractRedisInstrumentationTest extends AbstractInstrumentationTest {
    protected Jedis jedis;
    protected RedisServer server;

    private static int getAvailablePort() throws IOException {
        try (ServerSocket socket = ServerSocketFactory.getDefault().createServerSocket(0, 1, InetAddress.getByName("localhost"))) {
            return socket.getLocalPort();
        }
    }

    @BeforeEach
    final void initRedis() throws IOException {
        int port = getAvailablePort();
        server = RedisServer.builder()
            .setting("bind 127.0.0.1")
            .port(port)
            .build();
        server.start();

        jedis = new Jedis("localhost", port);
    }

    @AfterEach
    final void stopRedis() {
        try {
            // this method does not exist in Jedis 1
            Jedis.class.getMethod("close").invoke(jedis);
        } catch (NoSuchMethodException e) {
            // ignore, this version of redis does not support close
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
        }
        server.stop();
    }

    public void assertTransactionWithRedisSpans(String... commands) {
        assertThat(reporter.getSpans()).hasSize(2);
        assertThat(reporter.getSpans().stream().map(Span::getNameAsString)).containsExactly(commands);
        assertThat(reporter.getSpans().stream().map(Span::getType).distinct()).containsExactly("db");
        assertThat(reporter.getSpans().stream().map(Span::getSubtype).distinct()).containsExactly("redis");
        assertThat(reporter.getSpans().stream().map(Span::getAction).distinct()).containsExactly("query");
        assertThat(reporter.getSpans().stream().map(Span::isExit).distinct()).containsExactly(true);
    }
}
