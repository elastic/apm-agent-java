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
package co.elastic.apm.agent.cassandra;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.testutils.TestContainersUtils;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class Cassandra3InstrumentationIT extends AbstractInstrumentationTest {

    @Container
    public static GenericContainer<?> cassandra = new GenericContainer<>("cassandra:3.11")
        .withExposedPorts(9042)
        .withLogConsumer(TestContainersUtils.createSlf4jLogConsumer(Cassandra3InstrumentationIT.class))
        .withStartupTimeout(Duration.ofSeconds(120))
        .withCreateContainerCmdModifier(TestContainersUtils.withMemoryLimit(2048))
        .withEnv("HEAP_NEWSIZE", "700m")
        .withEnv("MAX_HEAP_SIZE", "1024m");
    private static Session session;
    private static Cluster cluster;
    private Transaction transaction;


    @BeforeAll
    public static void beforeClass() throws Exception {
        int cassandraPort = cassandra.getMappedPort(9042);
        cluster = Cluster.builder()
            .addContactPoint("localhost")
            .withPort(cassandraPort)
            .withoutMetrics()
            .build();
    }

    @BeforeEach
    void setUp() throws Exception {
        try (Session s = cluster.connect()) {
            s.execute("CREATE KEYSPACE IF NOT EXISTS test WITH replication = {'class':'SimpleStrategy','replication_factor':'1'};");
        }
        transaction = tracer.startRootTransaction(null).withName("transaction").activate();
        session = cluster.connect("test");
    }

    @AfterEach
    void tearDown() {
        Optional.ofNullable(transaction).ifPresent(t -> t.deactivate().end());
        Optional.ofNullable(session).ifPresent(s -> s.execute("DROP KEYSPACE test"));
        Optional.ofNullable(session).ifPresent(Session::close);
    }

    @Test
    void test() throws Exception {
        session.execute("CREATE TABLE users (id UUID, name text, PRIMARY KEY(name, id))");
        session.execute(session.prepare("INSERT INTO users (id, name) values (?, ?)").bind(UUID.randomUUID(), "alice"));
        session.executeAsync("SELECT * FROM users where name = 'alice' ALLOW FILTERING").get();

        reporter.awaitSpanCount(3);

        assertThat(reporter.getSpanByName("CREATE").getContext().getDb().getStatement())
            .isEqualTo("CREATE TABLE users (id UUID, name text, PRIMARY KEY(name, id))");

        assertThat(reporter.getSpanByName("INSERT INTO users").getContext().getDb().getStatement())
            .isEqualTo("INSERT INTO users (id, name) values (?, ?)");

        assertThat(reporter.getSpanByName("SELECT FROM users").getContext().getDb().getStatement())
            .isEqualTo("SELECT * FROM users where name = 'alice' ALLOW FILTERING");
    }

}
