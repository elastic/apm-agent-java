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
package co.elastic.apm.agent.mongoclient.v3_4;

import co.elastic.apm.agent.mongoclient.AbstractMongoClientInstrumentationTest;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;

import static org.junit.Assert.assertEquals;

public class MongoClientSyncInstrumentationIT extends AbstractMongoClientInstrumentationTest {

    private static final String DB_NAME = "testdb";
    private static final String COLLECTION_NAME = "testcollection";

    private static MongoClient mongo = null;
    private static MongoDatabase db = null;

    @BeforeClass
    public static void startMongoContainerAndClient() throws InterruptedException {
        container = new GenericContainer("mongo:3.4").withExposedPorts(27017);
        container.start();
        mongo = new MongoClient(container.getContainerIpAddress(), container.getMappedPort(27017));
        db = mongo.getDatabase(DB_NAME);
    }

    @AfterClass
    public static void closeClient() {
        db.getCollection(COLLECTION_NAME).drop();
        mongo.close();
        container.stop();
    }

    @Test
    public void testCreateCollection() {

        db.createCollection(COLLECTION_NAME);

        assertEquals(1, reporter.getSpans().size());
        assertEquals("testdb.testcollection.create", reporter.getFirstSpan().getNameAsString());
    }

    @Test
    public void testCountCollection() {
        long count = db.getCollection(COLLECTION_NAME).count();

        assertEquals(1, reporter.getSpans().size());
        assertEquals(count, 0);
        assertEquals("testdb.testcollection.count", reporter.getFirstSpan().getNameAsString());
    }

    @Test
    public void testCreateDocument() {
        db.getCollection(COLLECTION_NAME).drop();

        Document document = new Document();
        document.put("name", "Hello mongo");

        MongoCollection collection = db.getCollection(COLLECTION_NAME);
        reporter.reset();

        collection.insertOne(document);

        long count = db.getCollection(COLLECTION_NAME).count();

        assertEquals(2, reporter.getSpans().size());
        assertEquals(1, count);
        assertEquals("testdb.testcollection.insert", reporter.getSpans().get(0).getNameAsString());
        assertEquals("testdb.testcollection.count", reporter.getSpans().get(1).getNameAsString());
    }

    @Test
    public void testUpdateDocument() {
        db.getCollection(COLLECTION_NAME).drop();

        Document document = new Document();
        document.put("name", "Hello mongo");
        MongoCollection collection = db.getCollection(COLLECTION_NAME);
        collection.insertOne(document);
        reporter.reset();

        Document query = new Document();
        query.put("name", "Hello mongo");
        Document newDocument = new Document();
        newDocument.put("name", "Bye mongo");
        Document updatedObject = new Document();
        updatedObject.put("$set", newDocument);

        long updatedDocCount = collection.updateOne(query, updatedObject).getModifiedCount();

        long count = db.getCollection(COLLECTION_NAME).count();
        assertEquals(1, updatedDocCount);
        assertEquals(2, reporter.getSpans().size());
        assertEquals(1, count);
        assertEquals("testdb.testcollection.update", reporter.getSpans().get(0).getNameAsString());
        assertEquals("testdb.testcollection.count", reporter.getSpans().get(1).getNameAsString());
    }

    @Test
    public void testDeleteDocument() {
        db.getCollection(COLLECTION_NAME).drop();

        Document document = new Document();
        document.put("name", "Hello mongo");
        MongoCollection collection = db.getCollection(COLLECTION_NAME);
        collection.insertOne(document);
        reporter.reset();

        Document searchQuery = new Document();
        searchQuery.put("name", "Hello mongo");

        long deletedDocCount = collection.deleteOne(searchQuery).getDeletedCount();

        long count = db.getCollection(COLLECTION_NAME).count();
        assertEquals(1, deletedDocCount);
        assertEquals(2, reporter.getSpans().size());
        assertEquals(0, count);
        assertEquals("testdb.testcollection.delete", reporter.getSpans().get(0).getNameAsString());
        assertEquals("testdb.testcollection.count", reporter.getSpans().get(1).getNameAsString());
    }
}
