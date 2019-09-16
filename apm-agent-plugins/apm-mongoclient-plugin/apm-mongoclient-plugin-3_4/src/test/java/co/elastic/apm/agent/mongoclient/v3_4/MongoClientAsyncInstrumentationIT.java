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
import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoClients;
import com.mongodb.async.client.MongoCollection;
import com.mongodb.async.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;

public class MongoClientAsyncInstrumentationIT extends AbstractMongoClientInstrumentationTest {

    private static final String DB_NAME = "testdb";
    private static final String COLLECTION_NAME = "testcollection";

    private static MongoClient mongo = null;
    private static MongoDatabase db;
    private static int TIMEOUT_IN_MILLIS = 550;

    @BeforeClass
    public static void startMongoContainerAndClient() throws InterruptedException {
        container = new GenericContainer("mongo:3.4").withExposedPorts(27017);
        container.start();
        mongo = MongoClients.create("mongodb://" + container.getContainerIpAddress() + ":" + container.getMappedPort(27017));
        db = mongo.getDatabase(DB_NAME);
    }

    @AfterClass
    public static void closeClient() {
        mongo.getDatabase(DB_NAME).getCollection(COLLECTION_NAME).drop(getVoidCallback());
        container.stop();
        mongo.close();
    }

    @Before
    public void initCollection() throws InterruptedException {
        db.createCollection(COLLECTION_NAME, getVoidCallback());
        Thread.sleep(2 * TIMEOUT_IN_MILLIS);
        reporter.reset();
    }

    @After
    public void dropCollection() throws InterruptedException {
        db.getCollection(COLLECTION_NAME).drop(getVoidCallback());
        Thread.sleep(2 * TIMEOUT_IN_MILLIS);
    }

    @Test
    public void testCreateCollection() throws ExecutionException, InterruptedException {
        db.getCollection(COLLECTION_NAME).drop(getVoidCallback());
        Thread.sleep(TIMEOUT_IN_MILLIS);
        reporter.reset();

        db.createCollection(COLLECTION_NAME, getVoidCallback());
        Thread.sleep(TIMEOUT_IN_MILLIS);

        assertEquals(1, reporter.getSpans().size());
        assertEquals("{ \"create\" : \"testcollection\", \"autoIndexId\" : \"?\", \"capped\" : \"?\" }", reporter.getFirstSpan().getNameAsString());
    }

    @Test
    public void testCountCollection() throws InterruptedException {

        db.getCollection(COLLECTION_NAME).count(getLongCallback());
        Thread.sleep(TIMEOUT_IN_MILLIS);

        assertEquals(1, reporter.getSpans().size());
        assertEquals("{ \"count\" : \"testcollection\", \"query\" : { } }", reporter.getFirstSpan().getNameAsString());
    }

    @Test
    public void testCreateDocument() throws InterruptedException {

        Document document = new Document();
        document.put("name", "Hello mongo");

        MongoCollection collection = db.getCollection(COLLECTION_NAME);
        Thread.sleep(TIMEOUT_IN_MILLIS);
        reporter.reset();

        collection.insertOne(document, getVoidCallback());
        Thread.sleep(TIMEOUT_IN_MILLIS);

        assertEquals(1, reporter.getSpans().size());
        assertEquals("{ \"insert\" : \"testcollection\", \"ordered\" : \"?\", \"documents\" : [{ \"_id\" : \"?\", \"name\" : \"?\" }] }", reporter.getSpans().get(0).getNameAsString());
    }

    @Test
    public void testUpdateDocument() throws InterruptedException {

        MongoDatabase db = mongo.getDatabase(DB_NAME);

        Document document = new Document();
        document.put("name", "Hello mongo");
        MongoCollection collection = db.getCollection(COLLECTION_NAME);
        collection.insertOne(document, getVoidCallback());
        Thread.sleep(TIMEOUT_IN_MILLIS);
        reporter.reset();

        Document query = new Document();
        query.put("name", "Hello mongo");
        Document newDocument = new Document();
        newDocument.put("name", "Bye mongo");
        Document updatedObject = new Document();
        updatedObject.put("$set", newDocument);

        collection.updateOne(query, updatedObject, getUpdateResultCallback());
        Thread.sleep(TIMEOUT_IN_MILLIS);

        assertEquals(1, reporter.getSpans().size());
        assertEquals("{ \"update\" : \"?\", \"ordered\" : \"?\", \"updates\" : [{ \"q\" : { \"name\" : \"?\" }, \"u\" : { \"$set\" : { \"name\" : \"?\" } } }] }", reporter.getSpans().get(0).getNameAsString());
    }

    @Test
    public void testDeleteDocument() throws InterruptedException {

        MongoDatabase db = mongo.getDatabase(DB_NAME);

        Document document = new Document();
        document.put("name", "Hello mongo");
        MongoCollection collection = db.getCollection(COLLECTION_NAME);
        collection.insertOne(document, getVoidCallback());
        Thread.sleep(TIMEOUT_IN_MILLIS);
        reporter.reset();

        Document searchQuery = new Document();
        searchQuery.put("name", "Hello mongo");

        collection.deleteOne(searchQuery, getDeleteResultCallback());
        Thread.sleep(TIMEOUT_IN_MILLIS);

        assertEquals(1, reporter.getSpans().size());
        assertEquals("{ \"delete\" : \"?\", \"ordered\" : \"?\", \"deletes\" : [{ \"q\" : { \"name\" : \"?\" }, \"limit\" : \"?\" }] }", reporter.getSpans().get(0).getNameAsString());
    }

    public static SingleResultCallback<Void> getVoidCallback() {
        CompletableFuture future = new CompletableFuture<>();
        return new SingleResultCallback<>() {
            @Override
            public void onResult(final Void result, final Throwable t) {
                future.complete(result);
            }
        };
    }

    public static SingleResultCallback<Long> getLongCallback() {
        CompletableFuture future = new CompletableFuture<>();
        return new SingleResultCallback<>() {
            @Override
            public void onResult(final Long result, final Throwable t) {
                future.complete(result);
            }
        };
    }

    public static SingleResultCallback<UpdateResult> getUpdateResultCallback() {
        CompletableFuture future = new CompletableFuture<>();
        return new SingleResultCallback<>() {
            @Override
            public void onResult(final UpdateResult result, final Throwable t) {
                future.complete(result);
            }
        };
    }

    public static SingleResultCallback<DeleteResult> getDeleteResultCallback() {
        CompletableFuture future = new CompletableFuture<>();
        return new SingleResultCallback<>() {
            @Override
            public void onResult(final DeleteResult result, final Throwable t) {
                future.complete(result);
            }
        };
    }
}
