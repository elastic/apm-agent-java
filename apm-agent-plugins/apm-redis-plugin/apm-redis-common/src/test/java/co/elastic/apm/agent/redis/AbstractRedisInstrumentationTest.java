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
package co.elastic.apm.agent.redis;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.After;
import org.junit.Before;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.util.List;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public abstract class AbstractRedisInstrumentationTest extends AbstractInstrumentationTest {
    protected int redisPort;
    private String expectedAddress;
    protected static GenericContainer redisContainer;

    public AbstractRedisInstrumentationTest() {
        this.expectedAddress = "localhost";
    }

    public AbstractRedisInstrumentationTest(String expectedAddress) {
        this.expectedAddress = expectedAddress;
    }

    @Before
    @BeforeEach
    public final void initRedis() throws IOException {
        redisContainer = new GenericContainer("redis:6.2.6").withExposedPorts(6379);
        redisContainer.start();
        redisContainer.waitingFor(Wait.forLogMessage("Started!", 1));
        redisPort = redisContainer.getFirstMappedPort();
        tracer.startRootTransaction(null).activate();
    }

    @After
    @AfterEach
    public final void stopRedis() {
        Transaction transaction = tracer.currentTransaction();
        if (transaction != null) {
            transaction.deactivate().end();
        }
        redisContainer.stop();
    }

    public void assertTransactionWithRedisSpans(String... commands) {
        await().untilAsserted(() -> assertThat(reporter.getSpans()).hasSize(commands.length));
        assertThat(reporter.getSpans().stream().map(Span::getNameAsString)).containsExactly(commands);
        assertThat(reporter.getSpans().stream().map(Span::getType).distinct()).containsExactly("db");
        assertThat(reporter.getSpans().stream().map(Span::getSubtype).distinct()).containsExactly("redis");
        assertThat(reporter.getSpans().stream().map(Span::getAction).distinct()).containsExactly("query");
        assertThat(reporter.getSpans().stream().map(Span::isExit).distinct()).containsExactly(true);
        assertThat(reporter.getSpans().stream().map(Span::getOutcome).distinct()).containsExactly(Outcome.SUCCESS);
        verifyDestinationDetails(reporter.getSpans());
    }

    private void verifyDestinationDetails(List<Span> spanList) {
        for (Span span : spanList) {
            Destination destination = span.getContext().getDestination();
            if (destinationAddressSupported()) {
                assertThat(destination.getAddress().toString()).isEqualTo(expectedAddress);
                assertThat(destination.getPort()).isEqualTo(redisPort);
            }

            assertThat(span.getContext().getServiceTarget())
                .hasType("redis")
                .hasNoName()
                .hasDestinationResource("redis");
        }
    }

    protected boolean destinationAddressSupported() {
        return true;
    }
}
