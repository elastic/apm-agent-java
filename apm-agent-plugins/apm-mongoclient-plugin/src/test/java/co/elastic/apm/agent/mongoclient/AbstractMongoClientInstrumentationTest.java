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
package co.elastic.apm.agent.mongoclient;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.bson.Document;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;

import java.util.Collection;
import java.util.Collections;

import static org.assertj.core.api.Java6Assertions.assertThat;

public abstract class AbstractMongoClientInstrumentationTest extends AbstractInstrumentationTest {

    @SuppressWarnings("NullableProblems")
    protected static GenericContainer container;
    protected static final String DB_NAME = "testdb";
    protected static final String COLLECTION_NAME = "testcollection";

    @BeforeClass
    public static void startContainer() {
        container = new GenericContainer("mongo:3.4").withExposedPorts(27017);
        container.start();
    }

    @AfterClass
    public static void stopContainer() {
        container.stop();
        container = null;
    }

    @Before
    public void startTransaction() throws Exception {
        Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null).activate();
        transaction.withName("Mongo Transaction");
        transaction.withType("request");
        transaction.withResultIfUnset("success");
    }

    @After
    public void endTransaction() throws Exception {
        try {
            reporter.reset();
            dropCollection();
            assertThat(reporter.getSpans()).hasSize(1);
            assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("testdb.testcollection.drop");
            Transaction currentTransaction = tracer.currentTransaction();
            if (currentTransaction != null) {
                currentTransaction.deactivate().end();
            }
        } finally {
            reporter.reset();
        }
    }

    @Test
    public void testCreateCollection() throws Exception {
        createCollection();

        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("testdb.testcollection.create");
    }

    @Test
    public void testCountCollection() throws Exception {
        long count = count();

        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(count).isZero();
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("testdb.testcollection.count");
    }

    @Test
    public void testCreateDocument() throws Exception {
        Document document = new Document();
        document.put("name", "Hello mongo");

        insert(document);

        long count = count();

        assertThat(reporter.getSpans()).hasSize(2);
        assertThat(count).isOne();
        assertThat(reporter.getSpans().get(0).getNameAsString()).isEqualTo("testdb.testcollection.insert");
        assertThat(reporter.getSpans().get(1).getNameAsString()).isEqualTo("testdb.testcollection.count");
    }

    @Test
    public void testFindDocument() throws Exception {
        Document document = new Document();
        document.put("name", "Hello mongo");

        insert(document);

        assertThat(find(document)).isEqualTo(Collections.singletonList(document));

        assertThat(reporter.getSpans()).hasSize(2);
        assertThat(reporter.getSpans().get(0).getNameAsString()).isEqualTo("testdb.testcollection.insert");
        assertThat(reporter.getSpans().get(1).getNameAsString()).isEqualTo("testdb.testcollection.find");
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

        long count = count();
        assertThat(updatedDocCount).isOne();
        assertThat(reporter.getSpans()).hasSize(2);
        assertThat(count).isOne();
        assertThat(reporter.getSpans().get(0).getNameAsString()).isEqualTo("testdb.testcollection.update");
        assertThat(reporter.getSpans().get(1).getNameAsString()).isEqualTo("testdb.testcollection.count");
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

        long count = count();
        assertThat(reporter.getSpans()).hasSize(2);
        assertThat(count).isZero();
        assertThat(reporter.getSpans().get(0).getNameAsString()).isEqualTo("testdb.testcollection.delete");
        assertThat(reporter.getSpans().get(1).getNameAsString()).isEqualTo("testdb.testcollection.count");
    }

    protected abstract long update(Document query, Document updatedObject) throws Exception;

    protected abstract void dropCollection() throws Exception;

    protected abstract void delete(Document searchQuery) throws Exception;

    protected abstract long count() throws Exception;

    protected abstract Collection<Document> find(Document query) throws Exception;

    protected abstract void insert(Document document) throws Exception;

    protected abstract void createCollection() throws Exception;
}

