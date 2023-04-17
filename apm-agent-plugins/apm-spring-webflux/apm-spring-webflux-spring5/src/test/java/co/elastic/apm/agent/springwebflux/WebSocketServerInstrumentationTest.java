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
package co.elastic.apm.agent.springwebflux;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.springwebflux.testapp.GreetingWebClient;
import co.elastic.apm.agent.springwebflux.testapp.WebFluxApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class WebSocketServerInstrumentationTest extends AbstractInstrumentationTest {

    protected static WebFluxApplication.App app;
    protected GreetingWebClient client;

    @BeforeAll
    static void startApp() {
        app = WebFluxApplication.run(-1, "netty", true);
    }

    @AfterAll
    static void stopApp() {
        app.close();
    }

    @BeforeEach
    void beforeEach() {
        assertThat(reporter.getTransactions()).isEmpty();
        client = app.getClient(false); // functional/annotated does not matter for websockets
    }

    @AfterEach
    void afterEach() {
        AbstractServerInstrumentationTest.flushGcExpiry();
    }

    @Test
    void shouldIgnoreWebsockets() {
        int count = 5;

        List<String> result = client.webSocketPingPong(count);

        List<String> expected = IntStream.range(1, count + 1)
            .mapToObj(i -> String.format("pong-%d", i))
            .collect(Collectors.toList());
        assertThat(result).containsExactlyElementsOf(expected);

        reporter.assertNoTransaction(200);
        reporter.assertNoSpan(200);
    }
}
