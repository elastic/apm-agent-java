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
package co.elastic.apm.agent.jdbc;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.db.signature.SignatureParser;
import co.elastic.apm.agent.impl.context.Db;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.jdbc.helper.JdbcGlobalState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static co.elastic.apm.agent.jdbc.helper.JdbcHelper.DB_SPAN_ACTION;
import static co.elastic.apm.agent.jdbc.helper.JdbcHelper.DB_SPAN_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

/**
 * Uses plain connections without a connection pool
 */
public abstract class AbstractJdbcInstrumentationTest extends AbstractInstrumentationTest {
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();
    private static final String PREPARED_STATEMENT_SQL = "SELECT * FROM ELASTIC_APM WHERE FOO=?";
    private static final String UPDATE_PREPARED_STATEMENT_SQL = "UPDATE ELASTIC_APM SET BAR=? WHERE FOO=11";
    private static final long PREPARED_STMT_TIMEOUT = 10000;

    private final String expectedDbVendor;
    private Connection connection;
    @Nullable
    private PreparedStatement preparedStatement;
    @Nullable
    private PreparedStatement updatePreparedStatement;
    private final Transaction transaction;
    private final SignatureParser signatureParser;

    AbstractJdbcInstrumentationTest(Connection connection, String expectedDbVendor) throws Exception {
        this.connection = connection;
        this.expectedDbVendor = expectedDbVendor;
        connection.createStatement().execute("CREATE TABLE ELASTIC_APM (FOO INT NOT NULL, BAR VARCHAR(255))");
        connection.createStatement().execute("ALTER TABLE ELASTIC_APM ADD PRIMARY KEY (FOO)");
        transaction = startTestRootTransaction("jdbc-test");
        signatureParser = new SignatureParser();
    }

