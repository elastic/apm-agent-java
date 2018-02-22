package co.elastic.apm.jdbc;

import co.elastic.apm.MockReporter;
import co.elastic.apm.impl.Db;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.Span;
import co.elastic.apm.impl.Transaction;
import com.p6spy.engine.spy.P6SpyDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class ApmJdbcEventListenerTest {

    private Connection connection;
    private Transaction transaction;

    @BeforeEach
    void setUp() throws SQLException {
        ElasticApmTracer tracer = ElasticApmTracer.builder().reporter(new MockReporter()).build();
        P6SpyDriver.setJdbcEventListenerFactory(() -> new ApmJdbcEventListener(tracer));
        connection = DriverManager.getConnection("jdbc:p6spy:h2:mem:test", "user", "");
        connection.createStatement().execute("CREATE TABLE IF NOT EXISTS ELASTIC_APM (FOO INT, BAR VARCHAR(255))");
        connection.createStatement().execute("INSERT INTO ELASTIC_APM (FOO, BAR) VALUES (1, 'APM')");
        transaction = tracer.startTransaction();
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
        transaction.end();
    }

    @Test
    void testJdbcSpan() throws SQLException {
        PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM ELASTIC_APM WHERE FOO=$1");
        preparedStatement.setInt(1, 1);
        ResultSet resultSet = preparedStatement.executeQuery();
        assertThat(resultSet.next()).isTrue();
        assertThat(resultSet.getInt("foo")).isEqualTo(1);
        assertThat(resultSet.getString("BAR")).isEqualTo("APM");
        assertThat(transaction.getSpans()).hasSize(1);
        Span jdbcSpan = transaction.getSpans().get(0);
        assertThat(jdbcSpan.getName()).isEqualTo("SELECT");
        assertThat(jdbcSpan.getType()).isEqualToIgnoringCase("db.h2.sql");
        Db db = jdbcSpan.getContext().getDb();
        assertThat(db.getStatement()).isEqualTo("SELECT * FROM ELASTIC_APM WHERE FOO=$1");
        assertThat(db.getUser()).isEqualToIgnoringCase("user");
        assertThat(db.getType()).isEqualToIgnoringCase("sql");
    }
}
