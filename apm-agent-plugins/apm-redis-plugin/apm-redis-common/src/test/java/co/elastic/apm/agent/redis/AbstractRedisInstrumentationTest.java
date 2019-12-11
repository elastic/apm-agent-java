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
import org.junit.After;
import org.junit.Before;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import redis.embedded.RedisServer;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public abstract class AbstractRedisInstrumentationTest extends AbstractInstrumentationTest {
    protected RedisServer server;
    protected int redisPort;

    private static int getAvailablePort() throws IOException {
        try (ServerSocket socket = ServerSocketFactory.getDefault().createServerSocket(0, 1, InetAddress.getByName("localhost"))) {
            return socket.getLocalPort();
        }
    }

    @Before
    @BeforeEach
    public final void initRedis() throws IOException {
        redisPort = getAvailablePort();
        server = RedisServer.builder()
            .setting("bind 127.0.0.1")
            .port(redisPort)
            .build();
        server.start();
    }

    @After
    @AfterEach
    public final void stopRedis() {
        server.stop();
    }

    public void assertTransactionWithRedisSpans(String... commands) {
        await().untilAsserted(() -> assertThat(reporter.getSpans()).hasSize(commands.length));
        assertThat(reporter.getSpans().stream().map(Span::getNameAsString)).containsExactly(commands);
        assertThat(reporter.getSpans().stream().map(Span::getType).distinct()).containsExactly("db");
        assertThat(reporter.getSpans().stream().map(Span::getSubtype).distinct()).containsExactly("redis");
        assertThat(reporter.getSpans().stream().map(Span::getAction).distinct()).containsExactly("query");
        assertThat(reporter.getSpans().stream().map(Span::isExit).distinct()).containsExactly(true);
    }
}
