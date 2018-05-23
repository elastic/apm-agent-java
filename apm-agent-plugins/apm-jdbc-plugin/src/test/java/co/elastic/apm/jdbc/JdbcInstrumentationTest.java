/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
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
package co.elastic.apm.jdbc;

import co.elastic.apm.AbstractInstrumentationTest;
import co.elastic.apm.impl.transaction.Db;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcInstrumentationTest extends AbstractInstrumentationTest {

    private Connection connection;
    private Transaction transaction;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:h2:mem:test", "user", "");
        connection.createStatement().execute("CREATE TABLE IF NOT EXISTS ELASTIC_APM (FOO INT, BAR VARCHAR(255))");
        connection.createStatement().execute("INSERT INTO ELASTIC_APM (FOO, BAR) VALUES (1, 'APM')");
        transaction = tracer.startTransaction();
        transaction.setName("transaction");
        transaction.setType("request");
        transaction.withResult("success");
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
        transaction.end();
    }

    @Test
    void testStatement() throws SQLException {
        final String sql = "SELECT * FROM ELASTIC_APM WHERE FOO=1";
        ResultSet resultSet = connection.createStatement().executeQuery(sql);
        assertSpanRecorded(resultSet, sql);
    }

    @Test
    void testPreparedStatement() throws SQLException {
        final String sql = "SELECT * FROM ELASTIC_APM WHERE FOO=$1";
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        preparedStatement.setInt(1, 1);
        ResultSet resultSet = preparedStatement.executeQuery();
        assertSpanRecorded(resultSet, sql);
    }

    @Test
    void testCallableStatement() throws SQLException {
        final String sql = "SELECT * FROM ELASTIC_APM WHERE FOO=$1";
        CallableStatement preparedStatement = connection.prepareCall(sql);
        preparedStatement.setInt(1, 1);
        ResultSet resultSet = preparedStatement.executeQuery();
        assertSpanRecorded(resultSet, sql);
    }

    private void assertSpanRecorded(ResultSet resultSet, String sql) throws SQLException {
        assertThat(resultSet.next()).isTrue();
        assertThat(resultSet.getInt("foo")).isEqualTo(1);
        assertThat(resultSet.getString("BAR")).isEqualTo("APM");
        assertThat(reporter.getSpans()).hasSize(1);
        Span jdbcSpan = reporter.getFirstSpan();
        assertThat(jdbcSpan.getName().toString()).isEqualTo("SELECT");
        assertThat(jdbcSpan.getType()).isEqualToIgnoringCase("db.h2.sql");
        Db db = jdbcSpan.getContext().getDb();
        assertThat(db.getStatement()).isEqualTo(sql);
        assertThat(db.getUser()).isEqualToIgnoringCase("user");
        assertThat(db.getType()).isEqualToIgnoringCase("sql");
    }
}