    @Before
    public void setUp() throws Exception {
        preparedStatement = EXECUTOR_SERVICE.submit(new Callable<PreparedStatement>() {
            public PreparedStatement call() throws Exception {
                return connection.prepareStatement(PREPARED_STATEMENT_SQL);
            }
        }).get(PREPARED_STMT_TIMEOUT, TimeUnit.MILLISECONDS);
        updatePreparedStatement = EXECUTOR_SERVICE.submit(new Callable<PreparedStatement>() {
            public PreparedStatement call() throws Exception {
                return connection.prepareStatement(UPDATE_PREPARED_STATEMENT_SQL);
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
        executeTest(this::testStatement);
        executeTest(() -> testUpdateStatement(false));
        executeTest(() -> testUpdateStatement(true));
        executeTest(this::testStatementNotSupportingConnection);
        executeTest(this::testStatementWithoutConnectionMetadata);

        executeTest(() -> testUpdate(Statement::executeUpdate));
        executeTest(() -> testUpdate(Statement::executeLargeUpdate));

        executeTest(() -> testPreparedStatementUpdate(PreparedStatement::executeUpdate));
        executeTest(() -> testPreparedStatementUpdate(PreparedStatement::executeLargeUpdate));

        executeTest(() -> testBatch(false));
        executeTest(() -> testBatch(true));

        executeTest(this::testPreparedStatement);
        executeTest(this::testUpdatePreparedStatement);

        executeTest(() -> testBatchPreparedStatement(false));
        executeTest(() -> testBatchPreparedStatement(true));

        executeTest(this::testMultipleRowsModifiedStatement);
    }

    /**
     * Wraps JDBC code that may throw {@link SQLException} and properly resets reporter after test execution
     *
     * @param task test task
     */
    private void executeTest(JdbcTask task) throws SQLException {
        connection.createStatement().execute("INSERT INTO ELASTIC_APM (FOO, BAR) VALUES (1, 'APM')");
        connection.createStatement().execute("INSERT INTO ELASTIC_APM (FOO, BAR) VALUES (11, 'BEFORE')");
        reporter.reset();
        try {
            task.execute();
        } catch (SQLException e) {
            fail("unexpected exception", e);
        } finally {
            try {
                connection.createStatement().execute("DELETE FROM ELASTIC_APM");
            } catch (SQLException e) {
                e.printStackTrace();
            }
            // reset reporter is important otherwise one test may pollute results of the following test
            reporter.reset();

            // clear internal jdbc helper required due to metadata caching and global state about unsupported
            // JDBC driver features (based on classes instances)
            JdbcGlobalState.clearInternalStorage();
        }
    }

    private interface JdbcTask {
        void execute() throws SQLException;
    }

    private void testStatement() throws SQLException {
        final String sql = "SELECT * FROM ELASTIC_APM WHERE FOO=1";
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(sql);

        assertThat(resultSet.next()).isTrue();
        assertThat(resultSet.getInt("foo")).isEqualTo(1);
        assertThat(resultSet.getString("BAR")).isEqualTo("APM");

        Span span = assertSpanRecorded(sql, false, -1);
        assertThat(span.getOutcome()).isEqualTo(Outcome.SUCCESS);
    }

    private void testUpdateStatement(boolean executeUpdate) throws SQLException {
        final String sql = "UPDATE ELASTIC_APM SET BAR='AFTER' WHERE FOO=11";
        Statement statement = connection.createStatement();
        int expectedAffectedRows;
        if (executeUpdate) {
            statement.executeUpdate(sql);
            // executeUpdate allows to capture affected rows without side-effects
            expectedAffectedRows = 1;
        } else {
            boolean isResultSet = statement.execute(sql);
            assertThat(isResultSet).isFalse();
            expectedAffectedRows = -1;
        }

        assertSpanRecorded(sql, false, expectedAffectedRows);
    }

    private void testStatementNotSupportingConnection() throws SQLException {
        TestStatement statement = new TestStatement(connection.createStatement());
        statement.setGetConnectionSupported(false);

        checkWithoutConnectionMetadata(statement, statement::getUnsupportedThrownCount);
    }

    private void testStatementWithoutConnectionMetadata() throws SQLException {
        TestConnection testConnection = new TestConnection(connection);
        testConnection.setGetMetadataSupported(false);
        TestStatement statement = new TestStatement(testConnection.createStatement());

        assertThat(statement.getConnection()).isSameAs(testConnection);

        checkWithoutConnectionMetadata(statement, testConnection::getUnsupportedThrownCount);
    }

    private interface ThrownCountCheck {
        int getThrownCount();
    }

    private void checkWithoutConnectionMetadata(TestStatement statement, ThrownCountCheck check) throws SQLException {
        assertThat(check.getThrownCount()).isZero();

        final String sql = "UPDATE ELASTIC_APM SET BAR='AFTER1' WHERE FOO=11";
        boolean isResultSet = statement.execute(sql);

        assertThat(check.getThrownCount()).isEqualTo(1);
        assertThat(isResultSet).isFalse();

        assertSpanRecordedWithoutConnection(sql, false, -1);

        // try to execute statement again, should not throw again
        statement.execute(sql);

        assertThat(check.getThrownCount())
            .describedAs("unsupported exception should only be thrown once")
            .isEqualTo(1);
    }


    private void testBatch(boolean isLargeBatch) throws SQLException {
        final String insert = "INSERT INTO ELASTIC_APM (FOO, BAR) VALUES (2, 'TEST')";
        final String delete = "DELETE FROM ELASTIC_APM WHERE FOO=2";
        Statement statement = connection.createStatement();
        statement.addBatch(insert);
        statement.addBatch(delete);
        long[] updates = null;
        if (isLargeBatch) {
            boolean supported = executePotentiallyUnsupportedFeature(statement::executeLargeBatch);
            if (!supported) {
                // feature not supported, just ignore test
                return;
            }
        } else {
            updates = toLongArray(statement.executeBatch());
        }

        boolean nullForLargeBatch = isKnownDatabase("MySQL", "5.7");
        nullForLargeBatch |= isKnownDatabase("MySQL", "10."); // mariadb
        nullForLargeBatch |= isKnownDatabase("Microsoft SQL Server", "14.");
        nullForLargeBatch |= isKnownDatabase("Oracle", "");
        if (isLargeBatch && nullForLargeBatch) {
            // we might actually test for a bug or driver implementation detail here
            assertThat(updates).isNull();
        } else {
            assertThat(updates).containsExactly(1, 1);
        }

        // note: in that case, Statement.getUpdateCount() does not return the sum
        // of the returned array values.
        assertSpanRecorded(insert, false, 2);
    }

    private interface StatementExecutor<T> {
        T withStatement(Statement s, String sql) throws SQLException;
    }

    private void testUpdate(StatementExecutor<Number> statementConsumer) throws SQLException {
        Statement statement = connection.createStatement();
        String insert = "INSERT INTO ELASTIC_APM (FOO, BAR) VALUES (42, 'TEST')";

        boolean supported = executePotentiallyUnsupportedFeature(() -> assertThat(statementConsumer.withStatement(statement, insert).intValue()).isEqualTo(1));
        if (!supported) {
            // feature not supported, just ignore test
            return;
        }
        assertSpanRecorded(insert, false, 1);
        reporter.reset();
        // unique key violation
        assertThatThrownBy(() -> statementConsumer.withStatement(statement, insert)).isInstanceOf(SQLException.class);
        Span span = assertSpanRecorded(insert, false, -1);
        assertThat(span.getOutcome()).isEqualTo(Outcome.FAILURE);
    }

    private interface PreparedStatementExecutor<T> {
        T withStatement(PreparedStatement s) throws SQLException;
    }

    private void testPreparedStatementUpdate(PreparedStatementExecutor<Number> statementConsumer) throws SQLException {
        final String insert = "INSERT INTO ELASTIC_APM (FOO, BAR) VALUES (?, 'TEST')";

        PreparedStatement statement = connection.prepareStatement(insert);
        statement.setInt(1, 42);

        boolean supported = executePotentiallyUnsupportedFeature(() -> statementConsumer.withStatement(statement));
        if (!supported) {
            // feature not supported, just ignore test
            return;
        }
        assertSpanRecorded(insert, false, 1);
        reporter.reset();
        // unique key violation
        assertThatThrownBy(() -> statementConsumer.withStatement(statement)).isInstanceOf(SQLException.class);
        Span span = assertSpanRecorded(insert, false, -1);
        assertThat(span.getOutcome()).isEqualTo(Outcome.FAILURE);
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

    private void testUpdatePreparedStatement() throws SQLException {
        if (updatePreparedStatement != null) {
            updatePreparedStatement.setString(1, "UPDATED1");
            updatePreparedStatement.execute();
            assertSpanRecorded(UPDATE_PREPARED_STATEMENT_SQL, true, -1);
        }
    }

    private void testBatchPreparedStatement(boolean isLargeBatch) throws SQLException {
        final String query = "INSERT INTO ELASTIC_APM (FOO, BAR) VALUES (?, ?)";
        PreparedStatement statement = connection.prepareStatement(query);
        statement.setInt(1, 3);
        statement.setString(2, "TEST#3");
        statement.addBatch();
        statement.setInt(1, 4);
        statement.setString(2, "TEST#4");
        statement.addBatch();

        final long[] updates = new long[2];
        if (isLargeBatch) {
            boolean supported = executePotentiallyUnsupportedFeature(() -> {
                // definitely not pretty to (ab)use side-effects in lambda, but acceptable for test code
                long[] batchUpdates = statement.executeLargeBatch();
                System.arraycopy(batchUpdates, 0, updates, 0, batchUpdates.length);
            });
            if (!supported) {
                // ignore unsupported feature
                return;
            }
        } else {
            long batchUpdates[] = toLongArray(statement.executeBatch());
            System.arraycopy(batchUpdates, 0, updates, 0, batchUpdates.length);
        }

        long expectedAffected = 2;
        if (isKnownDatabase("Oracle", "")) {
            // for an unknown reason Oracle express have unexpected but somehow consistent behavior here
            assertThat(updates).containsExactly(-2, -2);
            expectedAffected = -4;
        } else {
            assertThat(updates).containsExactly(1, 1);
        }

        assertSpanRecorded(query, false, expectedAffected);
    }

    private void testMultipleRowsModifiedStatement() throws SQLException {
        String insert = "INSERT INTO ELASTIC_APM (FOO, BAR) VALUES (3, 'TEST')";
        Statement statement = connection.createStatement();
        statement.execute(insert);

        assertThat(reporter.getSpans()).hasSize(1);
        Db db = reporter.getFirstSpan().getContext().getDb();
        assertThat(db.getStatement()).isEqualTo(insert);
    }

    private void assertQuerySucceededAndSpanRecorded(ResultSet resultSet, String rawSql, boolean preparedStatement) throws SQLException {
        assertThat(resultSet.next()).isTrue();
        assertThat(resultSet.getInt("foo")).isEqualTo(1);
        assertThat(resultSet.getString("BAR")).isEqualTo("APM");

        // this method is only called with select statements, thus no affected rows
        assertSpanRecorded(rawSql, preparedStatement, -1);
    }

    private Span assertSpanRecorded(String rawSql, boolean preparedStatement, long expectedAffectedRows) throws SQLException {
        assertThat(reporter.getSpans())
            .describedAs("one span is expected")
            .hasSize(1);
        Span span = reporter.getFirstSpan();
        StringBuilder processedSql = new StringBuilder();
        signatureParser.querySignature(rawSql, processedSql, preparedStatement);
        assertThat(span.getNameAsString()).isEqualTo(processedSql.toString());
        assertThat(span.getType()).isEqualTo(DB_SPAN_TYPE);
        assertThat(span.getSubtype()).isEqualTo(expectedDbVendor);
        assertThat(span.getAction()).isEqualTo(DB_SPAN_ACTION);

        Db db = span.getContext().getDb();
        assertThat(db.getStatement()).isEqualTo(rawSql);
        DatabaseMetaData metaData = connection.getMetaData();
        assertThat(db.getInstance()).isEqualToIgnoringCase(connection.getCatalog());
        assertThat(db.getUser()).isEqualToIgnoringCase(metaData.getUserName());
        assertThat(db.getType()).isEqualToIgnoringCase("sql");

        assertThat(db.getAffectedRowsCount())
            .describedAs("unexpected affected rows count for statement %s", rawSql)
            .isEqualTo(expectedAffectedRows);

        Destination destination = span.getContext().getDestination();
        assertThat(destination.getAddress().toString()).isEqualTo("localhost");
        if (expectedDbVendor.equals("h2")) {
            assertThat(destination.getPort()).isEqualTo(-1);
        } else {
            assertThat(destination.getPort()).isGreaterThan(0);
        }

        Destination.Service service = destination.getService();
        assertThat(service.getName().toString()).isEqualTo(expectedDbVendor);
        assertThat(service.getResource().toString()).isEqualTo(expectedDbVendor);
        assertThat(service.getType()).isEqualTo(DB_SPAN_TYPE);

        assertThat(span.getOutcome())
            .describedAs("span outcome should be explicitly set to either failure or success")
            .isNotEqualTo(Outcome.UNKNOWN);

        return span;
    }

    private void assertSpanRecordedWithoutConnection(String rawSql, boolean preparedStatement, long expectedAffectedRows) {
        assertThat(reporter.getSpans())
            .describedAs("one span is expected")
            .hasSize(1);
        Span jdbcSpan = reporter.getFirstSpan();
        StringBuilder processedSql = new StringBuilder();
        signatureParser.querySignature(rawSql, processedSql, preparedStatement);
        assertThat(jdbcSpan.getNameAsString()).isEqualTo(processedSql.toString());
        assertThat(jdbcSpan.getType()).isEqualTo(DB_SPAN_TYPE);
        assertThat(jdbcSpan.getSubtype()).isEqualTo("unknown");
        assertThat(jdbcSpan.getAction()).isEqualTo(DB_SPAN_ACTION);

        Db db = jdbcSpan.getContext().getDb();
        assertThat(db.getStatement()).isEqualTo(rawSql);
        assertThat(db.getInstance()).isNull();
        assertThat(db.getUser()).isNull();
        assertThat(db.getType()).isEqualToIgnoringCase("sql");

        assertThat(db.getAffectedRowsCount())
            .describedAs("unexpected affected rows count for statement %s", rawSql)
            .isEqualTo(expectedAffectedRows);

        Destination destination = jdbcSpan.getContext().getDestination();
        assertThat(destination.getAddress()).isNullOrEmpty();
        assertThat(destination.getPort()).isLessThanOrEqualTo(0);

        Destination.Service service = destination.getService();
        assertThat(service.getName()).isNullOrEmpty();
        assertThat(service.getResource()).isNullOrEmpty();
        assertThat(service.getType()).isNullOrEmpty();
    }

    private static long[] toLongArray(int[] a) {
        long[] result = new long[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = a[i];
        }
        return result;
    }

    private boolean isKnownDatabase(String productName, String versionPrefix) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        return metaData.getDatabaseProductName().equals(productName)
            && metaData.getDatabaseProductVersion().startsWith(versionPrefix);
    }

    /**
     * @param task jdbc task to execute
     * @return false if feature is not supported, true otherwise
     */
    private static boolean executePotentiallyUnsupportedFeature(JdbcTask task) throws SQLException {
        try {
            task.execute();
        } catch (SQLFeatureNotSupportedException | UnsupportedOperationException unsupported) {
            // silently ignored as this feature is not supported by most JDBC drivers
            return false;
        } catch (SQLException e) {
            if (e.getCause() instanceof UnsupportedOperationException) {
                // same as above, because c3p0 have it's own way to say feature not supported
                return false;
            } else {
                throw new SQLException(e);
            }
        }
        return true;
    }

}
