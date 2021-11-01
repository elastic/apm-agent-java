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
package co.elastic.apm.agent.r2dbc;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.db.signature.SignatureParser;
import co.elastic.apm.agent.impl.context.Db;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import io.r2dbc.spi.Batch;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.R2dbcException;
import io.r2dbc.spi.Statement;
import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static co.elastic.apm.agent.r2dbc.helper.R2dbcHelper.DB_SPAN_ACTION;
import static co.elastic.apm.agent.r2dbc.helper.R2dbcHelper.DB_SPAN_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public abstract class AbstractR2dbcInstrumentationTest extends AbstractInstrumentationTest {

    private static final Logger logger = LoggerFactory.getLogger(AbstractR2dbcInstrumentationTest.class);

    private static final int SLEEP_TIME_AFTER_EXECUTE = 100;

    private final String expectedDbVendor;
    private Connection connection;

    private final Transaction transaction;
    private final SignatureParser signatureParser;

    AbstractR2dbcInstrumentationTest(Connection connection, String expectedDbVendor) {
        this.connection = connection;
        this.expectedDbVendor = expectedDbVendor;

        Mono.from(connection.createStatement("CREATE TABLE ELASTIC_APM (FOO INT NOT NULL, BAR VARCHAR(255))").execute()).block();
        Mono.from(connection.createStatement("ALTER TABLE ELASTIC_APM ADD PRIMARY KEY (FOO)").execute()).block();
        Mono.from(connection.createStatement("INSERT INTO ELASTIC_APM (FOO, BAR) VALUES (1, 'APM')").execute()).block();
        Mono.from(connection.createStatement("INSERT INTO ELASTIC_APM (FOO, BAR) VALUES (11, 'BEFORE')").execute()).block();

        transaction = startTestRootTransaction("r2dbc-test");
        signatureParser = new SignatureParser();
    }

    @After
    public void tearDown() {
        try {
            Thread.sleep(200);
        } catch (Exception e) {

        }
        Mono.from(connection.close()).block();
        transaction.deactivate().end();
    }

    @Test
    public void test() {
        executeTest(this::testStatement);
        executeTest(this::testUpdateStatement);
        executeTest(this::testStatementWithBinds);
        executeTest(this::testBatch);
    }

    private void executeTest(R2dbcTask task) throws R2dbcException {
        reporter.reset();
        try {
            task.execute();
        } catch (R2dbcException e) {
            fail("unexpected exception", e);
        } finally {
            reporter.reset();
        }
    }

    private interface R2dbcTask {
        void execute() throws R2dbcException;
    }

    private void testStatement() throws R2dbcException {
        final String sql = "SELECT FOO, BAR FROM ELASTIC_APM WHERE FOO=1";
        Statement statement = connection.createStatement(sql);
        AtomicBoolean isCheckRowData = new AtomicBoolean(false);
        Flux.from(statement.execute()).flatMap(result ->
            result.map((row, metadata) -> {
                    Integer foo = row.get(0, Integer.class);
                    String bar = row.get(1, String.class);
                    System.out.println(String.format("Foo = %s, bar = %s", foo, bar));
                    assertThat(foo).isEqualTo(1);
                    assertThat(bar).isEqualTo("APM");
                    isCheckRowData.set(true);
                    return "handle";
                }
            )).blockLast();

        sleep(SLEEP_TIME_AFTER_EXECUTE);

        assertThat(isCheckRowData).isTrue();
        Span span = assertSpanRecorded(sql, false, -1);
        assertThat(span.getOutcome()).isEqualTo(Outcome.SUCCESS);
    }

    private void testUpdateStatement() {
        final String sql = "UPDATE ELASTIC_APM SET BAR='AFTER' WHERE FOO=11";
        Statement statement = connection.createStatement(sql);
        Integer affectedRowCount = Flux.from(statement.execute())
            .flatMap(result -> Mono.from(result.getRowsUpdated()))
            .blockLast();

        sleep(SLEEP_TIME_AFTER_EXECUTE);

        assertSpanRecorded(sql, false, 1);
    }

    private void testStatementWithBinds() {
        String sql = null;
        switch (expectedDbVendor) {
            case "postgresql":
                sql = "SELECT * FROM ELASTIC_APM WHERE FOO=$1";
                break;
            case "sqlserver":
                sql = "SELECT * FROM ELASTIC_APM WHERE FOO=@id";
                break;
            default:
                sql = "SELECT * FROM ELASTIC_APM WHERE FOO=?";
                break;
        }
        Statement statement = connection.createStatement(sql);
        switch (expectedDbVendor) {
            case "postgresql":
                statement = statement.bind("$1", 1);
                break;
            case "sqlserver":
                statement = statement.bind("id", 1);
                break;
            default:
                statement = statement.bind(0, 1);
                break;
        }
        Flux.from(statement.execute())
            .flatMap(result -> result.getRowsUpdated())
            .blockLast();

        sleep(SLEEP_TIME_AFTER_EXECUTE);

        long expectedAffectedRowsCount = -1L;
        switch (expectedDbVendor) {
            case "mariadb":
            case "mysql":
                expectedAffectedRowsCount = 0L;
                break;
            case "postgresql":
            case "sqlserver":
                expectedAffectedRowsCount = 1L;
                break;
        }

        assertSpanRecorded(sql, false, expectedAffectedRowsCount);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (Exception e) {
        }
    }

    private void testBatch() {
        String insert = "INSERT INTO ELASTIC_APM (FOO, BAR) VALUES (111, 'APM BAR')";
        Batch batch = connection.createBatch()
            .add(insert)
            .add("UPDATE ELASTIC_APM SET BAR='AFTER' WHERE FOO=111");

        Integer batchAffectedRowCount = Flux.from(batch.execute())
            .flatMap(result -> Mono.from(result.getRowsUpdated()))
            .blockLast();

        sleep(SLEEP_TIME_AFTER_EXECUTE);

        assertSpanRecorded(insert, false, 2);
    }

    private Span assertSpanRecorded(String rawSql, boolean preparedStatement, long expectedAffectedRows) throws R2dbcException {
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

        if (!expectedDbVendor.equals("sqlserver")) {
            assertThat(db.getInstance()).isNotBlank();
        }
        assertThat(db.getUser()).isNotBlank();
        assertThat(db.getType()).isEqualToIgnoringCase("sql");

        assertThat(db.getAffectedRowsCount())
            .describedAs("unexpected affected rows count for statement %s", rawSql)
            .isEqualTo(expectedAffectedRows);

        Destination destination = span.getContext().getDestination();
        assertThat(destination.getAddress().toString()).isEqualTo("localhost");
        assertThat(destination.getPort()).isGreaterThan(0);

        Destination.Service service = destination.getService();
        assertThat(service.getResource().toString()).isEqualTo(expectedDbVendor);

        assertThat(span.getOutcome())
            .describedAs("span outcome should be explicitly set to either failure or success")
            .isNotEqualTo(Outcome.UNKNOWN);

        return span;
    }

}
