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

import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoClients;
import com.mongodb.async.client.MongoDatabase;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

@Ignore("Async instrumentation is not implemented yet")
public class MongoClientAsyncInstrumentationIT extends AbstractMongoClientInstrumentationTest {

    private static MongoClient mongo = null;
    private static MongoDatabase db;

    @BeforeClass
    public static void startMongoContainerAndClient() {
        mongo = MongoClients.create("mongodb://" + container.getContainerIpAddress() + ":" + container.getMappedPort(27017));
        db = mongo.getDatabase(DB_NAME);
    }

    @AfterClass
    public static void closeClient() {
        mongo.close();
    }

    private static void executeAndWait(Consumer<SingleResultCallback<Void>> execution) throws ExecutionException, TimeoutException, InterruptedException {
        executeAndGet(execution);
    }

    private static <T> T executeAndGet(Consumer<SingleResultCallback<T>> execution) throws ExecutionException, TimeoutException, InterruptedException {
        CompletableFuture<T> future = new CompletableFuture<>();
        SingleResultCallback<T> callback = getCallback(future);
        execution.accept(callback);
        int TIMEOUT_IN_MILLIS = 550;
        return future.get(TIMEOUT_IN_MILLIS, TimeUnit.MILLISECONDS);
    }

    private static <T> SingleResultCallback<T> getCallback(final CompletableFuture<T> future) {
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

    @Override
    public void dropCollection() throws Exception {
        executeAndWait(c -> db.getCollection(COLLECTION_NAME).drop(c));
        reporter.reset();
    }

    @Override
    protected long update(Document query, Document updatedObject) throws Exception {
        return MongoClientAsyncInstrumentationIT.<UpdateResult>executeAndGet(c -> db.getCollection(COLLECTION_NAME).updateOne(query, updatedObject, c)).getModifiedCount();
    }

    @Override
    protected void delete(Document searchQuery) throws Exception {
        executeAndWait(c -> db.getCollection(COLLECTION_NAME).insertOne(searchQuery, c));
    }

    @Override
    protected long count() throws Exception {
        return MongoClientAsyncInstrumentationIT.<Long>executeAndGet(c -> db.getCollection(COLLECTION_NAME).count(c));
    }

    @Override
    public Collection<Document> find(Document query) throws Exception {
        return MongoClientAsyncInstrumentationIT.<Collection<Document>>executeAndGet(c -> db.getCollection(COLLECTION_NAME).find(query).into(new ArrayList<>(), c));
    }

    @Override
    protected void createCollection() throws Exception {
        executeAndWait(c -> db.createCollection(COLLECTION_NAME, c));
    }

    @Override
    protected void insert(Document document) throws Exception {
        executeAndWait(c -> db.getCollection(COLLECTION_NAME).insertOne(document, c));
    }

}
