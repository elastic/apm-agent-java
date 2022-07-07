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
package co.elastic.apm.agent.mongodb.v4;

import co.elastic.apm.agent.mongodb.AbstractMongoClientInstrumentationTest;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public class Mongo4SyncTest extends AbstractMongoClientInstrumentationTest {  // TODO : name this consistently

    private static MongoClient client;
    private static MongoDatabase db;

    @BeforeClass
    public static void before() {
        client = MongoClients.create("mongodb://" + AbstractMongoClientInstrumentationTest.container.getContainerIpAddress() + ":" + AbstractMongoClientInstrumentationTest.container.getMappedPort(27017));
        db = client.getDatabase(DB_NAME);
    }

    @AfterClass
    public static void after() {
        if (client != null) {
            client.close();
        }
    }

    @Override
    protected boolean countWithAggregate() {
        // starting with 4.0 counting is implemented through an aggregate operation
        return true;
    }

    @Override
    protected long update(Document query, Document updatedObject) throws Exception {
        return db.getCollection(COLLECTION_NAME).updateOne(query, updatedObject).getModifiedCount();
    }

    @Override
    protected void dropCollection() throws Exception {
        db.getCollection(COLLECTION_NAME).drop();
    }

    @Override
    protected void delete(Document searchQuery) throws Exception {
        db.getCollection(COLLECTION_NAME).deleteOne(searchQuery);
    }

    @Override
    protected long collectionCount() throws Exception {
        return db.getCollection(COLLECTION_NAME).countDocuments();
    }

    // error here the methods that we call do not appear to all exist inthe new driver
    // another issue is the aggregate commands

    @Override
    protected Collection<Document> find(Document query, int batchSize) throws Exception {
        return db.getCollection(AbstractMongoClientInstrumentationTest.COLLECTION_NAME).find(query).batchSize(batchSize).into(new ArrayList<>());
    }

    @Override
    protected void insert(Document document) throws Exception {
        db.getCollection(AbstractMongoClientInstrumentationTest.COLLECTION_NAME).insertOne(document);
    }

    @Override
    protected void insert(Document... document) throws Exception {
        db.getCollection(AbstractMongoClientInstrumentationTest.COLLECTION_NAME).insertMany(Arrays.asList(document));
    }

    @Override
    protected void createCollection() throws Exception {
        db.createCollection(AbstractMongoClientInstrumentationTest.COLLECTION_NAME);
    }

    @Override
    protected void listCollections() throws Exception {
        db.listCollections().first();
    }
}
