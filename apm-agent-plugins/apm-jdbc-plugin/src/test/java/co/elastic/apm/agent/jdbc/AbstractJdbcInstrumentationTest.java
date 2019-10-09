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
package co.elastic.apm.agent.jdbc;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Db;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.jdbc.signature.SignatureParser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static co.elastic.apm.agent.jdbc.helper.JdbcHelperImpl.DB_SPAN_ACTION;
import static co.elastic.apm.agent.jdbc.helper.JdbcHelperImpl.DB_SPAN_TYPE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uses plain connections without a connection pool
 */
public abstract class AbstractJdbcInstrumentationTest extends AbstractInstrumentationTest {
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();
    private static final String PREPARED_STATEMENT_SQL = "SELECT * FROM ELASTIC_APM WHERE FOO=?";
    private static final long PREPARED_STMT_TIMEOUT = 10000;

    private final String expectedDbVendor;
    private Connection connection;
    @Nullable
    private PreparedStatement preparedStatement;
    private final Transaction transaction;

    private SignatureParser signatureParser;

    AbstractJdbcInstrumentationTest(Connection connection, String expectedDbVendor) throws Exception {
        this.connection = connection;
        this.expectedDbVendor = expectedDbVendor;
        connection.createStatement().execute("CREATE TABLE ELASTIC_APM (FOO INT, BAR VARCHAR(255))");
        connection.createStatement().execute("INSERT INTO ELASTIC_APM (FOO, BAR) VALUES (1, 'APM')");
        transaction = tracer.startTransaction(TraceContext.asRoot(), null, null).activate();
        transaction.withName("transaction");
        transaction.withType("request");
        transaction.withResultIfUnset("success");
        signatureParser = new SignatureParser();
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
        transaction.deactivate().end();
        connection.createStatement().execute("DROP TABLE ELASTIC_APM");
        connection.close();
    }

    // execute in a single test because creating a new connection is expensive,
    // as it spins up another docker container
    @Test
    public void test() throws SQLException {
        testStatement();
//        testBatch();
//        testPreparedStatement();
//        testBatchPreparedStatement();
    }

    private void testStatement() throws SQLException {
        final String sql = "SELECT * FROM ELASTIC_APM WHERE FOO=1";
        ResultSet resultSet = connection.createStatement().executeQuery(sql);
        assertQuerySucceededAndSpanRecorded(resultSet, sql, false);
    }

    private void testBatch() throws SQLException {
        final String insert = "INSERT INTO ELASTIC_APM (FOO, BAR) VALUES (2, 'TEST')";
        final String delete = "DELETE FROM ELASTIC_APM WHERE FOO=2";
        Statement statement = connection.createStatement();
        statement.addBatch(insert);
        statement.addBatch(delete);
        int[] updates = statement.executeBatch();
        assertThat(updates).hasSize(2);
        assertThat(updates[0]).isEqualTo(1);
        assertThat(updates[1]).isEqualTo(1);
        assertSpanRecorded(insert, false);
    }

    private void testPreparedStatement() throws SQLException {
        if (preparedStatement != null) {
            preparedStatement.setInt(1, 1);
            ResultSet resultSet = preparedStatement.executeQuery();
            assertQuerySucceededAndSpanRecorded(resultSet, PREPARED_STATEMENT_SQL, true);

            // test a second recording with the same statement object
            reporter.reset();
            resultSet = preparedStatement.executeQuery();
            assertQuerySucceededAndSpanRecorded(resultSet, PREPARED_STATEMENT_SQL, true);
        }
    }

    private void testBatchPreparedStatement() throws SQLException {
        final String query = "INSERT INTO ELASTIC_APM (FOO, BAR) VALUES (?, ?)";
        PreparedStatement statement = connection.prepareStatement(query);
        statement.setInt(1, 3);
        statement.setString(2, "TEST#3");
        statement.addBatch();
        statement.setInt(1, 4);
        statement.setString(2, "TEST#4");
        statement.addBatch();
        int[] updates = statement.executeBatch();
        assertThat(updates).hasSize(2);
        assertSpanRecorded(query, false);
    }

    private void assertQuerySucceededAndSpanRecorded(ResultSet resultSet, String rawSql, boolean preparedStatement) throws SQLException {
        assertThat(resultSet.next()).isTrue();
        assertThat(resultSet.getInt("foo")).isEqualTo(1);
        assertThat(resultSet.getString("BAR")).isEqualTo("APM");
        assertSpanRecorded(rawSql, preparedStatement);
    }

    private void assertSpanRecorded(String rawSql, boolean preparedStatement) throws SQLException {
        assertThat(reporter.getSpans()).hasSize(1);
        Span jdbcSpan = reporter.getFirstSpan();
        StringBuilder processedSql = new StringBuilder();
        signatureParser.querySignature(rawSql, processedSql, preparedStatement);
        assertThat(jdbcSpan.getNameAsString()).isEqualTo(processedSql.toString());
        assertThat(jdbcSpan.getType()).isEqualTo(DB_SPAN_TYPE);
        assertThat(jdbcSpan.getSubtype()).isEqualTo(expectedDbVendor);
        assertThat(jdbcSpan.getAction()).isEqualTo(DB_SPAN_ACTION);
        Db db = jdbcSpan.getContext().getDb();
        assertThat(db.getStatement()).isEqualTo(rawSql);
        assertThat(db.getUser()).isEqualToIgnoringCase(connection.getMetaData().getUserName());
        assertThat(db.getType()).isEqualToIgnoringCase("sql");
    }
}
