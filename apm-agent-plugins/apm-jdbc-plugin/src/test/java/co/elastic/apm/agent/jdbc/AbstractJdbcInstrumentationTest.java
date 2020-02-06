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
import co.elastic.apm.agent.impl.context.Db;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.jdbc.signature.SignatureParser;
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

import static co.elastic.apm.agent.jdbc.helper.JdbcHelperImpl.DB_SPAN_ACTION;
import static co.elastic.apm.agent.jdbc.helper.JdbcHelperImpl.DB_SPAN_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

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
    private final SignatureParser signatureParser;

    AbstractJdbcInstrumentationTest(Connection connection, String expectedDbVendor) throws Exception {
        this.connection = connection;
        this.expectedDbVendor = expectedDbVendor;
        connection.createStatement().execute("CREATE TABLE ELASTIC_APM (FOO INT, BAR VARCHAR(255))");
        connection.createStatement().execute("INSERT INTO ELASTIC_APM (FOO, BAR) VALUES (1, 'APM')");
        transaction = tracer.startRootTransaction(null).activate();
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
        connection.createStatement().execute("DROP TABLE ELASTIC_APM");
        connection.close();
        transaction.deactivate().end();
    }

    // execute in a single test because creating a new connection is expensive,
    // as it spins up another docker container
    @Test
    public void test() {
        executeTest(this::testStatement);

        executeTest(()->testUpdate(false));
        executeTest(()->testUpdate(true));

        executeTest(() -> testPreparedStatementUpdate(false));
        executeTest(() -> testPreparedStatementUpdate(true));

        executeTest(()->testBatch(false));
        executeTest(()->testBatch(true));

        executeTest(this::testPreparedStatement);

        executeTest(()->testBatchPreparedStatement(false));
        executeTest(()->testBatchPreparedStatement(true));

        executeTest(this::testMultipleRowsModifiedStatement);
    }

    /**
     * Wraps JDBC code that may throw {@link SQLException} and properly resets reporter after test execution
     * @param task test task
     */
    private static void executeTest(JdbcTask task) {
        try {
            task.execute();
        } catch (SQLException e) {
            fail("unexpected exception", e);
        } finally {
            // reset reporter is important otherwise one test may pollute results of the following test
            reporter.reset();
        }
    }

    private interface JdbcTask {
        void execute() throws SQLException;
    }

    private void testStatement() throws SQLException {
        final String sql = "SELECT * FROM ELASTIC_APM WHERE FOO=1";
        ResultSet resultSet = connection.createStatement().executeQuery(sql);
        assertQuerySucceededAndSpanRecorded(resultSet, sql, false);
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
        nullForLargeBatch = nullForLargeBatch || isKnownDatabase("MySQL", "10."); // mariadb
        nullForLargeBatch = nullForLargeBatch || isKnownDatabase("Microsoft SQL Server", "14.");
        nullForLargeBatch = nullForLargeBatch || isKnownDatabase("Oracle", "");
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

    private void testUpdate(boolean isLargeUpdate) throws SQLException {
        final String insert = "INSERT INTO ELASTIC_APM (FOO, BAR) VALUES (42, 'TEST')";

        Statement statement = connection.createStatement();

        if (isLargeUpdate) {
            boolean supported = executePotentiallyUnsupportedFeature(() -> statement.executeLargeUpdate(insert));
            if (!supported) {
                // feature not supported, just ignore test
                return;
            }

        } else {
            statement.executeUpdate(insert);
        }

        assertSpanRecorded(insert, false, 1);
    }

    private void testPreparedStatementUpdate(boolean isLargeUpdate) throws SQLException {
        final String insert = "INSERT INTO ELASTIC_APM (FOO, BAR) VALUES (42, 'TEST')";

        PreparedStatement statement = connection.prepareStatement(insert);

        if (isLargeUpdate) {
            boolean supported = executePotentiallyUnsupportedFeature(() -> statement.executeLargeUpdate());
            if (!supported) {
                // feature not supported, just ignore test
                return;
            }

        } else {
            statement.executeUpdate();
        }

        assertSpanRecorded(insert, false, 1);
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
        if (isKnownDatabase("MySQL", "10.") || isKnownDatabase("Oracle", "")) {
            // for an unknown reason mariadb 10 and Oracle express have unexpected but somehow consistent behavior here
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
        if (!isKnownDatabase("Oracle", "")) {
            assertThat(db.getAffectedRowsCount())
                .isEqualTo(statement.getUpdateCount())
                .isEqualTo(1);
        }
    }

    private void assertQuerySucceededAndSpanRecorded(ResultSet resultSet, String rawSql, boolean preparedStatement) throws SQLException {
        assertThat(resultSet.next()).isTrue();
        assertThat(resultSet.getInt("foo")).isEqualTo(1);
        assertThat(resultSet.getString("BAR")).isEqualTo("APM");

        // this method is only called with select statements, thus no affected rows
        assertSpanRecorded(rawSql, preparedStatement, -1);
    }

    private void assertSpanRecorded(String rawSql, boolean preparedStatement, long expectedAffectedRows) throws SQLException {
        assertThat(reporter.getSpans())
            .describedAs("one span is expected")
            .hasSize(1);
        Span jdbcSpan = reporter.getFirstSpan();
        StringBuilder processedSql = new StringBuilder();
        signatureParser.querySignature(rawSql, processedSql, preparedStatement);
        assertThat(jdbcSpan.getNameAsString()).isEqualTo(processedSql.toString());
        assertThat(jdbcSpan.getType()).isEqualTo(DB_SPAN_TYPE);
        assertThat(jdbcSpan.getSubtype()).isEqualTo(expectedDbVendor);
        assertThat(jdbcSpan.getAction()).isEqualTo(DB_SPAN_ACTION);

        Db db = jdbcSpan.getContext().getDb();
        assertThat(db.getStatement()).isEqualTo(rawSql);
        DatabaseMetaData metaData = connection.getMetaData();
        assertThat(db.getUser()).isEqualToIgnoringCase(metaData.getUserName());
        assertThat(db.getType()).isEqualToIgnoringCase("sql");

        assertThat(db.getAffectedRowsCount())
            .describedAs("unexpected affected rows count for statement %s", rawSql)
            .isEqualTo(expectedAffectedRows);

        Destination destination = jdbcSpan.getContext().getDestination();
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
     *
     * @param task jdbc task to execute
     * @return false if feature is not supported, true otherwise
     */
    private static boolean executePotentiallyUnsupportedFeature(JdbcTask task) throws SQLException {
        try {
            task.execute();
        } catch (SQLFeatureNotSupportedException|UnsupportedOperationException unsupported) {
            // silently ignored as this feature is not supported by most JDBC drivers
            return false;
        } catch (SQLException e){
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
