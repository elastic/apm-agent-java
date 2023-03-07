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
package co.elastic.apm.agent.mongodb;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.testutils.TestContainersUtils;
import co.elastic.apm.agent.testutils.assertions.DbAssert;
import org.bson.Document;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;


public abstract class AbstractMongoClientInstrumentationIT extends AbstractInstrumentationTest {

    protected static GenericContainer<?> container;
    protected static final String DB_NAME = "testdb";
    protected static final String COLLECTION_NAME = "testcollection";
    private static final int PORT = 27017;

    private static Set<String> COMMANDS_WITH_STATEMENT = Set.of("find", "aggregate", "count", "distinct", "mapReduce");

    @BeforeClass
    public static void startContainer() {
        container = new GenericContainer<>("mongo:3.4")
            .withExposedPorts(PORT)
            .withCreateContainerCmdModifier(TestContainersUtils.withMemoryLimit(2048));
        container.start();
    }

    @AfterClass
    public static void stopContainer() {
        container.stop();
    }

    @Before
    public void startTransaction() {
        startTestRootTransaction("Mongo Transaction");
    }

    @After
    public void endTransaction() throws Exception {
        Transaction currentTransaction = tracer.currentTransaction();
        if (currentTransaction != null) {
            currentTransaction.deactivate().end();
        }

        try {
            // drop outside of transaction to prevent creating any span
            dropCollection();
        } finally {
            reporter.reset();
        }
    }

    @Test
    public void testCreateAndDeleteCollection() throws Exception {
        createCollection();
        dropCollection();

        checkReportedSpans("create", "drop");
    }

    @Test
    public void testErrorSpanHasFailureOutcome() throws Exception {
        createCollection();
        dropCollection();

        // trying to drop when it does not exist creates an error
        dropCollection();

        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(3);

        verifySpan(spans.get(0), getSpanName("create"), Outcome.SUCCESS);
        verifySpan(spans.get(1), getSpanName("drop"), Outcome.SUCCESS);
        verifySpan(spans.get(2), getSpanName("drop"), Outcome.FAILURE);
    }

    @Test
    public void testListCollections() throws Exception {
        listCollections();

        verifySpan(reporter.getFirstSpan(), "testdb.listCollections", Outcome.SUCCESS);
    }

    @Test
    public void testCountCollection() throws Exception {
        assertThat(collectionCount()).isZero();

        checkReportedSpans(countOperationName());
    }

    protected boolean countWithAggregate() {
        return false;
    }

    private String countOperationName() {
        return countWithAggregate() ? "aggregate" : "count";
    }

    @Test
    public void testCreateDocument() throws Exception {
        Document document = new Document();
        document.put("name", "Hello mongo");

        insert(document);

        assertThat(collectionCount()).isOne();

        checkReportedSpans("insert", countOperationName());
    }

    @Test
    public void testFindDocument() throws Exception {
        Document document1 = new Document();
        document1.put("name", "Hello mongo");

        Document document2 = new Document();
        document2.put("name", "Hello world");

        Document document3 = new Document();
        document3.put("name", "Hello world");

        insert(document1, document2, document3);

        assertThat(find(new Document(), 2)).containsExactlyInAnyOrder(document1, document2, document3);

        checkReportedSpans("insert", "find", "getMore");
    }

    @Test
    public void testUpdateDocument() throws Exception {
        Document document = new Document();
        document.put("name", "Hello mongo");
        insert(document);
        reporter.reset();

        Document query = new Document();
        query.put("name", "Hello mongo");
        Document newDocument = new Document();
        newDocument.put("name", "Bye mongo");
        Document updatedObject = new Document();
        updatedObject.put("$set", newDocument);

        long updatedDocCount = update(query, updatedObject);

        long count = collectionCount();
        assertThat(updatedDocCount).isOne();
        assertThat(count).isOne();

        checkReportedSpans("update", countOperationName());
    }

    @Test
    public void testDeleteDocument() throws Exception {
        Document document = new Document();
        document.put("name", "Hello mongo");
        insert(document);
        reporter.reset();

        Document searchQuery = new Document();
        searchQuery.put("name", "Hello mongo");


        delete(searchQuery);

        long count = collectionCount();
        assertThat(count).isZero();

        checkReportedSpans("delete", countOperationName());
    }

    @Test
    public void testCaptureAllCommands() throws Exception {
        if (!canAlwaysCaptureStatement()) {
            return;
        }

        doReturn(List.of(WildcardMatcher.valueOf("*")))
            .when(config.getConfig(MongoConfiguration.class))
            .getCaptureStatementCommands();

        // insert
        Document document = new Document();
        document.put("name", "Hello mongo");
        insert(document);

        // find
        Document searchQuery = new Document();
        searchQuery.put("name", "Hello mongo");
        find(searchQuery, 1);

        // delete
        delete(searchQuery);

        // all 3 operations should be capture with statement

        assertThat(reporter.getNumReportedSpans()).isGreaterThanOrEqualTo(3);
        reporter.getSpans().forEach((s) -> assertThat(s.getContext().getDb()).hasStatement());

    }

    private void checkReportedSpans(String... operations) {
        assertThat(reporter.getNumReportedSpans()).isEqualTo(operations.length);

        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(operations.length);
        for (int i = 0; i < operations.length; i++) {
            verifySpan(spans.get(i), getSpanName(operations[i]), Outcome.SUCCESS);
        }
    }

    private void verifySpan(Span span, String expectedName, Outcome expectedOutcome) {

        assertThat(span.getNameAsString()).isEqualTo(expectedName);
        assertThat(span.getOutcome()).isEqualTo(expectedOutcome);

        // verify destination
        Destination destination = span.getContext().getDestination();
        String address = destination.getAddress().toString();
        assertThat(address).isIn("localhost", "127.0.0.1");

        assertThat(destination).hasPort(container.getMappedPort(PORT));

        DbAssert dbAssert = assertThat(span.getContext().getDb())
            .hasInstance("testdb");

        // not all driver versions support statement capture, so we assert on the ones that do
        String command = span.getAction();
        if (canAlwaysCaptureStatement() && COMMANDS_WITH_STATEMENT.contains(command)) {
            dbAssert.hasStatement();
        }

        assertThat(span.getContext().getServiceTarget())
            .hasType("mongodb")
            .hasName("testdb")
            .hasDestinationResource("mongodb/testdb");

    }

    protected boolean canAlwaysCaptureStatement() {
        return true;
    }

    private static String getSpanName(String operation) {
        return String.format("%s.%s.%s", DB_NAME, COLLECTION_NAME, operation);
    }

    protected abstract long update(Document query, Document updatedObject) throws Exception;

    protected abstract void dropCollection() throws Exception;

    protected abstract void delete(Document searchQuery) throws Exception;

    protected abstract long collectionCount() throws Exception;

    protected abstract Collection<Document> find(Document query, int batchSize) throws Exception;

    protected abstract void insert(Document document) throws Exception;

    protected abstract void insert(Document... document) throws Exception;

    protected abstract void createCollection() throws Exception;

    protected abstract void listCollections() throws Exception;

}

