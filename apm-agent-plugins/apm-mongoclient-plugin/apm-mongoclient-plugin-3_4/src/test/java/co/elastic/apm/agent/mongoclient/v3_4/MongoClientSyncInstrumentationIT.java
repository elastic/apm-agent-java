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
import org.bson.BsonDocument;
import org.bson.Document;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class MongoClientSyncInstrumentationIT extends AbstractMongoClientInstrumentationTest {

    private static final String DB_NAME = "testdb";
    private static final String COLLECTION_NAME = "testcollection";

    private static MongoClient mongo = null;

    @BeforeClass
    public static void startMongoContainerAndClient() throws InterruptedException {
        container = new GenericContainer("mongo:3.4").withExposedPorts(27017);
        container.start();
        mongo = new MongoClient(container.getContainerIpAddress(), container.getMappedPort(27017));
    }

    @AfterClass
    public static void closeClient() {
        mongo.close();
    }

    @Test
    public void testCreateCollection() {
        reporter.reset();

        MongoDatabase db = mongo.getDatabase(DB_NAME);

        db.createCollection(COLLECTION_NAME);

        assertEquals(1, reporter.getSpans().size());
        assertEquals("{ \"create\" : \"testcollection\", \"autoIndexId\" : \"?\", \"capped\" : \"?\" }", reporter.getFirstSpan().getNameAsString());
    }

    public void reset(MongoDatabase db) {
        if (db.getCollection(COLLECTION_NAME) != null) {
            db.getCollection(COLLECTION_NAME).drop();
        }
        reporter.reset();
    }

    @Test
    public void testCountCollection() {
        reporter.reset();

        MongoDatabase db = mongo.getDatabase(DB_NAME);

        long count = db.getCollection(COLLECTION_NAME).count();

        assertEquals(1, reporter.getSpans().size());
        assertEquals(count, 0);
        assertEquals("{ \"count\" : \"testcollection\", \"query\" : { } }", reporter.getFirstSpan().getNameAsString());
    }

    @Test
    public void testCreateDocument() {

        MongoDatabase db = mongo.getDatabase(DB_NAME);
        db.getCollection(COLLECTION_NAME).drop();

        Document document = new Document();
        document.put("name", "Hello mongo");

        MongoCollection collection = db.getCollection(COLLECTION_NAME);
        reporter.reset();

        collection.insertOne(document);

        long count = db.getCollection(COLLECTION_NAME).count();

        assertEquals(2, reporter.getSpans().size());
        assertEquals(1, count);
        assertEquals("{ \"insert\" : \"testcollection\", \"ordered\" : \"?\", \"documents\" : [{ \"_id\" : \"?\", \"name\" : \"?\" }] }", reporter.getSpans().get(0).getNameAsString());
        assertEquals("{ \"count\" : \"testcollection\", \"query\" : { } }", reporter.getSpans().get(1).getNameAsString());
    }

    @Test
    public void testUpdateDocument() {

        MongoDatabase db = mongo.getDatabase(DB_NAME);
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
        assertEquals("{ \"update\" : \"?\", \"ordered\" : \"?\", \"updates\" : [{ \"q\" : { \"name\" : \"?\" }, \"u\" : { \"$set\" : { \"name\" : \"?\" } } }] }", reporter.getSpans().get(0).getNameAsString());
        assertEquals("{ \"count\" : \"testcollection\", \"query\" : { } }", reporter.getSpans().get(1).getNameAsString());
    }

    @Test
    public void testDeleteDocument() {

        MongoDatabase db = mongo.getDatabase(DB_NAME);
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
        assertEquals("{ \"delete\" : \"?\", \"ordered\" : \"?\", \"deletes\" : [{ \"q\" : { \"name\" : \"?\" }, \"limit\" : \"?\" }] }", reporter.getSpans().get(0).getNameAsString());
        assertEquals("{ \"count\" : \"testcollection\", \"query\" : { } }", reporter.getSpans().get(1).getNameAsString());
    }

    @Test
    public void testUpdateWithInvalidBsonDocument() {
        reporter.reset();
        MongoDatabase db = mongo.getDatabase(DB_NAME);
        MongoCollection collection = db.getCollection(COLLECTION_NAME);
        try {
            collection.updateOne(new BsonDocument(), new BsonDocument()).getModifiedCount();
            fail("Should be thrown illegal argument exception");
        } catch (IllegalArgumentException e) {

        }
        assertEquals(0, reporter.getSpans().size());
        assertEquals(1, reporter.getErrors().size());
        assertEquals(IllegalArgumentException.class, reporter.getErrors().get(0).getException().getClass());
        assertEquals("Invalid BSON document for an update", reporter.getErrors().get(0).getException().getMessage());
    }
}
