/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018-2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.jdbc;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Db;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uses plain connections without a connection pool
 */
public abstract class AbstractJdbcInstrumentationTest extends AbstractInstrumentationTest {
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();
    private static final String PREPARED_STATEMENT_SQL = "SELECT * FROM ELASTIC_APM WHERE FOO=?";
    private static final long PREPARED_STMT_TIMEOUT = 10000;

    private final String expectedSpanType;
    private Connection connection;
    @Nullable
    private PreparedStatement preparedStatement;
    private final Transaction transaction;

    AbstractJdbcInstrumentationTest(Connection connection, String expectedSpanType) throws Exception {
        this.connection = connection;
        this.expectedSpanType = expectedSpanType;
        connection.createStatement().execute("CREATE TABLE ELASTIC_APM (FOO INT, BAR VARCHAR(255))");
        connection.createStatement().execute("INSERT INTO ELASTIC_APM (FOO, BAR) VALUES (1, 'APM')");
        transaction = tracer.startTransaction().activate();
        transaction.setName("transaction");
        transaction.withType("request");
        transaction.withResult("success");
    }

    @Before
    public void setUp() throws Exception {
        preparedStatement = EXECUTOR_SERVICE.submit(new Callable<PreparedStatement>() {
            public PreparedStatement call() throws Exception {
                return connection.prepareStatement(PREPARED_STATEMENT_SQL);
            }
        }).get(PREPARED_STMT_TIMEOUT, TimeUnit.MILLISECONDS);
    }

    @After
    public void tearDown() throws SQLException {
        if (preparedStatement != null) {
            preparedStatement.close();
        }
        preparedStatement = null;
        connection.createStatement().execute("DROP TABLE ELASTIC_APM");
        connection.close();
        transaction.deactivate().end();
    }

    // execute in a single test because creating a new connection is expensive,
    // as it spins up another docker container
    @Test
    public void test() throws SQLException {
        testStatement();
        testPreparedStatement();
    }

    private void testStatement() throws SQLException {
        final String sql = "SELECT * FROM ELASTIC_APM WHERE FOO=1";
        ResultSet resultSet = connection.createStatement().executeQuery(sql);
        assertSpanRecorded(resultSet, sql);
    }

    private void testPreparedStatement() throws SQLException {
        if (preparedStatement != null) {
            preparedStatement.setInt(1, 1);
            ResultSet resultSet = preparedStatement.executeQuery();
            assertSpanRecorded(resultSet, PREPARED_STATEMENT_SQL);

            // test a second recording with the same statement object
            reporter.reset();
            resultSet = preparedStatement.executeQuery();
            assertSpanRecorded(resultSet, PREPARED_STATEMENT_SQL);
        }
    }

    private void assertSpanRecorded(ResultSet resultSet, String sql) throws SQLException {
        assertThat(resultSet.next()).isTrue();
        assertThat(resultSet.getInt("foo")).isEqualTo(1);
        assertThat(resultSet.getString("BAR")).isEqualTo("APM");
        assertThat(reporter.getSpans()).hasSize(1);
        Span jdbcSpan = reporter.getFirstSpan();
        assertThat(jdbcSpan.getName().toString()).isEqualTo("SELECT");
        assertThat(jdbcSpan.getType()).isEqualToIgnoringCase(expectedSpanType);
        Db db = jdbcSpan.getContext().getDb();
        assertThat(db.getStatement()).isEqualTo(sql);
        assertThat(db.getUser()).isEqualToIgnoringCase(connection.getMetaData().getUserName());
        assertThat(db.getType()).isEqualToIgnoringCase("sql");
        reporter.reset();
    }
}
