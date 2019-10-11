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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;

public class MongoClientAsyncInstrumentationIT extends AbstractMongoClientInstrumentationTest {

    private static final String DB_NAME = "testdb";
    private static final String COLLECTION_NAME = "testcollection";

    private static MongoClient mongo = null;
    private static MongoDatabase db;
    private static int TIMEOUT_IN_MILLIS = 550;

    @BeforeClass
    public static void startMongoContainerAndClient() throws Exception {
        container = new GenericContainer("mongo:3.4").withExposedPorts(27017);
        container.start();
        mongo = MongoClients.create("mongodb://" + container.getContainerIpAddress() + ":" + container.getMappedPort(27017));
        db = mongo.getDatabase(DB_NAME);
    }

    @AfterClass
    public static void closeClient() throws Exception {
        MongoClientAsyncInstrumentationIT.<Void>executeAndGet(c -> mongo.getDatabase(DB_NAME).getCollection(COLLECTION_NAME).drop(c));
        container.stop();
        mongo.close();
    }

    private static void executeAndWait(Consumer<SingleResultCallback<Void>> execution) throws ExecutionException, TimeoutException, InterruptedException {
        executeAndGet(execution);
    }

    private static <T> T executeAndGet(Consumer<SingleResultCallback<T>> execution) throws ExecutionException, TimeoutException, InterruptedException {
        CompletableFuture<T> future = new CompletableFuture<>();
        SingleResultCallback<T> callback = getCallback(future);
        execution.accept(callback);
        return future.get(TIMEOUT_IN_MILLIS, TimeUnit.MILLISECONDS);
    }


    @Before
    public void initCollection() throws Exception {
        executeAndWait(c -> db.createCollection(COLLECTION_NAME, c));
        reporter.reset();
    }

    @After
    public void dropCollection() throws Exception {
        executeAndWait(c -> db.getCollection(COLLECTION_NAME).drop(c));
        reporter.reset();
    }

    @Test
    public void testCreateCollection() throws Exception {
        executeAndWait(c -> db.getCollection(COLLECTION_NAME).drop(c));
        reporter.reset();

        executeAndWait(c -> db.createCollection(COLLECTION_NAME, c));

        assertEquals(1, reporter.getSpans().size());
        assertEquals("testdb.testcollection.create", reporter.getFirstSpan().getNameAsString());
    }

    @Test
    public void testCountCollection() throws Exception {

        MongoClientAsyncInstrumentationIT.<Long>executeAndGet(c -> db.getCollection(COLLECTION_NAME).count(c));

        assertEquals(1, reporter.getSpans().size());
        assertEquals("testdb.testcollection.count", reporter.getFirstSpan().getNameAsString());
    }

    @Test
    public void testCreateDocument() throws Exception {

        Document document = new Document();
        document.put("name", "Hello mongo");

        MongoCollection<Document> collection = db.getCollection(COLLECTION_NAME);
        reporter.reset();

        executeAndWait(c -> collection.insertOne(document, c));

        assertEquals(1, reporter.getSpans().size());
        assertEquals("testdb.testcollection.insert", reporter.getSpans().get(0).getNameAsString());
    }

    @Test
    public void testUpdateDocument() throws Exception {

        MongoDatabase db = mongo.getDatabase(DB_NAME);

        Document document = new Document();
        document.put("name", "Hello mongo");
        MongoCollection<Document> collection = db.getCollection(COLLECTION_NAME);
        executeAndWait(c -> collection.insertOne(document, c));
        reporter.reset();

        Document query = new Document();
        query.put("name", "Hello mongo");
        Document newDocument = new Document();
        newDocument.put("name", "Bye mongo");
        Document updatedObject = new Document();
        updatedObject.put("$set", newDocument);

        MongoClientAsyncInstrumentationIT.<UpdateResult>executeAndGet(c -> collection.updateOne(query, updatedObject, c));

        assertEquals(1, reporter.getSpans().size());
        assertEquals("testdb.testcollection.update", reporter.getSpans().get(0).getNameAsString());
    }

    @Test
    public void testDeleteDocument() throws Exception {

        MongoDatabase db = mongo.getDatabase(DB_NAME);

        Document document = new Document();
        document.put("name", "Hello mongo");
        MongoCollection<Document> collection = db.getCollection(COLLECTION_NAME);
        executeAndWait(c -> collection.insertOne(document, c));
        reporter.reset();

        Document searchQuery = new Document();
        searchQuery.put("name", "Hello mongo");

        MongoClientAsyncInstrumentationIT.<DeleteResult>executeAndGet(c -> collection.deleteOne(searchQuery, c));

        assertEquals(1, reporter.getSpans().size());
        assertEquals("testdb.testcollection.delete", reporter.getSpans().get(0).getNameAsString());
    }

    public static <T> SingleResultCallback<T> getCallback(final CompletableFuture<T> future) {
        return new SingleResultCallback<>() {
            @Override
            public void onResult(final T result, final Throwable t) {
                if (t != null) {
                    future.completeExceptionally(t);
                } else {
                    future.complete(result);
                }
            }
        };
    }

}
